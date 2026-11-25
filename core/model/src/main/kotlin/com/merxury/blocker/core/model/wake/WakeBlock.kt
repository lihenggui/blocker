// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.model.wake

import com.merxury.blocker.core.model.ComponentType

data class WakeBlock(
    val pathKey: String,
    val target: String,
    val method: WakeBlockMethod,
    val componentType: ComponentType?,
    val sender: String?,
    val component: String?,
    val action: String?,
    val blockedAtMillis: Long,
)

/** Why some requested paths were not blocked or unblocked. */
sealed interface WakeBlockFailure {
    /** Blocker has no root or Shizuku access. */
    data object NoAccess : WakeBlockFailure

    /** The channel runs as the ADB shell, which cannot write Intent Firewall rules or disable regular apps' components. */
    data object NeedsRoot : WakeBlockFailure

    /** This system ignores sender package filters, so a rule for one sender cannot be written. */
    data class SenderFilterUnsupported(val pathKeys: List<String>) : WakeBlockFailure

    /** These paths cannot be cut with the requested method, such as a provider through the firewall. */
    data class NotBlockable(val pathKeys: List<String>) : WakeBlockFailure

    /** The system failed, with its message when it gave one. */
    data class SystemError(val message: String?) : WakeBlockFailure
}

/** What a block or unblock request changed: the paths done, and the first failure for the rest. */
data class WakeBlockOutcome(val done: Set<String>, val failure: WakeBlockFailure?)
