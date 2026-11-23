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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * What both transports share: the [state], one connect at a time, connecting before a call when
 * needed, and turning what a call threw into a [CallFailure] with [toCallFailure].
 *
 * [checkedState], and with it [executeIfReady], on a channel nothing has tried to acquire yet
 * first makes the attempt that cannot prompt, [connectIfGranted], or waits for the one already
 * running, so a caller started in the background gets the privilege the user granted earlier.
 *
 * Quiet attempts and connects each run one at a time under their own lock, and only `withLock`
 * guards them, so a cancelled participant always lets the next one go. A connect waits for a quiet
 * attempt already running, but not the other way round: a quiet attempt a reader starts while a
 * connect prompts runs at the same time, and its result never replaces one the connect published
 * meanwhile. Each transport's acquire tolerates that overlap.
 */
internal abstract class AbstractPrivilegedTransport : PrivilegedTransport {

    private val mutableState = MutableStateFlow<ChannelState>(NOT_CHECKED)
    override val state: StateFlow<ChannelState> = mutableState.asStateFlow()

    // Held by connect, which may prompt. It takes quietLock only to wait for a quiet attempt.
    private val connectLock = Mutex()

    // Held by each quiet attempt, which never prompts; readers queue here and never behind a prompt.
    private val quietLock = Mutex()

    // Guards connectsPublished together with publishing a result.
    private val publishLock = Any()

    // How many connects have published their result, so a quiet attempt can tell one landed while it ran.
    private var connectsPublished = 0L

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

    override suspend fun connect(): ChannelState = connectLock.withLock {
        // A quiet attempt already running decides first, so the two rarely acquire at once.
        quietLock.withLock { }
        acquire().also { result ->
            synchronized(publishLock) {
                connectsPublished++
                mutableState.value = result
            }
        }
    }

    override suspend fun connectIfGranted(): ChannelState {
        (state.value as? ChannelState.Ready)?.let { return it }
        // A connect already running decides for this call too. Not holding quietLock while
        // waiting keeps readers from waiting for its prompt.
        connectLock.withLock { }
        return quietLock.withLock { (state.value as? ChannelState.Ready) ?: attemptQuietly() }
    }

    /**
     * Makes the quiet attempt, or waits for the one running and then reads what it decided.
     * Readers wait only behind quiet attempts, never behind a [connect], so one that is
     * prompting does not hold them up; a reader may then make its quiet attempt while the prompt
     * is open, and [attemptQuietly] keeps the connect's result if it lands first.
     */
    override suspend fun checkedState(): ChannelState {
        state.value.let { if (it != NOT_CHECKED) return it }
        quietLock.withLock { if (state.value == NOT_CHECKED) attemptQuietly() }
        return state.value
    }

    /**
     * Acquires without a prompt and publishes the result, unless a [connect] published one while
     * this ran: what the user just answered stands. Returns the state afterwards. Call it holding
     * [quietLock].
     */
    private suspend fun attemptQuietly(): ChannelState {
        val connectsBefore = synchronized(publishLock) { connectsPublished }
        val result = acquireIfGranted()
        synchronized(publishLock) {
            if (connectsPublished == connectsBefore) mutableState.value = result
        }
        return state.value
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
