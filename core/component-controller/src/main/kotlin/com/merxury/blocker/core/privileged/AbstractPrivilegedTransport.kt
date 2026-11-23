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

package com.merxury.blocker.core.privileged

import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber

/**
 * What both transports share: the [state], one connect at a time, connecting before a call when
 * needed, and turning what a call threw into a [CallFailure] with [toCallFailure].
 *
 * [checkedState], and with it [executeIfReady], on a channel nothing has tried to acquire yet
 * first makes the attempt that cannot prompt, [connectIfGranted], or waits for the one already
 * running, so a caller started in the background gets the privilege the user granted earlier.
 */
internal abstract class AbstractPrivilegedTransport : PrivilegedTransport {

    private val mutableState = MutableStateFlow<ChannelState>(NOT_CHECKED)
    override val state: StateFlow<ChannelState> = mutableState.asStateFlow()

    // Held while acquiring, by connect and connectIfGranted alike; nothing that holds it calls them.
    private val connectLock = Mutex()

    // Counts the times connectLock was released, so checkedState can wait for an attempt to end.
    private val attemptsEnded = MutableStateFlow(0L)

    /** Acquires the channel, which may show a permission prompt. Throws only cancellation. */
    protected abstract suspend fun acquire(): ChannelState

    /**
     * Acquires the channel only if that cannot show a prompt, and otherwise returns why it stays
     * unavailable. Throws only cancellation.
     */
    protected abstract suspend fun acquireIfGranted(): ChannelState

    /**
     * Runs [call] through the channel. Returns the outcome when the other side of the channel
     * already judged it, or throws: [ChannelLostException] when the channel is gone, otherwise
     * whatever the call threw.
     */
    protected abstract suspend fun <R> send(call: PrivilegedCall<R>): CallResult<R>

    override suspend fun connect(): ChannelState = acquiring {
        acquire().also { updateState { _ -> it } }
    }

    override suspend fun connectIfGranted(): ChannelState {
        (state.value as? ChannelState.Ready)?.let { return it }
        return acquiring {
            (state.value as? ChannelState.Ready) ?: acquireIfGranted().also { updateState { _ -> it } }
        }
    }

    /**
     * Makes the first attempt to acquire without a prompt when the lock is free, and otherwise
     * waits only for the attempt holding it to end: a [connect] queued behind a quiet attempt
     * must not hold up a reader once the quiet attempt has decided.
     */
    override suspend fun checkedState(): ChannelState {
        while (true) {
            state.value.let { if (it != NOT_CHECKED) return it }
            val ended = attemptsEnded.value
            if (connectLock.tryLock()) {
                try {
                    if (state.value == NOT_CHECKED) acquireIfGranted().also { updateState { _ -> it } }
                } finally {
                    release()
                }
                return state.value
            }
            // Ends when the holder finishes or is cancelled; then the state or the lock decides.
            attemptsEnded.first { it != ended }
        }
    }

    private suspend inline fun <T> acquiring(block: () -> T): T {
        connectLock.lock()
        try {
            return block()
        } finally {
            release()
        }
    }

    // Unlocks before counting, so a waiter that wakes up finds the lock free.
    private fun release() {
        connectLock.unlock()
        attemptsEnded.update { it + 1 }
    }

    /**
     * Changes [state] atomically with [transform], which may run more than once. Safe from any
     * thread and against a concurrent [connect]; whichever finishes last decides.
     */
    protected fun updateState(transform: (ChannelState) -> ChannelState) {
        mutableState.update(transform)
    }

    override suspend fun <R> execute(call: PrivilegedCall<R>): CallResult<R> {
        if (state.value !is ChannelState.Ready) connect()
        return executeIfReady(call)
    }

    override suspend fun <R> executeIfReady(call: PrivilegedCall<R>): CallResult<R> {
        val ready = checkedState() as? ChannelState.Ready
            ?: return CallResult.Failure(CallFailure.ChannelUnavailable)
        return try {
            send(call).also { result ->
                if (result is CallResult.Failure) onFailure(ready, call, result.reason, cause = null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failure = e.toCallFailure()
            onFailure(ready, call, failure, e)
            CallResult.Failure(failure)
        }
    }

    private fun onFailure(ready: ChannelState.Ready, call: PrivilegedCall<*>, failure: CallFailure, cause: Exception?) {
        when (failure) {
            // The channel still answers calls that need no privileged process.
            CallFailure.ChannelUnavailable if cause is PrivilegedProcessUnavailableException ->
                Timber.w(cause, "$call could not reach the privileged process")

            CallFailure.ChannelUnavailable -> {
                Timber.w(cause, "Privileged channel lost while running $call")
                val reason = cause?.lostReason() ?: UnavailableReason.FAILED
                // A connect that already replaced the lost state wins.
                updateState { current -> if (current == ready) ChannelState.Unavailable(reason) else current }
            }

            is CallFailure.SystemError -> Timber.e(cause, "Privileged call $call failed: ${failure.message}")

            // Expected outcomes the caller handles; they say nothing about the channel.
            is CallFailure.NotPermitted, is CallFailure.TargetNotFound -> Unit
        }
    }

    private companion object {
        val NOT_CHECKED = ChannelState.Unavailable(UnavailableReason.NOT_CHECKED)
    }
}
