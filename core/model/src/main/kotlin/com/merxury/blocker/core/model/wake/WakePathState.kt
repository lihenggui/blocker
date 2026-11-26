// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.model.wake

/** Who keeps a wake path from running. */
enum class WakePathBlock { NONE, USER, AUTOMATIC }

data class WakePathState(
    val path: WakePath,
    val block: WakePathBlock,
    /** When the user blocked it on the wake screens; null otherwise. */
    val blockedAtMillis: Long?,
    /** Whether the path started again after [blockedAtMillis]. */
    val seenSinceBlock: Boolean,
)

/** A path reviewed by automatic wake protection and its current state. */
data class AutomaticWakeBlock(
    val pathId: String,
    val caller: String,
    val target: String,
    val component: String,
    val action: String,
    /** Whether the rule is configured and automatic blocking remains enabled. */
    val active: Boolean,
    /** Whether the user has allowed the path. */
    val allowed: Boolean,
)

enum class WakeSpecialAccess {
    ACCESSIBILITY,
    NOTIFICATION_LISTENER,
    DEVICE_ADMIN,
    ALWAYS_ON_VPN,
    COMPANION_DEVICE,
    INPUT_METHOD,
    LIVE_WALLPAPER,
    WIDGET,
    SYNC_ADAPTER,
}

data class WakeAppEnvironment(
    val userIdleAllowlisted: Boolean = false,
    val systemIdleAllowlisted: Boolean = false,
    val standbyBucket: Int? = null,
    val specialAccess: Set<WakeSpecialAccess> = emptySet(),
)

data class AppWakeState(
    val activity: AppWakeActivity,
    /** Every blockable path of [activity] by path key. */
    val paths: Map<String, WakePathState>,
    /** Active or user-allowed automatic blocks involving this app that match no path of [activity]. */
    val automatic: List<AutomaticWakeBlock>,
    /** Whether every blockable path and every automatic entry is blocked. */
    val allBlocked: Boolean,
    val environment: WakeAppEnvironment = WakeAppEnvironment(),
)
