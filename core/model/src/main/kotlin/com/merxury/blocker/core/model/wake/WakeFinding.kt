// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.model.wake

/** What made a stopped app process start while the app was not in use. */
enum class ReviveTrigger {
    PEER_SERVICE,
    PEER_BROADCAST,
    PEER_PROVIDER,
    PEER_ACTIVITY,
    SYSTEM_BROADCAST,
    ALARM,
    JOB,
    SYNC,
    PUSH_TRANSPORT,
    SYSTEM_BIND,
    STICKY_RESTART,
    SELF_PROCESS,
    PENDING_INTENT,
    UNKNOWN,
}

/** What kept an app process alive while it was neither visible nor cached. */
enum class HoldCause {
    FOREGROUND_SERVICE,
    BOUND_BY_SYSTEM,
    BOUND_BY_PEER,
    PROVIDER_CLIENT,
    OVERLAY_WINDOW,
    WAKELOCK,
    RUNNING_JOB,
    UNKNOWN,
}

enum class Justification { USER_INITIATED, USER_GRANTED_ROLE, PRODUCTIVE, NONE }

enum class Confidence { DIRECT, JOINED, SNAPSHOT }

enum class WakeVerdict { NORMAL, SUSPICIOUS, KEEP_ALIVE }

data class WakeFinding(
    val target: String,
    val pathKey: String,
    val trigger: ReviveTrigger?,
    val hold: HoldCause?,
    val actor: String?,
    val detail: String?,
    val actorInBackground: Boolean?,
    val afterKill: String?,
    val justification: Justification,
    val confidence: Confidence,
    val startNanos: Long,
    val durationNanos: Long,
    // pathKey of the origin revive when another revived app's process caused this start.
    val chainRoot: String? = null,
    // Hops from the origin revive. Zero for an origin and for a finding outside any chain.
    val chainDepth: Int = 0,
) {
    init {
        require((trigger == null) != (hold == null)) { "A finding is either a revive or a hold" }
        require((chainRoot == null) == (chainDepth == 0) && chainDepth >= 0) { "A chained finding has a root and a positive depth" }
        require(hold == null || chainRoot == null) { "Holds are not chained" }
    }

    companion object {
        fun revive(
            target: String,
            trigger: ReviveTrigger,
            actor: String?,
            detail: String?,
            actorInBackground: Boolean?,
            afterKill: String?,
            justification: Justification,
            confidence: Confidence,
            startNanos: Long,
        ) = WakeFinding(
            target, wakePathKey(trigger.name, actor, target, detail), trigger, null, actor, detail,
            actorInBackground, afterKill, justification, confidence, startNanos, 0,
        )

        fun hold(
            target: String,
            hold: HoldCause,
            actor: String?,
            detail: String?,
            justification: Justification,
            confidence: Confidence,
            startNanos: Long,
            durationNanos: Long,
        ) = WakeFinding(
            target, wakePathKey(HOLD_KIND_PREFIX + hold.name, actor, target, detail), null, hold, actor, detail,
            null, null, justification, confidence, startNanos, durationNanos,
        )
    }
}

/** Hold kinds are prefixed so that no hold key can equal a revive key, such as for UNKNOWN. */
const val HOLD_KIND_PREFIX = "HOLD_"

/**
 * Builds `kind|actor|target|detail`. The kind is the trigger name for a revive and
 * [HOLD_KIND_PREFIX] plus the cause name for a hold. A component detail is fully qualified so that short and long
 * spellings of one class aggregate. Actions are kept as reported. Alarm tags and job names that
 * do not identify a component are normalized by [normalizeWakeTag] and digested at capture.
 */
fun wakePathKey(kind: String, actor: String?, target: String, detail: String?): String {
    val normalized = when {
        detail == null -> ""
        '/' in detail || detail.startsWith('.') -> qualifiedWakeComponent(target, detail) ?: detail
        else -> detail
    }
    return listOf(kind, actor.orEmpty(), target, normalized).joinToString("|")
}

private val uuidToken = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
private val hexToken = Regex("""(?<![0-9A-Za-z])(?=[0-9a-fA-F]*[0-9])(?=[0-9a-fA-F]*[a-fA-F])[0-9a-fA-F]{6,}(?![0-9A-Za-z])""")
private val digitRun = Regex("""[0-9]+""")

/**
 * Replaces values that vary between runs of the same logical alarm or job: UUIDs, hexadecimal
 * tokens of at least six characters that mix digits and letters, and runs of digits.
 */
fun normalizeWakeTag(value: String): String = value
    .replace(uuidToken, "#")
    .replace(hexToken, "#")
    .replace(digitRun, "#")
