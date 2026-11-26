// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.database.wake.WakeFindingDay
import com.merxury.blocker.core.database.wake.WakeVerdictDay
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.wake.AppWakeActivity
import com.merxury.blocker.core.model.wake.AppWakeSummary
import com.merxury.blocker.core.model.wake.HoldCause
import com.merxury.blocker.core.model.wake.Justification
import com.merxury.blocker.core.model.wake.JustifiedStarts
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.OwnWakeMethod
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeDirection
import com.merxury.blocker.core.model.wake.WakeHeadline
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.WakePeerLink
import com.merxury.blocker.core.model.wake.WakeVerdict
import com.merxury.blocker.core.model.wake.qualifiedWakeComponent

private val peerTriggers = setOf(ReviveTrigger.PEER_SERVICE, ReviveTrigger.PEER_BROADCAST, ReviveTrigger.PEER_PROVIDER, ReviveTrigger.PEER_ACTIVITY)
private val peerHolds = setOf(HoldCause.BOUND_BY_PEER, HoldCause.PROVIDER_CLIENT)
private val timedTriggers = setOf(ReviveTrigger.ALARM, ReviveTrigger.JOB, ReviveTrigger.SYNC, ReviveTrigger.PENDING_INTENT)

// Triggers and holds whose detail can name a component.
private val componentTriggers = setOf(
    ReviveTrigger.PEER_SERVICE,
    ReviveTrigger.PEER_PROVIDER,
    ReviveTrigger.PEER_ACTIVITY,
    ReviveTrigger.STICKY_RESTART,
    ReviveTrigger.SELF_PROCESS,
    ReviveTrigger.SYSTEM_BIND,
    ReviveTrigger.JOB,
    ReviveTrigger.SYNC,
)
private val componentHolds = setOf(
    HoldCause.FOREGROUND_SERVICE,
    HoldCause.BOUND_BY_PEER,
    HoldCause.BOUND_BY_SYSTEM,
    HoldCause.PROVIDER_CLIENT,
    HoldCause.RUNNING_JOB,
)

// Bare dotted push details share one field with intent actions. Prefer a component only when
// the spelling looks like a class, keeping action constants such as c2dm.intent.RECEIVE as actions.
private val pushComponentClass = Regex("""(?:\.|(?:[A-Za-z_$][A-Za-z0-9_$]*\.)+)[A-Z][A-Za-z0-9_$]*""")

internal fun buildAppWakeActivity(
    packageName: String,
    fromDay: Long,
    throughDay: Long,
    findings: List<WakeFindingDay>,
    verdicts: List<WakeVerdictDay>,
): AppWakeActivity {
    val rows = findings.filter { it.day in fromDay..throughDay }
    val revives = rows.filter { it.target == packageName && it.trigger != null }
    val unjustified = rows.filter { it.isUnjustified }.map { it.toWakePath() }
    return AppWakeActivity(
        packageName = packageName,
        verdict = verdicts.asSequence()
            .filter { it.target == packageName && it.day in fromDay..throughDay }
            .map { WakeVerdict.valueOf(it.verdict) }
            .maxOrNull() ?: WakeVerdict.NORMAL,
        backgroundStarts = revives.filter { it.justification != Justification.USER_INITIATED.name }.sumOf { it.count },
        peers = buildPeerLinks(packageName, unjustified),
        own = buildOwnWakeMethods(unjustified.filter { it.target == packageName }),
        justified = countJustifiedStarts(revives),
    )
}

private fun countJustifiedStarts(revives: List<WakeFindingDay>): JustifiedStarts {
    val counts = revives.groupingBy { row ->
        if (row.trigger == ReviveTrigger.PUSH_TRANSPORT.name && !row.isUnjustified) ReviveTrigger.PUSH_TRANSPORT.name else row.justification
    }.fold(0L) { count, row -> count + row.count }
    return JustifiedStarts(
        userInitiated = counts[Justification.USER_INITIATED.name] ?: 0,
        notification = counts[Justification.PRODUCTIVE.name] ?: 0,
        push = counts[ReviveTrigger.PUSH_TRANSPORT.name] ?: 0,
        grantedRole = counts[Justification.USER_GRANTED_ROLE.name] ?: 0,
    )
}

private fun buildPeerLinks(packageName: String, paths: List<WakePath>): List<WakePeerLink> {
    val inbound = paths.filter { it.target == packageName && it.isPeerPath }.groupBy { requireNotNull(it.actor) }
    val outbound = paths.filter { it.actor == packageName && it.isPeerPath }.groupBy { it.target }
    return (inbound.keys + outbound.keys).map { peer ->
        val incoming = inbound[peer].orEmpty().sumPaths()
        val outgoing = outbound[peer].orEmpty().sumPaths()
        val direction = when {
            incoming.isEmpty() -> WakeDirection.OUTBOUND
            outgoing.isEmpty() -> WakeDirection.INBOUND
            else -> WakeDirection.MUTUAL
        }
        WakePeerLink(peer, direction, incoming, outgoing)
    }.sortedWith(
        compareBy<WakePeerLink> { it.direction != WakeDirection.MUTUAL }
            .thenBy { it.direction }
            .thenByDescending { it.inbound.sumOf(WakePath::count) + it.outbound.sumOf(WakePath::count) }
            .thenByDescending { maxOf(it.inbound.maxOfOrNull(WakePath::lastSeenMillis) ?: Long.MIN_VALUE, it.outbound.maxOfOrNull(WakePath::lastSeenMillis) ?: Long.MIN_VALUE) }
            .thenBy { it.peer },
    )
}

private data class OwnMethodKey(val kind: OwnWakeKind, val component: String? = null, val action: String? = null, val hold: HoldCause? = null)

private fun buildOwnWakeMethods(paths: List<WakePath>): List<OwnWakeMethod> = paths.mapNotNull { path ->
    path.ownMethodKey()?.let { it to path }
}.groupBy({ it.first }, { it.second }).map { (method, rows) ->
    OwnWakeMethod(method.kind, method.component, method.action, rows.sumPaths())
}.sortedWith(compareBy<OwnWakeMethod> { it.kind }.thenByDescending { it.paths.sumOf(WakePath::count) })

private fun WakePath.ownMethodKey(): OwnMethodKey? = when {
    trigger == ReviveTrigger.PEER_ACTIVITY && actor == target -> OwnMethodKey(OwnWakeKind.SELF_START, component, action)

    trigger in peerTriggers -> null

    trigger == ReviveTrigger.PUSH_TRANSPORT -> OwnMethodKey(OwnWakeKind.SYSTEM_EVENT, component, action)

    trigger == ReviveTrigger.STICKY_RESTART || (trigger != null && afterKill) ->
        OwnMethodKey(OwnWakeKind.RESTART_AFTER_CLOSE, component, action)

    trigger == ReviveTrigger.SELF_PROCESS -> OwnMethodKey(OwnWakeKind.SELF_START, component, action)

    trigger == ReviveTrigger.SYSTEM_BROADCAST -> OwnMethodKey(OwnWakeKind.SYSTEM_EVENT, action = action)

    trigger in timedTriggers -> OwnMethodKey(OwnWakeKind.TIMED, component = component)

    screenOffNanos > 0 && (
        hold == HoldCause.FOREGROUND_SERVICE || hold == HoldCause.WAKELOCK || hold == HoldCause.RUNNING_JOB ||
            hold == HoldCause.OVERLAY_WINDOW || hold == HoldCause.UNKNOWN ||
            (hold == HoldCause.BOUND_BY_PEER && actor == target)
        ) -> OwnMethodKey(OwnWakeKind.SCREEN_OFF_RUN, component, hold = hold)

    else -> null
}

internal fun buildWakeOverview(
    fromDay: Long,
    throughDay: Long,
    findings: List<WakeFindingDay>,
    verdicts: List<WakeVerdictDay>,
): List<AppWakeSummary> {
    val paths = findings.asSequence().filter { it.day in fromDay..throughDay && it.isUnjustified }.map { it.toWakePath() }.toList()
    val byTarget = paths.groupBy { it.target }
    val outbound = paths.filter { it.isPeerPath }.groupBy { it.actor }
    return verdicts.filter { it.day in fromDay..throughDay }.groupBy { it.target }.map { (packageName, days) ->
        val dailyVerdicts = days.map { WakeVerdict.valueOf(it.verdict) }
        val noneRevives = days.sumOf { it.noneRevives }
        AppWakeSummary(
            packageName = packageName,
            verdict = dailyVerdicts.max(),
            keepAliveDays = dailyVerdicts.count { it == WakeVerdict.KEEP_ALIVE },
            suspiciousDays = dailyVerdicts.count { it == WakeVerdict.SUSPICIOUS },
            headline = wakeHeadline(days, byTarget[packageName].orEmpty(), outbound[packageName].orEmpty(), noneRevives),
        ) to noneRevives
    }.sortedWith(
        compareByDescending<Pair<AppWakeSummary, Long>> { it.first.verdict }
            .thenByDescending { it.first.keepAliveDays }
            .thenByDescending { it.first.suspiciousDays }
            .thenByDescending { it.second }
            .thenBy { it.first.packageName },
    ).map { it.first }
}

private fun wakeHeadline(days: List<WakeVerdictDay>, targetPaths: List<WakePath>, outbound: List<WakePath>, noneRevives: Long): WakeHeadline {
    val afterKill = days.sumOf { it.afterKillNoneRevives }
    if (afterKill > 0) return WakeHeadline.RestartedAfterClose(afterKill)

    val otherTargets = outbound.map { it.target }.distinct().size
    if (otherTargets > 0) return WakeHeadline.StartedOthers(otherTargets)

    val inboundPeers = targetPaths.filter { it.isPeerPath }
    if (inboundPeers.isNotEmpty()) {
        return WakeHeadline.StartedByOthers(inboundPeers.map { it.actor }.distinct().size, inboundPeers.sumOf { it.count })
    }

    val screenOff = days.sumOf { it.screenOffHoldNoneNanos }
    if (screenOff > 0) return WakeHeadline.RanWithScreenOff(screenOff)

    val action = targetPaths.mapNotNull { path ->
        path.action?.takeIf { path.trigger == ReviveTrigger.SYSTEM_BROADCAST }?.let { it to path.count }
    }.groupingBy { it.first }.fold(0L) { count, entry -> count + entry.second }.entries
        .minWithOrNull(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
    if (action != null) return WakeHeadline.StartsOnSystemEvent(action.key, action.value)

    val timed = targetPaths.filter { it.trigger in timedTriggers }.sumOf { it.count }
    if (timed > 0) return WakeHeadline.TimedWakeups(timed)
    if (noneRevives > 0) return WakeHeadline.BackgroundStarts(noneRevives)

    val silentPushes = targetPaths.filter { it.trigger == ReviveTrigger.PUSH_TRANSPORT }.sumOf { it.count }
    return WakeHeadline.BackgroundStarts(silentPushes)
}

private val WakeFindingDay.isUnjustified get() = justification == Justification.NONE.name

private fun String?.isAppPackage(): Boolean = this != null && isNotBlank() && this != "android" && !all(Char::isDigit) && toLongOrNull() == null

private val WakePath.isPeerPath get() =
    (trigger in peerTriggers || hold in peerHolds) && actor != target && actor.isAppPackage() && target.isAppPackage()

/** Maps a stored detection finding to a wake path and its available block method. */
internal fun WakeFindingDay.toWakePath(): WakePath {
    val trigger = trigger?.let(ReviveTrigger::valueOf)
    val hold = hold?.let(HoldCause::valueOf)
    val broadcast = trigger == ReviveTrigger.PEER_BROADCAST || trigger == ReviveTrigger.SYSTEM_BROADCAST
    val push = trigger == ReviveTrigger.PUSH_TRANSPORT
    val scheduledService = isScheduledService(trigger, hold)
    val component = detail?.takeIf {
        when {
            broadcast -> '/' in it

            push -> '/' in it || (it.matches(pushComponentClass) && it.substringAfterLast('.').any(Char::isLowerCase))

            // Older job details and opaque job tags are digests. A bare digest is no class name.
            scheduledService -> '/' in it || '.' in it

            else -> trigger in componentTriggers || hold in componentHolds
        }
    }?.let { qualifiedWakeComponent(target, it) }
    val action = detail?.takeIf {
        component == null && it.isNotBlank() &&
            (broadcast || trigger == ReviveTrigger.SELF_PROCESS || (push && '/' !in it && it != "<redacted>"))
    }
    val componentType = when {
        broadcast || ((trigger == ReviveTrigger.SELF_PROCESS || push) && action != null) -> ComponentType.RECEIVER

        scheduledService && component != null -> ComponentType.SERVICE

        trigger == ReviveTrigger.PEER_SERVICE || trigger == ReviveTrigger.STICKY_RESTART || trigger == ReviveTrigger.SYSTEM_BIND ||
            trigger == ReviveTrigger.SELF_PROCESS || hold == HoldCause.FOREGROUND_SERVICE || hold == HoldCause.BOUND_BY_PEER ||
            hold == HoldCause.BOUND_BY_SYSTEM -> ComponentType.SERVICE

        trigger == ReviveTrigger.PEER_PROVIDER || hold == HoldCause.PROVIDER_CLIENT -> ComponentType.PROVIDER

        trigger == ReviveTrigger.PEER_ACTIVITY -> ComponentType.ACTIVITY

        else -> null
    }
    return WakePath(
        pathKey = pathKey,
        target = target,
        actor = actor,
        trigger = trigger,
        hold = hold,
        component = component,
        componentType = componentType,
        action = action,
        count = count,
        durationNanos = durationNanos,
        screenOffNanos = screenOffNanos,
        lastSeenMillis = Math.floorDiv(lastSeenNanos, 1_000_000L),
        afterKill = afterKill != null,
        blockMethod = blockMethod(trigger, hold, component, action),
    )
}

private fun isScheduledService(trigger: ReviveTrigger?, hold: HoldCause?) = trigger == ReviveTrigger.JOB || trigger == ReviveTrigger.SYNC || hold == HoldCause.RUNNING_JOB

private fun WakeFindingDay.blockMethod(trigger: ReviveTrigger?, hold: HoldCause?, component: String?, action: String?): WakeBlockMethod = when {
    trigger == ReviveTrigger.PEER_SERVICE && actor.isAppPackage() && component != null -> WakeBlockMethod.FIREWALL_RULE

    hold == HoldCause.BOUND_BY_PEER && actor.isAppPackage() && component != null -> WakeBlockMethod.FIREWALL_RULE

    (trigger == ReviveTrigger.PEER_BROADCAST || trigger == ReviveTrigger.SYSTEM_BROADCAST || trigger == ReviveTrigger.PUSH_TRANSPORT) &&
        (component != null || action != null) -> WakeBlockMethod.COMPONENT

    trigger == ReviveTrigger.SELF_PROCESS && component != null -> WakeBlockMethod.FIREWALL_RULE

    trigger == ReviveTrigger.SELF_PROCESS && action != null -> WakeBlockMethod.COMPONENT

    (trigger == ReviveTrigger.PEER_PROVIDER || trigger == ReviveTrigger.PEER_ACTIVITY || hold == HoldCause.PROVIDER_CLIENT) &&
        component != null -> WakeBlockMethod.COMPONENT

    (trigger == ReviveTrigger.STICKY_RESTART || hold == HoldCause.FOREGROUND_SERVICE) && component != null -> WakeBlockMethod.COMPONENT

    isScheduledService(trigger, hold) && component != null -> WakeBlockMethod.COMPONENT

    trigger in timedTriggers || hold == HoldCause.WAKELOCK || hold == HoldCause.RUNNING_JOB ||
        hold == HoldCause.FOREGROUND_SERVICE || hold == HoldCause.OVERLAY_WINDOW || hold == HoldCause.UNKNOWN -> WakeBlockMethod.BACKGROUND_RESTRICTION

    else -> WakeBlockMethod.NONE
}

private fun List<WakePath>.sumPaths(): List<WakePath> = groupBy { it.pathKey }.values.map { paths ->
    paths.maxBy { it.lastSeenMillis }.copy(
        count = paths.sumOf { it.count },
        durationNanos = paths.sumOf { it.durationNanos },
        screenOffNanos = paths.sumOf { it.screenOffNanos },
        afterKill = paths.any { it.afterKill },
    )
}.sortedWith(compareByDescending<WakePath> { it.count }.thenByDescending { it.lastSeenMillis }.thenBy { it.pathKey })
