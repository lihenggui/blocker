// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.model.wake

sealed interface WakeAvailability {
    /** Root or Shizuku is unavailable or access has not been granted. */
    data object NoAccess : WakeAvailability

    /** Blocker runs outside the owner profile, where the wake feature does not collect. */
    data object NotOwnerProfile : WakeAvailability

    /** The privileged channel refuses every read, as Shizuku does before version 13. */
    data object ReadsRefused : WakeAvailability

    /** Access is ready, with no stored findings or recorded successful detection. */
    data object Collecting : WakeAvailability

    /**
     * The latest detection succeeded or findings are stored.
     * [lastCheckedMillis] is the wall-clock time of the latest detection if it succeeded, or null otherwise.
     */
    data class Ready(val lastCheckedMillis: Long?) : WakeAvailability
}
