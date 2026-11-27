// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.model.wake

/** A procstats period. Its rows always hold the latest read of that period, never a sum of reads. */
data class WakeHoldPeriod(val periodId: String, val startMillis: Long, val endMillis: Long, val checkedAt: Long)

/** Process uptime in hold states within one period, split by screen state, and the part spent as a foreground service. */
data class WakeHoldProcess(
    val periodId: String,
    val target: String,
    val uid: Int,
    val process: String,
    val screenOffMillis: Long,
    val screenOnMillis: Long,
    val fgsScreenOffMillis: Long = 0,
    val fgsScreenOnMillis: Long = 0,
)

/**
 * A binding or provider connection of [target] from a client of another uid within one period: how
 * long it existed, and how long its client was above cached.
 */
data class WakeHoldAssociation(
    val periodId: String,
    val target: String,
    val uid: Int,
    val component: String,
    val sourceProcess: String,
    val sourceUid: Int,
    val boundMillis: Long,
    val activeMillis: Long,
)

/** The latest power exemption snapshot. Null means the source was unavailable. */
data class WakeExemption(
    val target: String,
    val idleWhitelist: String?,
    val standbyBucket: Int?,
    val backgroundRestricted: Boolean?,
    val checkedAt: Long,
)

/** Findings of one local day aggregated per path and classification. */
data class WakeFindingDay(
    val id: String,
    val day: Long,
    val target: String,
    val pathKey: String,
    val trigger: String?,
    val hold: String?,
    val actor: String?,
    val detail: String?,
    val actorInBackground: Boolean?,
    val afterKill: String?,
    val justification: String,
    val confidence: String,
    val chainRoot: String?,
    val chainDepth: Int,
    val count: Long,
    val durationNanos: Long,
    val screenOffNanos: Long,
    val lastSeenNanos: Long,
    // Cold starts further down the chains that these origin revives began, direct and transitive.
    val downstreamStarts: Long,
)

/** The verdict of one package for one local day, with the counters that produced it. */
data class WakeVerdictDay(
    val day: Long,
    val target: String,
    val verdict: String,
    val revives: Long,
    val noneRevives: Long,
    val afterKillRevives: Long,
    val afterKillNoneRevives: Long,
    val peerBackgroundNoneRevives: Long,
    val screenOffHoldNoneNanos: Long,
    val wakesOthers: Long,
    // Cold starts down the chains whose origin revive this package's request caused.
    val downstreamStarts: Long,
)
