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
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import java.util.Collections

/** Answers every call with [answer] and records each with whether it could connect. */
internal class ScriptedExecutor : PrivilegedExecutor {
    var answer: suspend (PrivilegedCall<*>) -> CallResult<*> = { CallResult.Failure(CallFailure.ChannelUnavailable) }
    val runs: MutableList<Pair<PrivilegedCall<*>, Boolean>> = Collections.synchronizedList(mutableListOf())

    override suspend fun <R> execute(call: PrivilegedCall<R>): CallResult<R> = run(call, connect = true)

    override suspend fun <R> executeIfReady(call: PrivilegedCall<R>): CallResult<R> = run(call, connect = false)

    override suspend fun checkedState(): ChannelState = ChannelState.Ready(PrivilegedIdentity.ROOT)

    @Suppress("UNCHECKED_CAST")
    private suspend fun <R> run(call: PrivilegedCall<R>, connect: Boolean): CallResult<R> {
        runs += call to connect
        return answer(call) as CallResult<R>
    }
}
