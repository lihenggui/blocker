// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.wake.WakeDetectionEvidence
import com.merxury.blocker.core.database.wake.WakeFindingDay
import com.merxury.blocker.core.database.wake.WakeVerdictDay
import com.merxury.blocker.core.model.wake.HoldCause
import com.merxury.blocker.core.model.wake.Justification
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeFinding
import com.merxury.blocker.core.model.wake.WakeVerdict
import com.merxury.blocker.core.model.wake.normalizeWakeTag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Daily verdict thresholds. Counts are findings of one package on one local day. */
object WakeVerdictThresholds {
    const val KEEP_ALIVE_AFTER_KILL_NONE_REVIVES = 3
    const val KEEP_ALIVE_PEER_BACKGROUND_NONE_REVIVES = 5
    const val KEEP_ALIVE_SCREEN_OFF_HOLD_NONE_NANOS = 2 * 60 * 60 * 1_000_000_000L
    const val SUSPICIOUS_NONE_REVIVES = 10
    const val SUSPICIOUS_SCREEN_OFF_HOLD_NONE_NANOS = 30 * 60 * 1_000_000_000L
    const val SUSPICIOUS_AFTER_KILL_NONE_REVIVES = 1
}

/** Local days whose rows a detection run rewrites ([from]..[through]); rows before [firstKeptDay] expire. */
internal data class WakeDetectionWindow(val from: Long, val through: Long, val firstKeptDay: Long)

internal object WakeDetection {
    private const val SYSTEM_ACTOR = "android"
    private val platformDeliveries = setOf(ReviveTrigger.SYSTEM_BROADCAST, ReviveTrigger.PUSH_TRANSPORT)
    private val peerTriggers = setOf(ReviveTrigger.PEER_SERVICE, ReviveTrigger.PEER_BROADCAST, ReviveTrigger.PEER_PROVIDER, ReviveTrigger.PEER_ACTIVITY)

    fun detect(evidence: WakeDetectionEvidence, zone: ZoneId = ZoneId.systemDefault()): List<Detected> {
        val index = WakeEvidenceIndex(evidence)
        return WakeRevives.detect(index) + WakeHolds.detect(index, zone)
    }

    fun findings(evidence: WakeDetectionEvidence): List<WakeFinding> = detect(evidence).map { it.finding }

    fun day(wallNanos: Long, zone: ZoneId): Long = Instant.ofEpochMilli(Math.floorDiv(wallNanos, 1_000_000)).atZone(zone).toLocalDate().toEpochDay()

    fun dayStartMillis(day: Long, zone: ZoneId): Long = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * The day containing the retention cutoff is incomplete and is dropped. A busy device trims history
     * by size: then the days before the first complete one keep their earlier, fuller results. The current
     * day is always recomputed from what remains.
     */
    fun window(now: Long, cutoff: Long, oldestRecord: Long?, oldestImported: Long?, zone: ZoneId): WakeDetectionWindow {
        val firstKeptDay = day(cutoff * 1_000_000, zone) + 1
        val through = day(now * 1_000_000, zone)
        val trimmed = oldestRecord != null && oldestImported != null && oldestImported < oldestRecord
        val complete = if (trimmed) maxOf(firstKeptDay, day(oldestRecord * 1_000_000, zone) + 1) else firstKeptDay
        return WakeDetectionWindow(minOf(complete, through), through, firstKeptDay)
    }

    private val WakeFinding.peerInBackground get() = trigger in peerTriggers && actorInBackground == true

    /** Rows also differ by classification so that no column is averaged away. Kill reasons carry pids, so they are normalized. */
    private fun rowKey(finding: WakeFinding, zone: ZoneId): List<Any?> = listOf(
        day(finding.startNanos, zone),
        finding.pathKey,
        finding.trigger?.name,
        finding.hold?.name,
        finding.justification.name,
        finding.confidence.name,
        finding.actorInBackground,
        finding.afterKill?.let(::normalizeWakeTag),
        finding.chainRoot,
        finding.chainDepth,
    )

    /** Aggregates per day and path. An origin's row also counts every start further down its chains. */
    fun daily(detected: List<Detected>, zone: ZoneId): List<WakeFindingDay> {
        val downstream = detected.mapNotNull { it.root }.groupingBy { rowKey(it, zone) }.eachCount()
        return detected.groupBy { rowKey(it.finding, zone) }.map { (key, list) ->
            val finding = list.first().finding
            WakeFindingDay(
                key.joinToString("|") { it?.toString().orEmpty() },
                key[0] as Long,
                finding.target,
                finding.pathKey,
                finding.trigger?.name,
                finding.hold?.name,
                finding.actor,
                finding.detail,
                finding.actorInBackground,
                key[7] as String?,
                finding.justification.name,
                finding.confidence.name,
                finding.chainRoot,
                finding.chainDepth,
                list.size.toLong(),
                list.sumOf { it.finding.durationNanos },
                list.sumOf { it.screenOffNanos },
                list.maxOf { it.finding.startNanos },
                downstream[key]?.toLong() ?: 0,
            )
        }
    }

    /**
     * A chained revive is a consequence of its origin. It does not count as an unjustified or
     * background-peer revive of its own package; the package whose request began the chain is
     * credited with it instead, on the day of the origin revive. An UNKNOWN revive is recorded and
     * counted in `revives`, but only the after-kill counters can make it count toward a verdict.
     * `afterKillRevives` counts every revive after a qualifying kill; `afterKillNoneRevives`, which
     * decides verdicts, leaves out chained ones and system broadcast or push deliveries. Holds bound by
     * the system are recorded but never count toward `screenOffHoldNoneNanos`.
     */
    fun verdicts(detected: List<Detected>, zone: ZoneId): List<WakeVerdictDay> {
        val caused = detected.mapNotNull { it.root }.filter { it.actor != null && it.actor != SYSTEM_ACTOR }
            .groupingBy { day(it.startNanos, zone) to it.actor!! }.eachCount()
        val byDay = detected.groupBy { day(it.finding.startNanos, zone) }
        return (byDay.keys + caused.keys.map { it.first }).distinct().flatMap { day ->
            val list = byDay[day].orEmpty()
            val wakers = list.filter { it.finding.peerInBackground }.groupBy({ it.finding.actor }, { it.finding.target })
                .filterKeys { it != null }.mapValues { it.value.toSet().size.toLong() }
            val targets = list.groupBy { it.finding.target }
            val origins = caused.keys.filter { it.first == day }.map { it.second }
            (targets.keys + wakers.keys.filterNotNull() + origins).distinct().map { pkg ->
                val own = targets[pkg].orEmpty()
                val revives = own.filter { it.finding.trigger != null }.map { it.finding }
                val none = revives.filter { it.justification == Justification.NONE }
                // A verdict rests on an identified cause. An unexplained revive counts only after a qualifying kill.
                val unchainedNone = none.filter { it.chainRoot == null && it.trigger != ReviveTrigger.UNKNOWN }
                val counters = WakeVerdictDay(
                    day,
                    pkg,
                    "",
                    revives.size.toLong(),
                    unchainedNone.size.toLong(),
                    revives.count { it.afterKill != null }.toLong(),
                    // A chained revive's kill and restart are the origin's doing, not its own. A protected system
                    // broadcast or a push delivered to a trimmed app is ordinary platform behaviour.
                    none.count { it.afterKill != null && it.chainRoot == null && it.trigger !in platformDeliveries }.toLong(),
                    unchainedNone.count { it.peerInBackground }.toLong(),
                    // The system binds a service only for a platform role or feature; that is no keep-alive trick.
                    own.filter { it.finding.hold != null && it.finding.hold != HoldCause.BOUND_BY_SYSTEM && it.finding.justification == Justification.NONE }
                        .sumOf { it.screenOffNanos },
                    wakers[pkg] ?: 0,
                    caused[day to pkg]?.toLong() ?: 0,
                )
                counters.copy(verdict = verdict(counters).name)
            }
        }
    }

    fun verdict(day: WakeVerdictDay): WakeVerdict = with(WakeVerdictThresholds) {
        when {
            day.afterKillNoneRevives >= KEEP_ALIVE_AFTER_KILL_NONE_REVIVES ||
                day.peerBackgroundNoneRevives >= KEEP_ALIVE_PEER_BACKGROUND_NONE_REVIVES ||
                day.screenOffHoldNoneNanos >= KEEP_ALIVE_SCREEN_OFF_HOLD_NONE_NANOS -> WakeVerdict.KEEP_ALIVE

            day.noneRevives >= SUSPICIOUS_NONE_REVIVES ||
                day.screenOffHoldNoneNanos >= SUSPICIOUS_SCREEN_OFF_HOLD_NONE_NANOS ||
                day.afterKillNoneRevives >= SUSPICIOUS_AFTER_KILL_NONE_REVIVES -> WakeVerdict.SUSPICIOUS

            else -> WakeVerdict.NORMAL
        }
    }
}
