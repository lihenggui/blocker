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
 */
internal abstract class AbstractPrivilegedTransport : PrivilegedTransport {

    private val mutableState = MutableStateFlow<ChannelState>(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
    override val state: StateFlow<ChannelState> = mutableState.asStateFlow()

    private val connectLock = Mutex()

    /** Acquires the channel, which may show a permission prompt. Throws only cancellation. */
    protected abstract suspend fun acquire(): ChannelState

    /**
     * Runs [call] through the channel. Returns the outcome when the other side of the channel
     * already judged it, or throws: [ChannelLostException] when the channel is gone, otherwise
     * whatever the call threw.
     */
    protected abstract suspend fun <R> send(call: PrivilegedCall<R>): CallResult<R>

    override suspend fun connect(): ChannelState = connectLock.withLock {
        acquire().also { mutableState.value = it }
    }

    override suspend fun <R> execute(call: PrivilegedCall<R>): CallResult<R> {
        if (state.value !is ChannelState.Ready) connect()
        return executeIfReady(call)
    }

    override suspend fun <R> executeIfReady(call: PrivilegedCall<R>): CallResult<R> {
        val ready = state.value as? ChannelState.Ready
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
            CallFailure.ChannelUnavailable -> {
                Timber.w(cause, "Privileged channel lost while running $call")
                val reason = cause?.lostReason() ?: UnavailableReason.FAILED
                // A connect that already replaced the lost state wins.
                mutableState.update { current -> if (current == ready) ChannelState.Unavailable(reason) else current }
            }

            is CallFailure.SystemError -> Timber.e(cause, "Privileged call $call failed: ${failure.message}")

            // Expected outcomes the caller handles; they say nothing about the channel.
            is CallFailure.NotPermitted, is CallFailure.TargetNotFound -> Unit
        }
    }
}
