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
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.privileged.PrivilegedChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the cached users current while Blocker runs, so a work profile added or removed meanwhile
 * shows in every profile switcher without a restart: reads the users again when the app comes to
 * the foreground, see [onForeground], and when the system announces that a work profile was added,
 * removed, turned on, turned off, unlocked or stopped. These announcements need no receiver
 * permission. Reads resume when the privileged channel becomes ready. Requests that arrive
 * during a read collapse into one more read.
 *
 * Reads only while the privileged channel is ready: without it the system shows only the user
 * Blocker runs in, and storing that would drop the other users' cached apps.
 */
@Singleton
class DeviceUserMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val deviceUsers: DeviceUserRepository,
    private val channel: PrivilegedChannel,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val started = AtomicBoolean(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action in PROFILE_CHANGES) requests.trySend(Unit)
        }
    }

    /** Starts listening for profile changes; later calls do nothing. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        val filter = IntentFilter().apply { PROFILE_CHANGES.forEach(::addAction) }
        // System broadcasts still reach a receiver that other apps cannot send to.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        scope.launch {
            for (request in requests) {
                if (channel.state.value !is ChannelState.Ready) continue
                try {
                    deviceUsers.refresh()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Cannot refresh device users")
                }
            }
        }
        scope.launch {
            channel.state.map { it is ChannelState.Ready }.distinctUntilChanged().collect { ready ->
                if (ready) requests.trySend(Unit)
            }
        }
    }

    /** Reads the users again, as when the app comes back to the foreground. */
    fun onForeground() {
        requests.trySend(Unit)
    }

    private companion object {
        // Hidden in the SDK. Android sends this protected broadcast to all running users
        // after the user has stopped, without a receiver permission.
        const val ACTION_USER_STOPPED = "android.intent.action.USER_STOPPED"

        val PROFILE_CHANGES = listOf(
            Intent.ACTION_MANAGED_PROFILE_ADDED,
            Intent.ACTION_MANAGED_PROFILE_REMOVED,
            Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
            Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
            Intent.ACTION_MANAGED_PROFILE_UNLOCKED,
            ACTION_USER_STOPPED,
        )
    }
}
