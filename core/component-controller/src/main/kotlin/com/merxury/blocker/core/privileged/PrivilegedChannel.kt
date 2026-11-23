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

import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import kotlinx.coroutines.flow.StateFlow

/**
 * The privileged service Blocker reaches the system through: a root process, or Shizuku, as the
 * controller type the user chose says. One channel serves every user on the device.
 */
interface PrivilegedChannel {
    /**
     * Last known state; starts [ChannelState.Unavailable] with [UnavailableReason.NOT_CHECKED].
     * It changes on [connect], when a call finds the channel gone, and when the user picks a
     * controller type that uses the other service.
     */
    val state: StateFlow<ChannelState>

    /**
     * Acquires the channel (this may show the su or Shizuku permission prompt) and updates
     * [state]. Returns the new state; never throws except for cancellation.
     */
    suspend fun connect(): ChannelState
}
