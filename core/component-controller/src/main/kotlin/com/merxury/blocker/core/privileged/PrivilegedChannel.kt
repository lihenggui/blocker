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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The privileged service Blocker reaches the system through: a root process, or Shizuku, as the
 * controller type the user chose says. One channel serves every user on the device.
 */
interface PrivilegedChannel {
    /**
     * Last known state; starts [ChannelState.Unavailable] with [UnavailableReason.NOT_CHECKED]
     * until the first attempt to acquire the channel ends, which [connectIfGranted] makes as soon
     * as the controller type is known. It changes on [connect] and [connectIfGranted], when a call
     * finds the channel gone, and when the user picks a controller type that uses the other
     * service.
     */
    val state: StateFlow<ChannelState>

    /**
     * Acquires the channel (this may show the su or Shizuku permission prompt) and updates
     * [state]. Returns the new state; never throws except for cancellation.
     */
    suspend fun connect(): ChannelState

    /**
     * Acquires the channel quietly. Blocker requests no permission dialog. Shizuku must already
     * be granted, and Blocker starts su only under a remembered root grant. Without a grant the
     * result is [UnavailableReason.DENIED]. A remembered grant also allows replacing a root server
     * that closed while idle.
     *
     * Updates [state] and returns the result. Reuses a current connection or waits for an attempt
     * already in progress. Never throws except for cancellation.
     *
     * The remembered grant records whether su started a root process the last time it was asked.
     * A root manager can refuse or show its own prompt if that grant has since expired or been
     * revoked.
     */
    suspend fun connectIfGranted(): ChannelState
}

/**
 * Applies the quiet permission policy from [PrivilegedChannel.connectIfGranted] to nested reads,
 * writes and retries. Blocker requests no permission dialog and starts su only under a remembered
 * grant.
 */
suspend fun <T> withoutPermissionPrompts(block: suspend () -> T): T = withContext(NoPermissionPrompts) { block() }

internal suspend fun permissionPromptsAllowed(): Boolean = currentCoroutineContext()[NoPermissionPrompts.Key] == null

private object NoPermissionPrompts : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<NoPermissionPrompts>
}
