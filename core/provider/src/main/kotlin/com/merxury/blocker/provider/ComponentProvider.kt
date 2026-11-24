/*
 * Copyright 2025 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Looper
import android.os.Process
import androidx.core.os.bundleOf
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.control.GetControlLayersUseCase
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.Availability
import com.merxury.blocker.core.model.component.BlockReason
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.privileged.withoutPermissionPrompts
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

class ComponentProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderEntryPoint {
        fun componentStatuses(): ComponentStatusRepository
        fun selectedUsers(): SelectedUserRepository
        fun controlLayers(): GetControlLayersUseCase
        fun privilegedChannel(): PrivilegedChannel
        fun analyticsHelper(): AnalyticsHelper

        @ApplicationScope
        fun applicationScope(): CoroutineScope

        @Dispatcher(IO)
        fun ioDispatcher(): CoroutineDispatcher
    }

    private val callerVerifier: ProviderCallerVerifier by lazy {
        ProviderCallerVerifier(
            ownUid = Process.myUid(),
            trustedCallers = TRUSTED_PROVIDER_CALLERS,
            packageSource = PackageManagerCallerPackageSource(checkNotNull(context).packageManager),
        )
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        enforceTrustedCaller()
        return when (method) {
            "getComponents" -> arg?.let { packageName ->
                runCall(method) { getBlockedComponents(packageName) }
            }

            "blocks" -> if (arg != null && extras != null) {
                runCall(method) { controlComponents(arg, extras) }
            } else {
                null
            }

            else -> null
        }
    }

    // The provider is exported without a permission so LibChecker, which has its own
    // signing key, can reach it. Identify the caller by its kernel-provided Binder UID
    // rather than the package name it reports about itself.
    private fun enforceTrustedCaller() {
        val callingUid = Binder.getCallingUid()
        if (callerVerifier.isAllowed(callingUid)) return
        Timber.w("Rejected ComponentProvider call from uid %d", callingUid)
        throw SecurityException("uid $callingUid is not allowed to call Blocker's ComponentProvider")
    }

    private fun runCall(method: String, work: suspend ProviderEntryPoint.() -> Bundle?): Bundle? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Timber.w("ComponentProvider.%s must be called off the main thread", method)
            return null
        }
        return try {
            val appContext = context?.applicationContext ?: return null
            val entryPoint = EntryPointAccessors.fromApplication(appContext, ProviderEntryPoint::class.java)
            // The Binder wait must end even if an in-flight write is finishing in NonCancellable.
            val task = entryPoint.applicationScope().async(entryPoint.ioDispatcher()) {
                withoutPermissionPrompts { entryPoint.work() }
            }
            try {
                runBlocking { withTimeout(CALL_TIMEOUT_MILLIS) { task.await() } }
            } finally {
                task.cancel()
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w("ComponentProvider.%s timed out", method)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error in ComponentProvider.%s", method)
            null
        }
    }

    private suspend fun ProviderEntryPoint.getBlockedComponents(packageName: String): Bundle {
        val blocked = if (packageName.isBlank()) {
            emptyList()
        } else {
            val app = AppKey(packageName, selectedUsers().ownUser)
            val ready = privilegedChannel().connectIfGranted() is ChannelState.Ready
            refreshComponents(app, preferCache = !ready).mapNotNull { status ->
                val reasons = (status.availability as? Availability.Blocked)?.reasons.orEmpty()
                if (BlockReason.PM_SETTING in reasons || BlockReason.IFW_RULE in reasons) {
                    val id = status.declaration.id
                    // The wire format carries the package name in "type".
                    ShareCmpInfo.Component(id.packageName, id.className, block = true)
                } else {
                    null
                }
            }
        }
        return bundleOf("cmp_list" to Json.encodeToString(ShareCmpInfo(packageName, blocked)))
    }

    private suspend fun ProviderEntryPoint.refreshComponents(app: AppKey, preferCache: Boolean = false): List<ComponentStatus> {
        val repository = componentStatuses()
        val cached = repository.components(app).first()
        val refreshed = try {
            repository.refresh(app)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot refresh components of %s", app.packageName)
            return cached
        }
        return when (val reason = (refreshed as? ReadResult.Failure)?.reason) {
            is ReadFailure.PackageNotFound -> emptyList()

            is ReadFailure.RulesUnreadable -> repository.components(app).first()

            null -> if (preferCache && cached.isNotEmpty()) cached else repository.components(app).first()

            else -> {
                Timber.w("Cannot refresh components of %s: %s", app.packageName, reason)
                cached.ifEmpty { repository.components(app).first() }
            }
        }
    }

    private suspend fun ProviderEntryPoint.controlComponents(packageName: String, data: Bundle): Bundle? {
        val rawString = data.getString("cmp_list") ?: return null
        val entries = Json.decodeFromString<ShareCmpInfo>(rawString).components
        if (entries.isEmpty()) return data
        val app = packageName.takeUnless(String::isBlank)?.let { AppKey(it, selectedUsers().ownUser) }
        if (app != null) {
            val declarations = refreshComponents(app).associate { it.declaration.id.className to it.declaration }
            // A repeated class ends in the state asked for by its last entry.
            val changes = entries.associate { it.name to it.block }.filterKeys { it in declarations }
            if (changes.isNotEmpty()) {
                if (privilegedChannel().connectIfGranted() !is ChannelState.Ready) return null
                for (goal in listOf(ControlGoal.BLOCK, ControlGoal.ENABLE)) {
                    val components = changes.filterValues { it == (goal == ControlGoal.BLOCK) }.keys.map(declarations::getValue)
                    if (components.isEmpty()) continue
                    val request = ControlRequest(app.userId, components, goal, controlLayers()(goal))
                    val ownBlock = goal == ControlGoal.BLOCK && app.packageName == context?.packageName
                    val failed = componentStatuses().control(request)
                        .filterIsInstance<ControlEvent.PackageDone>()
                        .fold(false) { failed, event -> failed || event.outcomes.any { it.failures.isControlException(ownBlock) } }
                    if (failed) return null
                }
            }
        }
        // The old provider logged each input entry, including entries that made no change.
        entries.forEach { analyticsHelper().logControlComponentViaProvider(newState = !it.block) }
        return data
    }

    // A failed read-back or IFW write could return false in the old controller. PM exceptions
    // and missing access returned null. Blocking our own components is now an explicit refusal.
    private fun Map<ControlLayer, ControlFailure>.isControlException(ownBlock: Boolean): Boolean = ControlFailure.ChannelUnavailable in values || when (get(ControlLayer.PACKAGE_MANAGER)) {
        is ControlFailure.NotPermitted -> !ownBlock
        is ControlFailure.SystemError, is ControlFailure.TargetNotFound -> true
        else -> false
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        // Not implemented
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // Not implemented
        return null
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        // Not implemented
        return 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        // Not implemented
        return 0
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.com.merxury.blocker.component"

    private companion object {
        // One interactive wait for all work in a call, including both batches.
        const val CALL_TIMEOUT_MILLIS = 5_000L
    }
}
