// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.model.wake

enum class PotentialWakeCategory {
    BOOT_AUTOSTART,
    PUSH_RECEIVER,
    SCHEDULED_JOB,
    ACCOUNT_SYNC,
    SYSTEM_BROADCAST,
}

/** An autostart or background-wake entry declared in the app's manifest, with its live block state. */
data class PotentialWakeEntry(
    val category: PotentialWakeCategory,
    val state: WakePathState,
)
