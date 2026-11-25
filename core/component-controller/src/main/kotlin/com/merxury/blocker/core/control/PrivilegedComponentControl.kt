/*
 * Copyright 2026 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.core.control

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ComponentWriteObserver
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.control.layersFor
import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.privileged.calls.SetComponentEnabledSettings
import com.merxury.blocker.core.privileged.permissionPromptsAllowed
import com.merxury.blocker.core.system.SystemPackageReader
import com.merxury.core.ifw.IfwStore
import com.merxury.core.ifw.isPackageRuleFileName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** What went wrong per component and layer while changing one package. */
private typealias Failures = MutableMap<ComponentId, MutableMap<ControlLayer, ControlFailure>>

/**
 * [ComponentControl] over the privileged channel, the system reader and the Intent Firewall
 * store. Each package is changed under its lock in [locks], which every request and the wake
 * feature share.
 */
@Singleton
internal class PrivilegedComponentControl @Inject constructor(
    private val channel: PrivilegedChannel,
    private val executor: PrivilegedExecutor,
    private val reader: SystemPackageReader,
    private val ifwStore: IfwStore,
    private val locks: PackageLocks,
) : ComponentControl {

    override suspend fun state(app: AppKey, components: Collection<ComponentDeclaration>): Map<ComponentId, ControlState> = readState(app, components.distinctBy { it.id })

    override fun apply(
        request: ControlRequest,
        writeObserver: ComponentWriteObserver?,
        persistence: ComponentControlPersistence?,
    ): Flow<ControlEvent> = flow {
        val packages = request.components.distinctBy { it.id }.groupBy { it.id.packageName }
        if (packages.isNotEmpty() && channel.state.value !is ChannelState.Ready && permissionPromptsAllowed()) {
            Timber.i("Connect the privileged channel to ${request.goal} components")
            channel.connect()
        }
        val results = mutableListOf<ControlResult>()
        packages.forEach { (packageName, components) ->
            currentCoroutineContext().ensureActive()
            val app = AppKey(packageName, request.user)
            val event = locks.withLock(packageName) { controlPackage(app, components, request, writeObserver, persistence) }
            results += event.outcomes.map { it.result }
            emit(event)
        }
        emit(
            ControlEvent.Finished(
                achieved = results.count { it == ControlResult.ACHIEVED },
                partial = results.count { it == ControlResult.PARTIAL },
                failed = results.count { it == ControlResult.FAILED },
                skipped = results.count { it == ControlResult.SKIPPED },
            ),
        )
    }

    /**
     * Reads, changes what is needed and reads back the components of one package; call it under
     * the package's lock. Once a change is sent, the rest runs even if the caller is cancelled.
     */
    private suspend fun controlPackage(
        app: AppKey,
        components: List<ComponentDeclaration>,
        request: ControlRequest,
        writeObserver: ComponentWriteObserver?,
        persistence: ComponentControlPersistence?,
    ): ControlEvent.PackageDone {
        val identity = (executor.checkedState() as? ChannelState.Ready)?.identity
        val failures: Failures = mutableMapOf()
        val blockingFirewallProtected = request.goal == ControlGoal.BLOCK && !ifwStore.allowsBlocking(app.packageName)
        val layers = components.associate { component ->
            val requested = component.layersFor(request.layers).reachableBy(request.goal, identity)
            if (blockingFirewallProtected && ControlLayer.INTENT_FIREWALL in requested) {
                failures.addIfAbsent(component.id, ControlLayer.INTENT_FIREWALL, ControlFailure.ProtectedComponent)
            }
            component.id to requested
        }
        var pmReadFailure: ReadFailure? = null
        val before = readState(app, components) { pmReadFailure = it }
        val pmChanges = components.mapNotNull { component ->
            val id = component.id
            val current = before.getValue(id).pm
            val change = component.pmChange(request.goal, layers.getValue(id), current) ?: return@mapNotNull null
            if (change == PmSetting.DISABLED && current == PmSetting.UNKNOWN) {
                val failure = when (val readFailure = pmReadFailure) {
                    is ReadFailure.PackageNotFound -> ControlFailure.TargetNotFound(readFailure.toString())
                    is ReadFailure.UserNotReachable, ReadFailure.ChannelUnavailable -> ControlFailure.ChannelUnavailable
                    else -> ControlFailure.StateNotConfirmed
                }
                failures.addIfAbsent(id, ControlLayer.PACKAGE_MANAGER, failure)
                null
            } else {
                id to change
            }
        }
        val ifwChanges = components.mapNotNull { component ->
            ifwChange(request.goal, layers.getValue(component.id))?.takeUnless { blockingFirewallProtected }?.let { component to it }
        }.toMap()
        val hasChanges = pmChanges.isNotEmpty() || ifwChanges.isNotEmpty()
        val identityAtWrite = if (hasChanges) (executor.checkedState() as? ChannelState.Ready)?.identity else null
        val alreadyDisabled = if (request.goal == ControlGoal.BLOCK) {
            before.keys.filterTo(mutableSetOf()) { id ->
                ControlLayer.PACKAGE_MANAGER in layers.getValue(id) && before.getValue(id).pm == PmSetting.DISABLED
            }
        } else {
            emptySet()
        }
        fun outcome(after: Map<ComponentId, ControlState>, sentPm: Boolean): ControlEvent.PackageDone {
            val outcomes = components.map { component ->
                val id = component.id
                judge(component, app.userId, request.goal, layers.getValue(id), before.getValue(id), after.getValue(id), failures[id].orEmpty())
            }
            val pmWrites = if (sentPm) {
                pmChanges.filter { (id, setting) ->
                    ControlLayer.PACKAGE_MANAGER !in failures[id].orEmpty() && after.getValue(id).pm == setting
                }.mapTo(mutableSetOf()) { (id, _) -> id }
            } else {
                emptySet()
            }
            return ControlEvent.PackageDone(app.packageName, outcomes, pmWrites)
        }
        if (writeObserver?.canStart() == false) {
            return outcome(before, sentPm = false).also { persistence?.afterPackage(it, emptySet()) }
        }
        if (!hasChanges) {
            return outcome(before, sentPm = false).also { persistence?.afterPackage(it, alreadyDisabled) }
        }
        currentCoroutineContext().ensureActive()
        Timber.i("${request.goal} ${components.size} components of $app: ${pmChanges.size} PM and ${ifwChanges.size} IFW changes")
        return withContext(NonCancellable) {
            val onWrite = writeObserver?.let { it::onWrite }
            if (request.goal == ControlGoal.BLOCK && identityAtWrite != null && pmChanges.isNotEmpty()) {
                persistence?.beforePmBlock(app, pmChanges.map { it.first })
            }
            changePm(app, pmChanges, identityAtWrite, failures, onWrite)
            changeIfw(app.packageName, ifwChanges, identityAtWrite, failures, onWrite)
            outcome(readState(app, components), sentPm = true).also { persistence?.afterPackage(it, alreadyDisabled) }
        }
    }

    /**
     * Sends the changes. A failure is recorded only for a layer of a component whose change was
     * attempted and did not complete: a call or a store that throws fails only what it was sent.
     */
    private suspend fun changePm(
        app: AppKey,
        changes: List<Pair<ComponentId, PmSetting>>,
        identity: PrivilegedIdentity?,
        failures: Failures,
        onWrite: ((Collection<ComponentId>) -> Unit)?,
    ) {
        if (changes.isEmpty()) return
        if (identity == null) {
            Timber.w("Cannot change ${changes.size} PM settings of $app: no channel")
            changes.forEach { (id, _) -> failures.addIfAbsent(id, ControlLayer.PACKAGE_MANAGER, ControlFailure.ChannelUnavailable) }
            return
        }
        changes.groupBy({ it.second }, { it.first }).forEach { (setting, ids) -> setPm(app, setting, ids, identity, failures, onWrite) }
    }

    /**
     * Sets [ids] to [setting] in one call where the platform has one, which applies to all of
     * them or none. When the system refuses it, sets them one at a time, so a component the
     * system protects fails alone. The shell may change components only of a test-only app, which
     * the system decides; when it refuses the shell for one call, the package is not such an app,
     * so the refusal stands for all of [ids] without asking again for each.
     */
    private suspend fun setPm(
        app: AppKey,
        setting: PmSetting,
        ids: List<ComponentId>,
        identity: PrivilegedIdentity,
        failures: Failures,
        onWrite: ((Collection<ComponentId>) -> Unit)?,
    ) {
        fun refuseAll(failure: ControlFailure) = ids.forEach { failures.addIfAbsent(it, ControlLayer.PACKAGE_MANAGER, failure) }
        if (ids.size > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val batch = sendPm(app, setting, ids, onWrite)
            val failure = (batch as? CallResult.Failure)?.reason
            when {
                batch is CallResult.Success -> return

                failure == CallFailure.ChannelUnavailable -> {
                    Timber.w("Channel lost while setting ${ids.size} components of $app to $setting")
                    refuseAll(ControlFailure.ChannelUnavailable)
                    return
                }

                failure is CallFailure.NotPermitted && (failure.identity ?: identity) == PrivilegedIdentity.SHELL -> {
                    Timber.w("The shell may not change components of $app: $failure")
                    refuseAll(failure.toControlFailure())
                    return
                }

                else -> Timber.w("The system refused to set ${ids.size} components of $app to $setting at once: $batch")
            }
        }
        for (id in ids) {
            val result = sendPm(app, setting, listOf(id), onWrite)
            if (result is CallResult.Failure) {
                val reason = result.reason
                Timber.w("Cannot set $id of user ${app.userId.value} to $setting: $reason")
                val failure = reason.toControlFailure()
                if (reason is CallFailure.NotPermitted && (reason.identity ?: identity) == PrivilegedIdentity.SHELL) {
                    refuseAll(failure)
                    return
                }
                failures.addIfAbsent(id, ControlLayer.PACKAGE_MANAGER, failure)
            }
        }
    }

    // The channel was connected when the request started; a channel lost since then is not
    // connected again, so a request shows at most one prompt. A call that throws, which the
    // executor is not expected to do, fails the components it was sent for.
    private suspend fun sendPm(
        app: AppKey,
        setting: PmSetting,
        ids: List<ComponentId>,
        onWrite: ((Collection<ComponentId>) -> Unit)?,
    ): CallResult<Unit> = try {
        executor.executeIfReady(
            SetComponentEnabledSettings(ids.map { ComponentName(it.packageName, it.className) }, setting.toComponentState(), app.userId),
        ).also { if (it is CallResult.Success) onWrite?.invoke(ids) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Cannot set ${ids.size} components of $app to $setting")
        CallResult.Failure(CallFailure.SystemError(e.toString()))
    }

    private suspend fun changeIfw(
        packageName: String,
        changes: Map<ComponentDeclaration, Boolean>,
        identity: PrivilegedIdentity?,
        failures: Failures,
        onWrite: ((Collection<ComponentId>) -> Unit)?,
    ) {
        if (changes.isEmpty()) return
        val failure = when {
            identity == null -> ControlFailure.ChannelUnavailable

            !identity.canWriteSystemFiles -> ControlFailure.NotPermitted("$identity may not write the Intent Firewall rules", identity)

            else -> try {
                when (val result = ifwStore.setBlocked(packageName, changes, onWrite)) {
                    is IfwResult.Success -> return
                    is IfwResult.Failure -> result.reason.toControlFailure()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The store is not expected to throw; only the firewall layer of these failed.
                Timber.e(e, "The rule store failed for $packageName")
                ControlFailure.SystemError(e.toString())
            }
        }
        Timber.w("Cannot change IFW rules of ${changes.size} components of $packageName: $failure")
        changes.keys.forEach { failures.addIfAbsent(it.id, ControlLayer.INTENT_FIREWALL, failure) }
    }

    /** The state of [components] of [app]; never throws except for cancellation. */
    private suspend fun readState(
        app: AppKey,
        components: List<ComponentDeclaration>,
        onPmFailure: (ReadFailure) -> Unit = {},
    ): Map<ComponentId, ControlState> {
        val pm = readPm(app, components.map { it.id }, onPmFailure)
        val ifw = readIfw(app.packageName, components)
        return components.associate { it.id to ControlState(pm[it.id] ?: PmSetting.UNKNOWN, ifw[it.id] ?: IfwBlock.UNKNOWN) }
    }

    private suspend fun readPm(app: AppKey, ids: List<ComponentId>, onFailure: (ReadFailure) -> Unit): Map<ComponentId, PmSetting> {
        if (ids.isEmpty()) return emptyMap()
        val read = try {
            reader.pmSettings(app, ids)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot read the PM settings of $app")
            return emptyMap()
        }
        return when (read) {
            is ReadResult.Success -> read.value

            is ReadResult.Failure -> {
                Timber.w("Cannot read the PM settings of $app: ${read.reason}")
                onFailure(read.reason)
                emptyMap()
            }
        }
    }

    /** The firewall state of the components of [packageName]; other packages' stay unknown. */
    private suspend fun readIfw(packageName: String, components: List<ComponentDeclaration>): Map<ComponentId, IfwBlock> {
        val own = components.filter { it.id.packageName == packageName }
        if (own.isEmpty() || !isPackageRuleFileName(packageName)) return emptyMap()
        return try {
            ifwStore.blocks(packageName, own)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot read the IFW state of $packageName")
            emptyMap()
        }
    }
}

/** Keeps the first failure of a layer, which is the one that explains it. */
private fun Failures.addIfAbsent(id: ComponentId, layer: ControlLayer, failure: ControlFailure) {
    getOrPut(id) { mutableMapOf() }.putIfAbsent(layer, failure)
}

private fun PmSetting.toComponentState(): Int = when (this) {
    PmSetting.DEFAULT -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    PmSetting.ENABLED -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    PmSetting.DISABLED -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    PmSetting.UNKNOWN -> error("Not a setting to send")
}

private fun CallFailure.toControlFailure(): ControlFailure = when (this) {
    CallFailure.ChannelUnavailable -> ControlFailure.ChannelUnavailable
    is CallFailure.NotPermitted -> ControlFailure.NotPermitted(message, identity)
    is CallFailure.TargetNotFound -> ControlFailure.TargetNotFound(message)
    is CallFailure.SystemError -> ControlFailure.SystemError(message)
}

/** Called only with an identity that may write the rules, so no access means the channel went away. */
private fun IfwFailure.toControlFailure(): ControlFailure = when (this) {
    IfwFailure.ProtectedComponent -> ControlFailure.ProtectedComponent
    IfwFailure.NoAccess -> ControlFailure.ChannelUnavailable
    is IfwFailure.Unreadable -> ControlFailure.RulesUnreadable
    is IfwFailure.WriteFailed -> ControlFailure.WriteFailed(message)
}
