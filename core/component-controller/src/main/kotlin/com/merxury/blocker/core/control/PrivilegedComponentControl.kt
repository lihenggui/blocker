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
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.control.layersFor
import com.merxury.blocker.core.model.ifw.IfwFailure
import com.merxury.blocker.core.model.ifw.IfwResult
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.privileged.calls.SetComponentEnabledSettings
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

    override fun apply(request: ControlRequest): Flow<ControlEvent> = flow {
        val packages = request.components.distinctBy { it.id }.groupBy { it.id.packageName }
        if (packages.isNotEmpty() && channel.state.value !is ChannelState.Ready) {
            Timber.i("Connect the privileged channel to ${request.goal} components")
            channel.connect()
        }
        val results = mutableListOf<ControlResult>()
        packages.forEach { (packageName, components) ->
            currentCoroutineContext().ensureActive()
            val app = AppKey(packageName, request.user)
            val outcomes = locks.withLock(packageName) { controlPackage(app, components, request) }
            results += outcomes.map { it.result }
            emit(ControlEvent.PackageDone(packageName, outcomes))
        }
        emit(
            ControlEvent.Finished(
                achieved = results.count { it == ControlResult.ACHIEVED },
                partial = results.count { it == ControlResult.PARTIAL },
                failed = results.count { it == ControlResult.FAILED },
            ),
        )
    }

    /**
     * Reads, changes what is needed and reads back the components of one package; call it under
     * the package's lock. Once a change is sent, the rest runs even if the caller is cancelled.
     */
    private suspend fun controlPackage(app: AppKey, components: List<ComponentDeclaration>, request: ControlRequest): List<ComponentOutcome> {
        val identity = (channel.state.value as? ChannelState.Ready)?.identity
        val layers = components.associate { it.id to it.layersFor(request.layers).reachableBy(request.goal, identity) }
        val before = readState(app, components)
        val pmChanges = components.mapNotNull { component ->
            component.pmChange(request.goal, layers.getValue(component.id), before.getValue(component.id).pm)?.let { component.id to it }
        }
        val ifwChanges = components.mapNotNull { component ->
            ifwChange(request.goal, layers.getValue(component.id))?.let { component to it }
        }.toMap()
        val failures: Failures = mutableMapOf()
        val after = if (pmChanges.isEmpty() && ifwChanges.isEmpty()) {
            before
        } else {
            Timber.i("${request.goal} ${components.size} components of $app: ${pmChanges.size} PM and ${ifwChanges.size} IFW changes")
            withContext(NonCancellable) {
                change(app, pmChanges, ifwChanges, failures)
                readState(app, components)
            }
        }
        return components.map { component ->
            val id = component.id
            judge(component, app.userId, request.goal, layers.getValue(id), before.getValue(id), after.getValue(id), failures[id].orEmpty())
        }
    }

    /**
     * Sends the changes. A failure is recorded only for a layer of a component whose change was
     * attempted and did not complete: a call or a store that throws fails only what it was sent.
     */
    private suspend fun change(app: AppKey, pmChanges: List<Pair<ComponentId, PmSetting>>, ifwChanges: Map<ComponentDeclaration, Boolean>, failures: Failures) {
        val identity = (channel.state.value as? ChannelState.Ready)?.identity
        changePm(app, pmChanges, identity, failures)
        changeIfw(app.packageName, ifwChanges, identity, failures)
    }

    private suspend fun changePm(app: AppKey, changes: List<Pair<ComponentId, PmSetting>>, identity: PrivilegedIdentity?, failures: Failures) {
        if (changes.isEmpty()) return
        if (identity == null) {
            Timber.w("Cannot change ${changes.size} PM settings of $app: no channel")
            changes.forEach { (id, _) -> failures.addIfAbsent(id, ControlLayer.PACKAGE_MANAGER, ControlFailure.ChannelUnavailable) }
            return
        }
        changes.groupBy({ it.second }, { it.first }).forEach { (setting, ids) -> setPm(app, setting, ids, identity, failures) }
    }

    /**
     * Sets [ids] to [setting] in one call where the platform has one, which applies to all of
     * them or none. When the system refuses it, sets them one at a time, so a component the
     * system protects fails alone. The shell may change components only of a test-only app, which
     * the system decides; when it refuses the shell for one call, the package is not such an app,
     * so the refusal stands for all of [ids] without asking again for each.
     */
    private suspend fun setPm(app: AppKey, setting: PmSetting, ids: List<ComponentId>, identity: PrivilegedIdentity, failures: Failures) {
        fun refuseAll(failure: ControlFailure) = ids.forEach { failures.addIfAbsent(it, ControlLayer.PACKAGE_MANAGER, failure) }
        if (ids.size > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val batch = sendPm(app, setting, ids)
            when {
                batch is CallResult.Success -> return

                batch is CallResult.Failure && batch.reason == CallFailure.ChannelUnavailable -> {
                    Timber.w("Channel lost while setting ${ids.size} components of $app to $setting")
                    refuseAll(ControlFailure.ChannelUnavailable)
                    return
                }

                batch is CallResult.Failure && batch.reason is CallFailure.NotPermitted && identity == PrivilegedIdentity.SHELL -> {
                    Timber.w("The shell may not change components of $app: ${batch.reason}")
                    refuseAll(batch.reason.toControlFailure())
                    return
                }

                else -> Timber.w("The system refused to set ${ids.size} components of $app to $setting at once: $batch")
            }
        }
        for (id in ids) {
            val result = sendPm(app, setting, listOf(id))
            if (result is CallResult.Failure) {
                Timber.w("Cannot set $id of user ${app.userId.value} to $setting: ${result.reason}")
                val failure = result.reason.toControlFailure()
                if (result.reason is CallFailure.NotPermitted && identity == PrivilegedIdentity.SHELL) {
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
    private suspend fun sendPm(app: AppKey, setting: PmSetting, ids: List<ComponentId>): CallResult<Unit> = try {
        executor.executeIfReady(
            SetComponentEnabledSettings(ids.map { ComponentName(it.packageName, it.className) }, setting.toComponentState(), app.userId),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Cannot set ${ids.size} components of $app to $setting")
        CallResult.Failure(CallFailure.SystemError(e.toString()))
    }

    private suspend fun changeIfw(packageName: String, changes: Map<ComponentDeclaration, Boolean>, identity: PrivilegedIdentity?, failures: Failures) {
        if (changes.isEmpty()) return
        val failure = when {
            identity == null -> ControlFailure.ChannelUnavailable

            !identity.canWriteSystemFiles -> ControlFailure.NotPermitted("$identity may not write the Intent Firewall rules")

            else -> try {
                when (val result = ifwStore.setBlocked(packageName, changes)) {
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
    private suspend fun readState(app: AppKey, components: List<ComponentDeclaration>): Map<ComponentId, ControlState> {
        val pm = readPm(app, components.map { it.id })
        val ifw = readIfw(app.packageName, components)
        return components.associate { it.id to ControlState(pm[it.id] ?: PmSetting.UNKNOWN, ifw[it.id] ?: IfwBlock.UNKNOWN) }
    }

    private suspend fun readPm(app: AppKey, ids: List<ComponentId>): Map<ComponentId, PmSetting> {
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
    is CallFailure.NotPermitted -> ControlFailure.NotPermitted(message)
    is CallFailure.TargetNotFound -> ControlFailure.TargetNotFound(message)
    is CallFailure.SystemError -> ControlFailure.SystemError(message)
}

/** Called only with an identity that may write the rules, so no access means the channel went away. */
private fun IfwFailure.toControlFailure(): ControlFailure = when (this) {
    IfwFailure.NoAccess -> ControlFailure.ChannelUnavailable
    is IfwFailure.Unreadable -> ControlFailure.RulesUnreadable
    is IfwFailure.WriteFailed -> ControlFailure.WriteFailed(message)
}
