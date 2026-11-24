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

package com.merxury.blocker.core.testing.controller

import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.privileged.PrivilegedChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/** A [PrivilegedChannel] whose state tests set; [connect] moves it to [connectResult]. */
class FakePrivilegedChannel(
    initial: ChannelState = ChannelState.Ready(PrivilegedIdentity.ROOT),
) : PrivilegedChannel {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<ChannelState> = mutableState

    /** What [connect] gives. */
    var connectResult: ChannelState = ChannelState.Ready(PrivilegedIdentity.ROOT)

    var connectCount = 0
        private set

    override suspend fun connect(): ChannelState {
        connectCount++
        mutableState.value = connectResult
        return connectResult
    }

    /**
     * While true, [connectIfGranted] waits for an unchecked channel to be checked, as the real one
     * waits for the first check, which needs the controller type; tests end the wait with [setState].
     */
    var firstCheckPending = false

    /**
     * Never prompts and never changes the state: it answers with what the channel has, after the
     * first check when [firstCheckPending] is set.
     */
    override suspend fun connectIfGranted(): ChannelState = if (firstCheckPending) {
        mutableState.first { it != ChannelState.Unavailable(UnavailableReason.NOT_CHECKED) }
    } else {
        mutableState.value
    }

    /** Sets the state as a connect, a lost channel or a change of controller type would. */
    fun setState(state: ChannelState) {
        mutableState.value = state
    }
}
