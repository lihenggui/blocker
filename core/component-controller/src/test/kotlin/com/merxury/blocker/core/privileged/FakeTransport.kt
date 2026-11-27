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

import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import kotlinx.coroutines.CompletableDeferred

/**
 * A transport that runs calls against [system] in this process, with switches for what acquiring
 * gives and what a call throws.
 */
internal class FakeTransport(
    val system: FakeSystemServices = FakeSystemServices(),
) : AbstractPrivilegedTransport() {
    /** What the next [connect] gives. */
    var acquireResult: ChannelState = ChannelState.Ready(PrivilegedIdentity.ROOT)
    var acquireCount = 0
        private set

    var connectionCurrent = true

    /** When set, [connect] waits for it, as a transport waits for a prompt to be answered. */
    var acquireGate: CompletableDeferred<Unit>? = null

    /** What the next [connectIfGranted] gives: by default, nothing was granted. */
    var quietAcquireResult: ChannelState = ChannelState.Unavailable(UnavailableReason.DENIED)
    var quietAcquireCount = 0
        private set

    /** When set, [connectIfGranted] waits for it, as a transport waits for su or a binder. */
    var quietAcquireGate: CompletableDeferred<Unit>? = null

    /** Thrown by the next call instead of running it. */
    var throwOnSend: Exception? = null
    val sent = mutableListOf<PrivilegedCall<*>>()

    override suspend fun isConnectionCurrent(): Boolean = connectionCurrent

    override suspend fun acquire(): ChannelState {
        acquireCount++
        acquireGate?.await()
        return acquireResult
    }

    override suspend fun acquireIfGranted(): ChannelState {
        quietAcquireCount++
        quietAcquireGate?.await()
        return quietAcquireResult
    }

    override suspend fun <R> send(call: PrivilegedCall<R>): CallResult<R> {
        sent += call
        throwOnSend?.let { throw it }
        return CallResult.Success(call.run(system))
    }
}
