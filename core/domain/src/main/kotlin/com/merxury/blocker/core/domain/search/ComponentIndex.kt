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

package com.merxury.blocker.core.domain.search

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.RefreshProgress
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.privileged.PrivilegedChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the components of every app of a user into the component cache, so a search finds the
 * components of apps that were never opened. Each user is indexed once per run of the app, in
 * the application's scope so leaving the screen does not stop it, and again when asked to or when
 * it ran with less access than the privileged channel has now. Kept in memory only: nothing is
 * persisted, so each start of the app indexes again when first needed.
 */
@Singleton
class ComponentIndex @Inject constructor(
    private val componentStatuses: ComponentStatusRepository,
    private val channel: PrivilegedChannel,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val states = MutableStateFlow<Map<UserId, IndexState>>(emptyMap())
    private val jobs = mutableMapOf<UserId, Job>()
    private var nextRun = 0L

    /** How far the indexing of [user] got in this run of the app. */
    fun state(user: UserId): Flow<IndexState> = states.map { it[user] ?: IndexState.NotIndexed }.distinctUntilChanged()

    /**
     * Indexes [user] unless that is running, or it ran in this run of the app with the access the
     * privileged channel has now. Never connects the channel, so it never shows a prompt.
     */
    fun ensureIndexed(user: UserId) = synchronized(jobs) {
        when (val state = states.value[user]) {
            is IndexState.Indexing -> return
            is IndexState.Indexed -> if (state.exception == null && !state.couldReadMore(channel.state.value)) return
            IndexState.NotIndexed, null -> Unit
        }
        start(user)
    }

    /** Indexes [user] again, as an explicit refresh asks; one running is started over. */
    fun reindex(user: UserId) = synchronized(jobs) {
        jobs.remove(user)?.cancel()
        start(user)
    }

    private fun start(user: UserId) {
        val run = nextRun++
        states.update { it + (user to IndexState.Indexing(run, done = 0, total = 0)) }
        jobs[user] = scope.launch {
            // Until the first access check ends, another user cannot be read and the firewall
            // state would be stored as unknown, so wait for it, but not forever.
            withTimeoutOrNull(ACCESS_CHECK_TIMEOUT_MILLIS) { channel.state.first { it != NOT_CHECKED } }
            val identity = (channel.state.value as? ChannelState.Ready)?.identity
            try {
                refresh(user, run, identity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Cannot index user ${user.value}")
                publish(user, run, IndexState.Indexed(run, 0, 0, null, identity, exception = e))
            }
        }.also { job ->
            job.invokeOnCompletion { cause ->
                synchronized(jobs) {
                    if (jobs[user] === job) jobs.remove(user)
                    // Cancelled before it finished, as by a restart or the end of the app.
                    if (cause != null) states.update { current -> if ((current[user] as? IndexState.Indexing)?.run == run) current - user else current }
                }
            }
        }
    }

    private suspend fun refresh(user: UserId, run: Long, identity: PrivilegedIdentity?) {
        componentStatuses.refreshAll(user).collect { progress ->
            val state = when (progress) {
                is RefreshProgress.Refreshing -> IndexState.Indexing(run, progress.done, progress.total)

                is RefreshProgress.Finished -> {
                    // Access lost or changed during the run left the apps read since with less.
                    val atEnd = (channel.state.value as? ChannelState.Ready)?.identity
                    val throughout = identity.takeIf { it == atEnd }
                    Timber.i("Indexed ${progress.total} apps of user ${user.value}, ${progress.failed} failed, as $identity until $atEnd")
                    IndexState.Indexed(run, progress.total, progress.failed, progress.appListFailure, throughout)
                }
            }
            publish(user, run, state)
        }
    }

    private fun publish(user: UserId, run: Long, state: IndexState) {
        states.update { current ->
            if ((current[user] as? IndexState.Indexing)?.run == run) current + (user to state) else current
        }
    }

    private companion object {
        val NOT_CHECKED = ChannelState.Unavailable(UnavailableReason.NOT_CHECKED)

        /** How long indexing waits for the first access check before it reads what it can. */
        const val ACCESS_CHECK_TIMEOUT_MILLIS = 10_000L
    }
}

/** How far the indexing of one user's apps got. Each indexing has its own [run]. */
sealed interface IndexState {
    /** Not indexed in this run of the app. */
    data object NotIndexed : IndexState

    /** [done] of [total] apps are read; [total] is 0 until the user's apps are listed. */
    data class Indexing(val run: Long, val done: Int, val total: Int) : IndexState

    /**
     * Every app was read once.
     *
     * @property failed apps that could not be read in full; their unread parts show as unknown
     * @property appListFailure why the user's apps could not be listed; then nothing was read
     * @property identity what the privileged channel ran as throughout the run, or null when it
     * was not ready or did not run as the same identity from the start to the end, so the parts
     * that need it may show as unknown
     * @property exception an unexpected failure while indexing, if it could not finish
     */
    data class Indexed(
        val run: Long,
        val total: Int,
        val failed: Int,
        val appListFailure: ReadFailure?,
        val identity: PrivilegedIdentity?,
        val exception: Throwable? = null,
    ) : IndexState {
        /** Whether some state could not be read: an app failed or the channel was not ready. */
        val incomplete: Boolean get() = failed > 0 || appListFailure != null || identity == null || exception != null

        /** Whether indexing again could read more, as the channel now runs as another identity. */
        fun couldReadMore(now: ChannelState): Boolean = now is ChannelState.Ready && now.identity != identity
    }
}
