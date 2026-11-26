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

/** A reviewed path Blocker blocks, or blocked until the user allowed it again. */
data class AutomaticWakeBlock(
    val pathId: String,
    val caller: String,
    val target: String,
    val component: String,
    val action: String,
    val active: Boolean,
)

data class AppWakeState(
    val activity: AppWakeActivity,
    /** Every blockable path of [activity] by path key. */
    val paths: Map<String, WakePathState>,
    /** Automatic blocks involving this app that match no path of [activity]. */
    val automatic: List<AutomaticWakeBlock>,
    /** Whether every blockable path and every automatic entry is blocked. */
    val allBlocked: Boolean,
)
