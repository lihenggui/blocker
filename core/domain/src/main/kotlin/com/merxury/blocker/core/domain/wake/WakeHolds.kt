// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.database.wake.WakeHoldAssociation
import com.merxury.blocker.core.database.wake.WakeHoldPeriod
import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.model.component.isRegularAppUid
import com.merxury.blocker.core.model.component.userOfUid
import com.merxury.blocker.core.model.wake.Confidence
import com.merxury.blocker.core.model.wake.HoldCause
import com.merxury.blocker.core.model.wake.Justification
import com.merxury.blocker.core.model.wake.WakeFinding
import com.merxury.blocker.core.model.wake.qualifiedWakeComponent
import java.time.Instant
import java.time.ZoneId

internal object WakeHolds {
    private const val PERSISTENT_FGS_SCREEN_OFF_MILLIS = 30 * 60 * 1000L

    // ServiceInfo.FOREGROUND_SERVICE_TYPE_* bits and their manifest names.
    private val fgsTypes = listOf(
        0 to "dataSync", 1 to "mediaPlayback", 2 to "phoneCall", 3 to "location", 4 to "connectedDevice",
        5 to "mediaProjection", 6 to "camera", 7 to "microphone", 8 to "health", 9 to "remoteMessaging",
        10 to "systemExempted", 11 to "shortService", 12 to "fileManagement", 13 to "mediaProcessing", 30 to "specialUse",
    )

    // Sound, a call, or the system's capture and recording indicators show these without a notification.
    private val perceptibleFgsTypes = setOf("mediaPlayback", "phoneCall", "mediaProjection", "camera", "microphone")

    // Android reserves systemExempted for roles and privileges such as VPN and device administration.
    private val legitimateOngoingFgsTypes = perceptibleFgsTypes + setOf("dataSync", "location", "connectedDevice", "health", "systemExempted")

    fun fgsTypeNames(mask: Long?): String? = mask?.takeIf { it > 0 }?.let { bits ->
        fgsTypes.filter { (bit, _) -> bits and (1L shl bit) != 0L }.joinToString(",") { it.second }.ifEmpty { null }
    }

    /** A cause candidate before the procstats cap. A [justification] set here overrides the one by cause. */
    private data class Candidate(
        val cause: HoldCause,
        val actor: String?,
        val detail: String?,
        val confidence: Confidence,
        val millis: Long,
        val justification: Justification? = null,
    )

    private data class Interval(val uid: Int, val key: String?, val actor: String?, val fromWall: Long, val toWall: Long)

    /**
     * Pairs state changes per boot and key. A close without an open uses [openedFor] when the record
     * reports its duration. An open without a close lasts until the newest evidence of that boot.
     */
    private fun WakeEvidenceIndex.intervals(
        kind: String,
        uid: (WakeRecord) -> Int?,
        key: (WakeRecord) -> String?,
        opens: (WakeRecord) -> Boolean,
        closes: (WakeRecord) -> Boolean,
        actor: (WakeRecord) -> String? = { null },
        openedFor: (WakeRecord) -> Long? = { null },
    ): List<Interval> {
        val result = mutableListOf<Interval>()
        for ((group, list) in kind(kind).mapNotNull { record -> uid(record)?.let { Triple(record.boot, it, key(record)) to record } }.groupBy({ it.first }, { it.second })) {
            var open: WakeRecord? = null
            for (record in list.sortedBy { it.elapsedNanos }) {
                when {
                    opens(record) -> if (open == null) open = record

                    closes(record) -> {
                        val from = open?.wallMillis ?: openedFor(record)?.let { record.wallMillis - it }
                        if (from != null) result += Interval(group.second, group.third, actor(open ?: record), from, record.wallMillis)
                        open = null
                    }
                }
            }
            open?.let { result += Interval(group.second, group.third, actor(it), it.wallMillis, newestWall.getValue(group.first)) }
        }
        return result
    }

    /** The start and length in milliseconds of each local day's part of [period]. */
    private fun days(period: WakeHoldPeriod, zone: ZoneId): List<Pair<Long, Long>> {
        val result = mutableListOf<Pair<Long, Long>>()
        var from = period.startMillis
        while (from < period.endMillis) {
            val next = Instant.ofEpochMilli(from).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = minOf(next, period.endMillis)
            result += from to to - from
            from = to
        }
        return result.ifEmpty { listOf(period.startMillis to 1L) }
    }

    fun detect(index: WakeEvidenceIndex, zone: ZoneId = ZoneId.systemDefault()): List<Detected> = with(index) {
        val fgs = intervals(
            "ForegroundServiceStateChanged",
            { it.targetUid },
            { it.component },
            opens = { it.number("state") == 1L },
            closes = { it.number("state") == 2L },
            actor = { fgsTypeNames(it.number("fgs_types")) },
            openedFor = { it.number("fgs_duration_ms") },
        ).groupBy { it.uid }
        val wakelocks = intervals(
            "LongPartialWakelockStateChanged",
            { it.chainUid() },
            { it.attr("tag") },
            opens = { it.number("state") == 1L },
            closes = { it.number("state") == 0L },
        ).groupBy { it.uid }
        val jobs = intervals(
            "ScheduledJobStateChanged",
            { it.chainUid() },
            { listOf(it.attr("job_id"), it.attr("namespace_hash"), it.attr("job_name")).joinToString("/") },
            opens = { it.number("state") == 1L },
            closes = { it.number("state") == 0L },
            actor = { it.attr("job_name") },
        ).groupBy { it.uid }
        val associations = evidence.associations.groupBy { Triple(it.periodId, it.target, it.uid) }
        val periods = evidence.periods.associateBy(WakeHoldPeriod::periodId)
        // The process each component runs in, from the service and provider snapshots. A component's
        // process is fixed by its manifest, so a snapshot from any time maps it.
        val hosts = kind("HeldConnection").mapNotNull { record ->
            val component = qualifiedWakeComponent(record.targetPackage, record.component) ?: return@mapNotNull null
            val process = record.attr("host_process") ?: return@mapNotNull null
            Triple(record.targetPackage, component, process)
        }.associate { (pkg, component, process) -> (pkg to component) to process }

        val result = mutableListOf<Detected>()
        for ((key, processes) in evidence.processes.groupBy { Triple(it.periodId, it.target, it.uid) }) {
            val (periodId, target, uid) = key
            // procstats files isolated processes under their host uid. Other non-regular uids are not the app's own time.
            if (!isRegularAppUid(uid)) continue
            val period = periods[periodId] ?: continue
            val hidden = notificationsOff(target, uid, period.startMillis, period.endMillis)
            val off = processes.sumOf { it.screenOffMillis }
            val total = off + processes.sumOf { it.screenOnMillis }
            if (total <= 0) continue
            fun overlap(interval: Interval) = (minOf(interval.toWall, period.endMillis) - maxOf(interval.fromWall, period.startMillis)).coerceAtLeast(0)
            val services = fgs[uid].orEmpty().groupBy { it.actor to it.key }.map { (cause, list) ->
                Candidate(HoldCause.FOREGROUND_SERVICE, cause.first, cause.second, Confidence.JOINED, list.sumOf(::overlap))
            }.filter { it.millis > 0 }
            // procstats reports the foreground service state itself (Android 12 and later), also for a
            // service that started before any state change was recorded. statsd only names its types.
            val fgsOff = processes.sumOf { it.fgsScreenOffMillis }
            val fgsTotal = (fgsOff + processes.sumOf { it.fgsScreenOnMillis }).coerceAtMost(total)
            val foreground = if (fgsTotal > 0) {
                val named = services.sumOf { it.millis }
                when {
                    named == 0L -> listOf(Candidate(HoldCause.FOREGROUND_SERVICE, null, null, Confidence.SNAPSHOT, fgsTotal))
                    named >= fgsTotal -> services.map { it.copy(millis = it.millis * fgsTotal / named) }
                    else -> services + Candidate(HoldCause.FOREGROUND_SERVICE, null, null, Confidence.SNAPSHOT, fgsTotal - named)
                }.filter { it.millis > 0 }
            } else {
                emptyList()
            }
            val candidates = mutableListOf<Candidate>()
            if (fgsTotal == 0L) candidates += services
            jobs[uid].orEmpty().groupBy { it.actor }.forEach { (job, list) ->
                candidates += Candidate(HoldCause.RUNNING_JOB, null, job, Confidence.JOINED, list.sumOf(::overlap))
            }
            wakelocks[uid].orEmpty().groupBy { it.key }.forEach { (tag, list) ->
                candidates += Candidate(HoldCause.WAKELOCK, null, tag, Confidence.JOINED, list.sumOf(::overlap))
            }
            // The system keeps its bindings (input method, listeners, roles) while its own process is
            // persistent, so procstats records little "active" time for them. Count how long they were
            // bound. An app client only holds the target while it is itself above cached. procstats
            // counts an association as active only while each process state computation touches it,
            // which partial updates do not: on the emulator messaging's ":rcs" process was a bound
            // foreground service all period, bound to SharedStorageProvider for 882 s but "active" for
            // 483 s. So the app's own processes count their bound time too.
            fun sameApp(association: WakeHoldAssociation) = association.sourceUid == uid
            fun held(association: WakeHoldAssociation) = if (isApp(association.sourceUid) && !sameApp(association)) association.activeMillis else association.boundMillis
            fun component(association: WakeHoldAssociation) = qualifiedWakeComponent(target, association.component) ?: association.component
            // Processes of the app that a system binding with a user grant kept during this period.
            val granted = associations[key].orEmpty().filter { !isApp(it.sourceUid) && it.boundMillis > 0 }.map(::component)
                .filter { it !in providerComponents && grantedSpecialAccess(target, it, userOfUid(uid)) }.mapNotNull { hosts[target to it] }.toSet()
            // Outside causes come first; the app's own processes explain only what they leave.
            for (association in associations[key].orEmpty().sortedWith(compareBy(::sameApp).thenByDescending(::held))) {
                val component = component(association)
                val provider = component in providerComponents
                val candidate = when {
                    !isApp(association.sourceUid) ->
                        Candidate(if (provider) HoldCause.PROVIDER_CLIENT else HoldCause.BOUND_BY_SYSTEM, "android", component, Confidence.SNAPSHOT, held(association))

                    // An app's processes holding each other up. It is what the user granted when the
                    // holding process is itself kept by a granted system binding.
                    sameApp(association) -> Candidate(
                        HoldCause.BOUND_BY_PEER,
                        target,
                        component,
                        Confidence.SNAPSHOT,
                        held(association),
                        if (association.sourceProcess in granted) Justification.USER_GRANTED_ROLE else Justification.NONE,
                    )

                    else -> {
                        // The same app in another user is not an outside cause. A process name is never taken for a package.
                        if (target in evidence.packages[association.sourceUid].orEmpty()) continue
                        val source = packageOf(association.sourceUid, association.sourceProcess) ?: association.sourceUid.toString()
                        Candidate(if (provider) HoldCause.PROVIDER_CLIENT else HoldCause.BOUND_BY_PEER, source, component, Confidence.SNAPSHOT, held(association))
                    }
                }
                candidates += candidate
            }
            // Never attribute more than procstats reports. Earlier causes take precedence.
            val foregroundTotal = foreground.sumOf { it.millis }
            var remaining = total - foregroundTotal
            val attributed = mutableListOf<Candidate>()
            for (candidate in candidates) {
                val share = minOf(candidate.millis, remaining)
                if (share <= 0) continue
                remaining -= share
                attributed += candidate.copy(millis = share)
            }
            if (remaining > 0) {
                val cause = if (hasOverlayWindow(target, uid, period.startMillis, period.endMillis)) HoldCause.OVERLAY_WINDOW else HoldCause.UNKNOWN
                attributed += Candidate(cause, null, null, Confidence.SNAPSHOT, remaining)
            }
            // Foreground service time keeps its own screen split; the other causes share the rest.
            val otherOff = (off - fgsOff.coerceAtMost(foregroundTotal)).coerceAtLeast(0)
            val otherTotal = total - foregroundTotal
            fun screenOffMillis(candidate: Candidate) = when {
                candidate.cause == HoldCause.FOREGROUND_SERVICE && foregroundTotal > 0 -> candidate.millis * fgsOff.coerceAtMost(foregroundTotal) / foregroundTotal
                otherTotal > 0 -> candidate.millis * otherOff / otherTotal
                else -> 0L
            }
            for (candidate in foreground + attributed) {
                val candidateScreenOffMillis = screenOffMillis(candidate)
                val justification = candidate.justification ?: when {
                    // A foreground service shows an ongoing notification, unless the app may not notify:
                    // from Android 13 the service still runs then. The user notices some types anyway.
                    candidate.cause == HoldCause.FOREGROUND_SERVICE -> {
                        val types = candidate.actor.orEmpty().split(',').filter { it.isNotBlank() }
                        val unjustified = if (hidden) {
                            val perceptible = types.filter { it in perceptibleFgsTypes }
                            perceptible.isEmpty() ||
                                (perceptible == listOf("mediaPlayback") && silentAudio(uid, period.startMillis, period.endMillis))
                        } else {
                            candidateScreenOffMillis >= PERSISTENT_FGS_SCREEN_OFF_MILLIS &&
                                types.none { it in legitimateOngoingFgsTypes } && !grantedSpecialAccess(target, candidate.detail, userOfUid(uid))
                        }
                        if (unjustified) Justification.NONE else Justification.PRODUCTIVE
                    }

                    candidate.cause == HoldCause.BOUND_BY_SYSTEM && grantedSpecialAccess(target, candidate.detail, userOfUid(uid)) -> Justification.USER_GRANTED_ROLE

                    else -> Justification.NONE
                }
                // procstats splits process time by screen state but not by cause, except for the foreground service
                // state, and not by time of day: a period that crosses midnight is shared by its overlap with each day.
                val days = days(period, zone)
                val length = days.sumOf { it.second }
                var durationLeft = candidate.millis * 1_000_000
                var screenOffLeft = candidateScreenOffMillis * 1_000_000
                days.forEachIndexed { i, (from, millis) ->
                    val last = i == days.lastIndex
                    // Shares are taken in milliseconds: nanoseconds times a period length overflows.
                    val duration = if (last) durationLeft else candidate.millis * millis / length * 1_000_000
                    val screenOff = if (last) screenOffLeft else candidateScreenOffMillis * millis / length * 1_000_000
                    durationLeft -= duration
                    screenOffLeft -= screenOff
                    if (duration <= 0) return@forEachIndexed
                    result += Detected(
                        WakeFinding.hold(target, candidate.cause, candidate.actor, candidate.detail, justification, candidate.confidence, from * 1_000_000, duration),
                        screenOff,
                    )
                }
            }
        }
        // One finding per package, cause, actor and detail per period, even across users.
        return result.groupBy { it.finding.copy(durationNanos = 0) }.map { (finding, list) ->
            Detected(finding.copy(durationNanos = list.sumOf { it.finding.durationNanos }), list.sumOf { it.screenOffNanos })
        }
    }
}
