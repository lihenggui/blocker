// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.privileged.PrivilegedChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** When the wake feature can work: it reads and writes only through the privileged channel. */
class WakeAuthorizationRepository @Inject constructor(channel: PrivilegedChannel) {
    /**
     * The identity the privileged channel is ready as, or null while it is unavailable. Any
     * identity reads evidence; root and the system uid also write. Observing it never connects
     * the channel, so it never shows a prompt.
     */
    val readyIdentity: Flow<PrivilegedIdentity?> = channel.state
        .map { (it as? ChannelState.Ready)?.identity }
        .distinctUntilChanged()
}
