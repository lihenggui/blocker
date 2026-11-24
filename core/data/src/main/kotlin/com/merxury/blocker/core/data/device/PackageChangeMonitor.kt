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

package com.merxury.blocker.core.data.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.component.AppKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the app and component cache current without waiting for a screen to read it. */
@Singleton
class PackageChangeMonitor internal constructor(
    private val context: Context,
    private val cache: LocalComponentStatusRepository,
    private val users: SelectedUserRepository,
    private val controlActivity: PackageControlActivity,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val elapsedRealtime: () -> Long,
) {
    @Inject
    internal constructor(
        @ApplicationContext context: Context,
        cache: LocalComponentStatusRepository,
        users: SelectedUserRepository,
        controlActivity: PackageControlActivity,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
    ) : this(context, cache, users, controlActivity, scope, ioDispatcher, SystemClock::elapsedRealtime)

    private val started = AtomicBoolean(false)
    private val foreground = Channel<Unit>(Channel.CONFLATED)
    private val pending = mutableMapOf<String, PendingChange>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action !in PACKAGE_CHANGES) return
            if (action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            val uri = intent.data?.takeIf { it.scheme == PACKAGE_SCHEME } ?: return
            val name = uri.schemeSpecificPart?.takeIf { it.isNotEmpty() } ?: return
            enqueue(name)
        }
    }

    /** Registers once for the process and requests the first reconciliation. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(ioDispatcher) {
            val registered = safely("register for package changes") {
                val filter = IntentFilter().apply {
                    PACKAGE_CHANGES.forEach(::addAction)
                    addDataScheme(PACKAGE_SCHEME)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
            }
            try {
                onForeground()
                reconcileOnForeground()
            } finally {
                if (registered) safely("unregister for package changes") { context.unregisterReceiver(receiver) }
            }
        }
    }

    fun onForeground() {
        foreground.trySend(Unit)
    }

    private suspend fun reconcileOnForeground() {
        var lastFinished: Long? = null
        for (request in foreground) {
            if (lastFinished?.let { elapsedRealtime() - it < FOREGROUND_INTERVAL_MILLIS } == true) continue
            safely("find users with cached apps") {
                val reconcileUsers = setOf(users.ownUser, users.selectedUser.value) + cache.usersWithCachedApps()
                for (user in reconcileUsers) safely("reconcile packages of user ${user.value}") { cache.reconcile(user) }
            }
            lastFinished = elapsedRealtime()
        }
    }

    private fun enqueue(packageName: String) {
        synchronized(pending) {
            val event = Event(elapsedRealtime())
            val existing = pending[packageName]
            if (existing != null) {
                existing.latest = event
                return
            }
            val change = PendingChange(event)
            pending[packageName] = change
            scope.launch(ioDispatcher) { refreshChanges(packageName, change) }
        }
    }

    private suspend fun refreshChanges(packageName: String, change: PendingChange) {
        try {
            while (true) {
                val event = synchronized(pending) { change.latest }
                delay(DEBOUNCE_MILLIS - (elapsedRealtime() - event.receivedAt))
                controlActivity.awaitIdle(packageName)
                // Include changes received while waiting for a control or the quiet period.
                val refreshed = cache.refreshAfterPackageChange(AppKey(packageName, users.ownUser)) {
                    synchronized(pending) { change.latest === event }
                }
                if (!refreshed) continue
                synchronized(pending) {
                    if (change.latest === event) {
                        pending.remove(packageName)
                        return
                    }
                }
            }
        } finally {
            synchronized(pending) { pending.remove(packageName, change) }
        }
    }

    private inline fun safely(operation: String, action: () -> Unit): Boolean = try {
        action()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Cannot $operation")
        false
    }

    private class Event(val receivedAt: Long)
    private class PendingChange(var latest: Event)

    private companion object {
        const val PACKAGE_SCHEME = "package"
        const val DEBOUNCE_MILLIS = 500L
        const val FOREGROUND_INTERVAL_MILLIS = 30_000L
        val PACKAGE_CHANGES = setOf(
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_FULLY_REMOVED,
            Intent.ACTION_PACKAGE_CHANGED,
        )
    }
}
