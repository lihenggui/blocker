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
import com.merxury.blocker.core.data.device.FirewallRead
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.control.GetControlLayersUseCase
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.Availability
import com.merxury.blocker.core.model.component.BlockReason
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.control.ComponentWriteObserver
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

class ComponentProvider internal constructor(
    private val readTimeoutMillis: Long,
    private val writeTimeoutMillis: Long,
) : ContentProvider() {

    constructor() : this(READ_TIMEOUT_MILLIS, WRITE_TIMEOUT_MILLIS)

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
                runCall(method, onFailure = { componentsBundle(packageName, emptyList()) }) { getBlockedComponents(packageName) }
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

    private fun runCall(
        method: String,
        onFailure: () -> Bundle? = { null },
        work: suspend ProviderEntryPoint.() -> Bundle?,
    ): Bundle? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Timber.w("ComponentProvider.%s must be called off the main thread", method)
            return null
        }
        val appContext = context?.applicationContext ?: return null
        return try {
            val resolved = EntryPointAccessors.fromApplication(appContext, ProviderEntryPoint::class.java)
            runBlocking { resolved.work() }
        } catch (e: Exception) {
            Timber.e(e, "Error in ComponentProvider.%s", method)
            onFailure()
        }
    }

    private suspend fun ProviderEntryPoint.getBlockedComponents(packageName: String): Bundle {
        if (packageName.isBlank()) return componentsBundle(packageName, emptyList())
        val cached = AtomicReference(emptyList<ComponentStatus>())
        val task = startQuietly {
            val app = AppKey(packageName, selectedUsers().ownUser)
            // Leave two of the five seconds for a local PackageManager refresh.
            val privileged = withTimeoutOrNull(readTimeoutMillis * 3 / 5) {
                cached.set(cachedComponents(app))
                val access = privilegedChannel().connectIfGranted() as? ChannelState.Ready ?: return@withTimeoutOrNull null
                refreshAndReadComponents(app, access)
            }
            privileged ?: refreshAndReadComponents(app, access = null)
        }
        val components = try {
            withTimeout(readTimeoutMillis) { task.await() }
        } catch (e: Exception) {
            if (e is CancellationException) currentCoroutineContext().ensureActive()
            Timber.w(e, "Cannot finish refreshing components of %s", packageName)
            cached.get()
        } finally {
            task.cancel()
        }
        return componentsBundle(packageName, components)
    }

    private suspend fun ProviderEntryPoint.refreshAndReadComponents(app: AppKey, access: ChannelState.Ready?): List<ComponentStatus> = if (refreshComponents(app, access) is ReadFailure.PackageNotFound) emptyList() else cachedComponents(app)

    private fun componentsBundle(packageName: String, components: List<ComponentStatus>): Bundle {
        val blocked = components.mapNotNull { status ->
            val reasons = (status.availability as? Availability.Blocked)?.reasons.orEmpty()
            if (BlockReason.PM_SETTING in reasons || BlockReason.IFW_RULE in reasons) {
                val id = status.declaration.id
                // The wire format carries the package name in "type".
                ShareCmpInfo.Component(id.packageName, id.className, block = true)
            } else {
                null
            }
        }
        return bundleOf("cmp_list" to Json.encodeToString(ShareCmpInfo(packageName, blocked)))
    }

    private suspend fun ProviderEntryPoint.cachedComponents(app: AppKey): List<ComponentStatus> = componentStatuses().components(app).first()

    private suspend fun ProviderEntryPoint.refreshComponents(app: AppKey, access: ChannelState.Ready?): ReadFailure? {
        val firewall = when {
            access == null -> FirewallRead.KEEP_CACHED
            access.identity.canWriteSystemFiles -> FirewallRead.REQUIRED
            else -> FirewallRead.IF_AVAILABLE
        }
        val result = componentStatuses().refreshIfReadable(app, firewall)
        val reason = (result as? ReadResult.Failure)?.reason
        if (reason != null && reason !is ReadFailure.PackageNotFound) {
            Timber.w("Cannot refresh components of %s: %s", app.packageName, reason)
        }
        return reason
    }

    private fun <T> ProviderEntryPoint.startQuietly(work: suspend () -> T): Deferred<T> = applicationScope().async(ioDispatcher()) {
        withoutPermissionPrompts(work)
    }

    private suspend fun ProviderEntryPoint.controlComponents(packageName: String, data: Bundle): Bundle? {
        val rawString = data.getString("cmp_list") ?: return null
        val entries = Json.decodeFromString<ShareCmpInfo>(rawString).components
        if (entries.isEmpty()) return data
        // A repeated class ends in the state asked for by its last entry.
        val changes = entries.associate { it.name to it.block }
        if (packageName.isNotBlank() && !writeBeforeDeadline(packageName, changes)) return null
        // The old provider logged each input entry, including entries that made no change.
        entries.forEach { analyticsHelper().logControlComponentViaProvider(newState = !it.block) }
        return data
    }

    private suspend fun ProviderEntryPoint.prepareControl(packageName: String, changes: Map<String, Boolean>): List<ControlRequest>? {
        val app = AppKey(packageName, selectedUsers().ownUser)
        val access = privilegedChannel().connectIfGranted() as? ChannelState.Ready
        val failure = refreshComponents(app, access)
        if (failure is ReadFailure.PackageNotFound) return emptyList()
        if (failure == ReadFailure.ChannelUnavailable) return null
        val declarations = cachedComponents(app).associate { it.declaration.id.className to it.declaration }
        val known = changes.filterKeys { it in declarations }
        if (known.isEmpty()) return if (failure == null) emptyList() else null
        // The repository can refuse a protected block without access. Enabling still requires it.
        if (access == null && (!componentStatuses().isProtectedFromBlocking(app) || known.containsValue(false))) return null

        return listOf(ControlGoal.BLOCK, ControlGoal.ENABLE).mapNotNull { goal ->
            val components = known.filterValues { it == (goal == ControlGoal.BLOCK) }.keys.map(declarations::getValue)
            components.takeIf { it.isNotEmpty() }?.let { ControlRequest(app.userId, it, goal, controlLayers()(goal)) }
        }
    }

    private suspend fun ProviderEntryPoint.writeBeforeDeadline(packageName: String, changes: Map<String, Boolean>): Boolean {
        val progress = WriteProgress(packageName)
        val task = startQuietly {
            val requests = prepareControl(packageName, changes) ?: return@startQuietly false
            if (requests.isEmpty()) return@startQuietly true
            progress.beginControl() && applyControl(requests, progress)
        }
        return try {
            withTimeoutOrNull(writeTimeoutMillis) { task.await() } ?: progress.onTimeout()
        } finally {
            // Cancel preparation. Once control starts, let its in-flight batch report its writes.
            if (progress.stopBeforeControl()) task.cancel()
        }
    }

    private suspend fun ProviderEntryPoint.applyControl(requests: List<ControlRequest>, progress: WriteProgress): Boolean {
        for (request in requests) {
            if (!progress.canStart()) return false
            val failed = componentStatuses().control(request, keepKnownState = true, writeObserver = progress)
                .filterIsInstance<ControlEvent.PackageDone>()
                .fold(false) { failed, event -> failed || event.outcomes.any { it.failures.isControlException() } }
            if (failed) return false
        }
        return true
    }

    // A failed read-back or IFW write could return false in the old controller. PM exceptions
    // and missing access returned null. A protected block is an explicit refusal without an exception.
    private fun Map<ControlLayer, ControlFailure>.isControlException(): Boolean = ControlFailure.ChannelUnavailable in values || when (get(ControlLayer.PACKAGE_MANAGER)) {
        is ControlFailure.NotPermitted, is ControlFailure.SystemError, is ControlFailure.TargetNotFound -> true
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

    private class WriteProgress(private val packageName: String) : ComponentWriteObserver {
        private val written = linkedSetOf<ComponentId>()
        private var controlStarted = false
        private var stopped = false
        private var timedOut = false

        @Synchronized
        override fun canStart(): Boolean = !stopped

        @Synchronized
        fun beginControl(): Boolean {
            if (stopped) return false
            controlStarted = true
            return true
        }

        @Synchronized
        fun stopBeforeControl(): Boolean {
            stopped = true
            return !controlStarted
        }

        @Synchronized
        fun onTimeout(): Boolean {
            stopped = true
            timedOut = true
            Timber.w("ComponentProvider.blocks timed out for %s with confirmed writes %s", packageName, written)
            return false
        }

        @Synchronized
        override fun onWrite(components: Collection<ComponentId>) {
            written += components
            if (timedOut) Timber.w("ComponentProvider.blocks confirmed writes after timeout for %s (%s)", packageName, components)
        }
    }

    private companion object {
        const val READ_TIMEOUT_MILLIS = 5_000L

        // Budget two 15-second Shizuku user service attachments plus the 30-second system
        // reply budget used by privileged app operations, allowing for sequential PM calls.
        const val WRITE_TIMEOUT_MILLIS = 60_000L
    }
}
