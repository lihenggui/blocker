// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

/**
 * Kills after which a restart means the app came back although the user or the system wanted it
 * gone. The vocabulary follows the Android 15 sources of ActivityManagerService, ProcessList,
 * OomAdjuster, CachedAppOptimizer and BroadcastQueueModernImpl, and AppExitReasonCode /
 * AppExitSubReasonCode of app_enums.proto. Housekeeping kills do not qualify: a stopping user,
 * package install, update, removal or component change, isolated or sandbox processes no longer
 * needed, crashes, ANRs, start failures, dependency deaths and freezer errors.
 */
internal object WakeKillReasons {
    // am_kill reason strings. A force stop is "stop <package> due to <reason>"; only a stop requested
    // through ActivityManager.forceStopPackage without an internal reason is reported "from pid".
    private val qualifyingText = listOf(
        Regex("""stop \S+ due to from pid \d+.*"""),
        Regex("""fully stop \S+ by user request"""),
        Regex("""remove task"""),
        Regex("""kill background"""),
        Regex("""kill all background(?: except)?"""),
        Regex("""empty(?: #\d+| for \d+s)?"""),
        Regex("""cached #\d+"""),
        Regex("""swap low and too many cached"""),
        Regex("""excessive cpu .*"""),
        Regex("""excessive binder traffic during cached"""),
        Regex("""cached idle & background restricted"""),
        Regex("""Too many outgoing broadcasts in cached state"""),
    )

    private const val REASON_LOW_MEMORY = 3
    private const val REASON_EXCESSIVE_RESOURCE_USAGE = 9
    private const val REASON_USER_REQUESTED = 10
    private const val REASON_OTHER = 13

    // REASON_USER_REQUESTED + SUBREASON_FORCE_STOP is not here: forceStopPackageLocked tags every
    // internal force stop (clear data, package removal, instrumentation) that way. A user force
    // stop is recognized by its am_kill text instead.
    private val userRequested = setOf(22, 23, 24) // REMOVE_TASK, STOP_APP, KILL_BACKGROUND

    // TOO_MANY_CACHED, TOO_MANY_EMPTY, TRIM_EMPTY, LARGE_CACHED, MEMORY_PRESSURE,
    // CACHED_IDLE_FORCED_APP_STANDBY, EXCESSIVE_OUTGOING_BROADCASTS_WHILE_CACHED.
    private val trimmed = setOf(2, 3, 4, 5, 6, 18, 32)

    private val reasonNames = listOf(
        "UNKNOWN", "EXIT_SELF", "SIGNALED", "LOW_MEMORY", "CRASH", "CRASH_NATIVE", "ANR", "INITIALIZATION_FAILURE",
        "PERMISSION_CHANGE", "EXCESSIVE_RESOURCE_USAGE", "USER_REQUESTED", "USER_STOPPED", "DEPENDENCY_DIED", "OTHER",
        "FREEZER", "PACKAGE_STATE_CHANGE", "PACKAGE_UPDATED",
    )
    private val subReasonNames = listOf(
        "UNKNOWN", "WAIT_FOR_DEBUGGER", "TOO_MANY_CACHED", "TOO_MANY_EMPTY", "TRIM_EMPTY", "LARGE_CACHED",
        "MEMORY_PRESSURE", "EXCESSIVE_CPU", "SYSTEM_UPDATE_DONE", "KILL_ALL_FG", "KILL_ALL_BG_EXCEPT", "KILL_UID",
        "KILL_PID", "INVALID_START", "INVALID_STATE", "IMPERCEPTIBLE", "REMOVE_LRU", "ISOLATED_NOT_NEEDED",
        "CACHED_IDLE_FORCED_APP_STANDBY", "FREEZER_BINDER_IOCTL", "FREEZER_BINDER_TRANSACTION", "FORCE_STOP",
        "REMOVE_TASK", "STOP_APP", "KILL_BACKGROUND", "PACKAGE_UPDATE", "UNDELIVERED_BROADCAST", "SDK_SANDBOX_DIED",
        "SDK_SANDBOX_NOT_NEEDED", "EXCESSIVE_BINDER_OBJECTS", "OOM_KILL", "FREEZER_BINDER_ASYNC_FULL",
        "EXCESSIVE_OUTGOING_BROADCASTS_WHILE_CACHED",
    )

    fun qualifies(amKillReason: String): Boolean = qualifyingText.any { it.matches(amKillReason) }

    fun qualifies(reason: Int?, subReason: Int?): Boolean = when (reason) {
        REASON_LOW_MEMORY, REASON_EXCESSIVE_RESOURCE_USAGE -> true
        REASON_USER_REQUESTED -> subReason in userRequested
        REASON_OTHER -> subReason in trimmed
        else -> false
    }

    /** The AppProcessDied reason as text, such as `REASON_OTHER:SUBREASON_TOO_MANY_EMPTY`. */
    fun name(reason: Int?, subReason: Int?): String {
        val main = "REASON_" + (reason?.let { reasonNames.getOrNull(it) } ?: "UNKNOWN")
        val sub = subReason?.takeIf { it > 0 }?.let { subReasonNames.getOrNull(it) } ?: return main
        return "$main:SUBREASON_$sub"
    }
}
