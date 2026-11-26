// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.model.wake

import com.merxury.blocker.core.model.ComponentType

/** How Blocker could cut one wake path. */
enum class WakeBlockMethod {
    /** An Intent Firewall rule for one sender and one service. */
    FIREWALL_RULE,

    /** Disables the named component or receivers for the action for every caller. */
    COMPONENT,

    /** Only Android's background restriction of the whole app limits this path. */
    BACKGROUND_RESTRICTION,

    /** Blocker cannot cut the path here. */
    NONE,
}

/** Which way the wakes between the shown app and one other app go. */
enum class WakeDirection { INBOUND, OUTBOUND, MUTUAL }

/** Keep-alive the app does on its own. */
enum class OwnWakeKind { RESTART_AFTER_CLOSE, SELF_START, SYSTEM_EVENT, TIMED, SCREEN_OFF_RUN }

/** One finding path summed over a window. */
data class WakePath(
    val pathKey: String,
    val target: String,
    val actor: String?,
    val trigger: ReviveTrigger?,
    val hold: HoldCause?,
    /** Fully qualified class in [target] when the detail names a component. */
    val component: String?,
    val componentType: ComponentType?,
    /** The broadcast action when the detail names one. */
    val action: String?,
    val count: Long,
    val durationNanos: Long,
    val screenOffNanos: Long,
    /** Wall-clock milliseconds of the latest start in the window. */
    val lastSeenMillis: Long,
    val afterKill: Boolean,
    val blockMethod: WakeBlockMethod,
)

/** Wakes between the shown app and [peer]. */
data class WakePeerLink(
    val peer: String,
    val direction: WakeDirection,
    /** [peer] waking or holding the shown app. */
    val inbound: List<WakePath>,
    /** The shown app waking or holding [peer]. */
    val outbound: List<WakePath>,
)

/** One own keep-alive method, its paths grouped by component, action or hold cause. */
data class OwnWakeMethod(val kind: OwnWakeKind, val component: String?, val action: String?, val paths: List<WakePath>)

/** Starts with a reason the user accepts, by reason. */
data class JustifiedStarts(val userInitiated: Long, val notification: Long, val push: Long, val grantedRole: Long)

data class AppWakeActivity(
    val packageName: String,
    /** The worst daily verdict in the window, or [WakeVerdict.NORMAL] when there is none. */
    val verdict: WakeVerdict,
    /** Revives of the app in the window that the user did not start. */
    val backgroundStarts: Long,
    val peers: List<WakePeerLink>,
    val own: List<OwnWakeMethod>,
    val justified: JustifiedStarts,
)

/** The strongest evidence for one overview row. */
sealed interface WakeHeadline {
    data class RestartedAfterClose(val times: Long) : WakeHeadline
    data class StartedOthers(val apps: Int) : WakeHeadline
    data class StartedByOthers(val apps: Int, val times: Long) : WakeHeadline
    data class RanWithScreenOff(val nanos: Long) : WakeHeadline
    data class StartsOnSystemEvent(val action: String, val times: Long) : WakeHeadline
    data class TimedWakeups(val times: Long) : WakeHeadline
    data class BackgroundStarts(val times: Long) : WakeHeadline
}

data class AppWakeSummary(
    val packageName: String,
    val verdict: WakeVerdict,
    val keepAliveDays: Int,
    val suspiciousDays: Int,
    val headline: WakeHeadline,
    val allBlocked: Boolean = false,
)
