// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

/** What one run of the wake feature may change, decided once from the privileged channel. */
sealed interface WakeWrites {
    /** The channel may write what the feature changes: it enforces. */
    data object Enforce : WakeWrites

    /** The run observes only; [status] is the protection status that says why. */
    data class ObserveOnly(val status: String) : WakeWrites
}

interface WakeProtectionRepository {
    /** Checks what the current identity may change without prompting for access. */
    suspend fun writeAccess(): WakeWrites

    /** Resets one package's managed firewall rules, or all managed rules when [packageName] is null. */
    suspend fun resetFirewall(packageName: String?): Boolean

    suspend fun syncUserFirewallRules(writes: WakeWrites)

    suspend fun applyReviewedPaths(capture: WakeImport, writes: WakeWrites, now: Long): String

    suspend fun revokeStaleFirewallRules(writes: WakeWrites)

    suspend fun supportsPeerFilter(): Boolean
}
