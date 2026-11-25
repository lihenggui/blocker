// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.database.wake.WakeContinuation
import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.model.component.userOfUid
import com.merxury.blocker.core.model.wake.Confidence
import com.merxury.blocker.core.model.wake.Justification
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import com.merxury.blocker.core.model.wake.WakeFinding
import com.merxury.blocker.core.model.wake.qualifiedWakeComponent
import com.merxury.blocker.core.privileged.wake.WAKE_START_INFO
import com.merxury.blocker.core.privileged.wake.WAKE_START_INFO_CLOCK
import com.merxury.blocker.core.privileged.wake.WAKE_START_INFO_SLEEP

/**
 * A finding, the part of its duration that fell while the screen was off, and for a chained revive
 * the origin revive it is credited to.
 */
internal data class Detected(val finding: WakeFinding, val screenOffNanos: Long = 0, val root: WakeFinding? = null)

/** One cold start, merged from every source that reported it. */
internal class ColdStart(val boot: String, val uid: Int, var process: String?, var elapsed: Long) {
    var info: WakeRecord? = null
    var stats: WakeRecord? = null
    var event: WakeRecord? = null

    // `package/fully.qualified.Class` of the hosted component when a source names it.
    var component: String? = null

    // The event log and statsd time a start exactly; start-info only within its clock's uncertainty.
    val reference get() = event ?: stats ?: info!!
}

/** `{pkg/.Cls}`, `ComponentInfo{pkg/Cls}` and `pkg/Cls` as `pkg/pkg.Cls`, or null if it names no component. */
internal fun componentKey(raw: String?): String? {
    val name = raw?.removePrefix("ComponentInfo{")?.removePrefix("{")?.removeSuffix("}") ?: return null
    val pkg = name.substringBefore('/', "").takeIf { it.isNotEmpty() } ?: return null
    return qualifiedWakeComponent(pkg, name)?.let { "$pkg/$it" }
}

internal object WakeRevives {
    const val MERGE_WINDOW = 3_000_000_000L
    const val JOIN_WINDOW = 5_000_000_000L
    const val KILL_WINDOW = 60_000_000_000L
    const val PRODUCTIVE_WINDOW = 10_000_000_000L

    // Continuations are ordered in time, so a longer walk means corrupt links rather than a real chain.
    const val MAX_CHAIN_DEPTH = 8

    // ApplicationStartInfo reasons that name the trigger without a caller.
    private val directReasons = mapOf(
        "ALARM" to ReviveTrigger.ALARM,
        "JOB" to ReviveTrigger.JOB,
        "PUSH" to ReviveTrigger.PUSH_TRANSPORT,
        "BOOT COMPLETE" to ReviveTrigger.SYSTEM_BROADCAST,
    )

    // A process started to host an activity is showing UI. HostingRecord types and their
    // ProcessStartTime hosting_type_id: activity 1, next-activity 8, next-top-activity 9, top-activity 13.
    private val activityHostingTypes = setOf("activity", "next-activity", "next-top-activity", "top-activity")
    val ACTIVITY_HOSTING_TYPE_IDS = setOf(1L, 8L, 9L, 13L)
    private val activityReasons = setOf("START ACTIVITY", "LAUNCHER", "LAUNCHER RECENTS")

    // HOSTING_TYPE_EMPTY: app zygote and WebView zygote starts, which are always isolated processes.
    private const val ZYGOTE_HOSTING_TYPE_ID = 14L
    private const val GMS = "com.google.android.gms"

    // ProcessStartTime.TriggerType: ALARM, PUSH_MESSAGE, PUSH_MESSAGE_OVER_QUOTA, JOB.
    private val triggerTypes = mapOf(1L to ReviveTrigger.ALARM, 2L to ReviveTrigger.PUSH_TRANSPORT, 3L to ReviveTrigger.PUSH_TRANSPORT, 4L to ReviveTrigger.JOB)

    private val peerTriggers = mapOf(
        "ServiceRequestEventReported" to ReviveTrigger.PEER_SERVICE,
        "BroadcastDeliveryEventReported" to ReviveTrigger.PEER_BROADCAST,
        "ProviderAcquisitionEventReported" to ReviveTrigger.PEER_PROVIDER,
        "AppStartOccurred" to ReviveTrigger.PEER_ACTIVITY,
    )
    private val requesterState = mapOf(
        "ServiceRequestEventReported" to "requester_process_state",
        "BroadcastDeliveryEventReported" to "sender_proc_state",
        "ProviderAcquisitionEventReported" to "client_process_state",
    )

    // NotificationRecordLogger.NotificationReportedEvent: POSTED and UPDATED. ADJUSTED comes from the assistant.
    private val appNotificationEvents = setOf(162L, 163L)

    private data class Trigger(
        val trigger: ReviveTrigger,
        val confidence: Confidence,
        val actor: String? = null,
        val detail: String? = null,
        val actorInBackground: Boolean? = null,
        // The joined request this classification came from. Only such revives take part in chains.
        val requestId: String? = null,
    )

    fun coldStarts(index: WakeEvidenceIndex): List<ColdStart> = with(index) {
        val starts = mutableMapOf<Pair<String, Int>, MutableList<ColdStart>>()
        val events = kind("am_proc_start").filter { it.source == SYSTEM_WAKE_EVENTS }
        // The event log names each start with its exact time, pid and process.
        for (record in events) {
            val uid = record.targetUid?.takeIf(::isRegularApp) ?: continue
            starts.getOrPut(record.boot to uid) { mutableListOf() } += ColdStart(record.boot, uid, record.processName, record.elapsedNanos).apply {
                event = record
                component = componentKey(record.component)
            }
        }
        // ProcessStartTime logs isolated processes under the host uid; the event log under their own.
        val isolatedPids = events.filter { it.targetUid != null && !isRegularApp(it.targetUid) && it.pid != null }
            .groupBy({ it.boot to it.pid }, { it.elapsedNanos })
        for (record in kind("ProcessStartTime").filter { it.source == "statsd" && it.startType != 1 && it.startType != 2 }) {
            val uid = record.targetUid?.takeIf(::isRegularApp) ?: continue
            if (record.number("hosting_type_id") == ZYGOTE_HOSTING_TYPE_ID) continue
            val actual = record.number("process_start_time_millis")?.takeIf { it >= 0 }?.times(1_000_000) ?: record.elapsedNanos
            fun near(time: Long) = kotlin.math.abs(time - actual) <= MERGE_WINDOW
            if (isolatedPids[record.boot to record.pid].orEmpty().any(::near)) continue
            val group = starts.getOrPut(record.boot to uid) { mutableListOf() }
            // Both name the pid. Its process_name field carries the package name, so the pid joins them.
            val same = group.firstOrNull { it.stats == null && record.pid != null && it.event?.pid == record.pid && near(it.elapsed) }
            val start = same ?: ColdStart(record.boot, uid, null, actual).also(group::add)
            start.stats = record
            if (start.component == null) start.component = componentKey(record.component)
        }
        // start-info describes a start the event log or statsd observed. Its recorded time is the earliest
        // the start can have happened; a monotonic record may be later by the deep sleep it could not see,
        // so it fits the starts within that span. It also writes one record per component started while
        // the process comes up; the earliest describes it.
        for (record in kind(WAKE_START_INFO).filter { it.startType == 3 }.sortedBy { it.elapsedNanos }) {
            val uid = record.targetUid?.takeIf(::isRegularApp) ?: continue
            val sleep = if (record.attr(WAKE_START_INFO_CLOCK) == "elapsed") 0 else record.number(WAKE_START_INFO_SLEEP)
            val span = (record.elapsedNanos - MERGE_WINDOW)..(record.elapsedNanos + (sleep ?: 0) + MERGE_WINDOW)
            val candidates = starts[record.boot to uid].orEmpty().filter { start ->
                start.elapsed in span && (start.process == null || start.process == record.processName)
            }
            if (candidates.isEmpty()) {
                // Unobserved by the event log and statsd, for example during boot. Its time stands on its own
                // only when the clock uncertainty is within the merge window.
                if (sleep != null && sleep <= MERGE_WINDOW && starts[record.boot to uid].orEmpty().none { it.info?.processName == record.processName && kotlin.math.abs(it.elapsed - record.elapsedNanos) <= MERGE_WINDOW }) {
                    starts.getOrPut(record.boot to uid) { mutableListOf() } += ColdStart(record.boot, uid, record.processName, record.elapsedNanos).apply {
                        info = record
                        component = componentKey(record.component)
                    }
                }
                continue
            }
            val start = candidates.singleOrNull() ?: continue
            if (start.info != null) continue
            start.info = record
            if (start.process == null) start.process = record.processName
            if (start.component == null) start.component = componentKey(record.component)
        }
        starts.values.flatten()
    }

    /**
     * The package whose component this start hosts. A uid shared by several packages is resolved only
     * by a component or the joined request; a process name is never taken for a package.
     */
    private fun WakeEvidenceIndex.target(start: ColdStart, request: WakeRecord?): String? {
        val known = evidence.packages[start.uid]
        val named = listOfNotNull(start.component?.substringBefore('/'), start.stats?.targetPackage, request?.targetPackage)
        return when {
            known != null && known.size == 1 -> known.single()
            known != null && known.size > 1 -> named.firstOrNull { it in known }
            else -> named.firstOrNull() ?: start.info?.targetPackage ?: start.event?.targetPackage
        }
    }

    fun detect(index: WakeEvidenceIndex): List<Detected> = chain(classify(index), index.evidence.continuations)

    /**
     * WakeAttribution.continuations links a request to the earlier request whose cold-started process
     * issued it. Walking those links back from a revive's own request reaches the revives upstream;
     * the earliest one found is the origin. A cycle yields no chain, and a walk stops after
     * [MAX_CHAIN_DEPTH] hops.
     */
    private fun chain(revives: List<Pair<Detected, String?>>, continuations: List<WakeContinuation>): List<Detected> {
        val byRequest = revives.mapNotNull { (detected, request) -> request?.let { it to detected.finding } }.toMap()
        val incoming = continuations.associate { it.outgoing to it.incoming }
        return revives.map { (detected, request) ->
            if (request == null) return@map detected
            val visited = mutableSetOf(request)
            var current: String = request
            var hops = 0
            var root: WakeFinding? = null
            var rootDepth = 0
            while (hops < MAX_CHAIN_DEPTH) {
                val prior = incoming[current] ?: break
                if (!visited.add(prior)) return@map detected
                hops++
                current = prior
                byRequest[prior]?.let {
                    root = it
                    rootDepth = hops
                }
            }
            root?.let { detected.copy(finding = detected.finding.copy(chainRoot = it.pathKey, chainDepth = rootDepth), root = it) } ?: detected
        }
    }

    private fun classify(index: WakeEvidenceIndex): List<Pair<Detected, String?>> = with(index) {
        val requestsByStart = evidence.relations.groupBy({ it.processId }, { byId[it.requestId] }).mapValues { it.value.filterNotNull() }
        val restarts = kind("am_schedule_service_restart").filter { it.source == SYSTEM_WAKE_EVENTS }.groupBy { it.boot }
        val jobs = byUid("ScheduledJobStateChanged") { record -> record.chainUid().takeIf { record.number("state") == 1L } }
        val syncs = byUid("SyncStateChanged") { record -> record.chainUid().takeIf { record.number("state") == 1L } }
        val wakeups = byUid("WakeupAlarmOccurred") { it.chainUid() }
        val batches = kind("AlarmBatchDelivered").groupBy { it.boot }
        val notifications = byUid("NotificationReported") { record -> record.targetUid.takeIf { record.number("event_id") in appNotificationEvents } }
        // The app showed UI: a reported activity launch, or any start record of the uid hosting an activity.
        val shown = (
            kind("AppStartOccurred") +
                kind(WAKE_START_INFO).filter { it.attr("reason") in activityReasons } +
                kind("am_proc_start").filter { it.source == SYSTEM_WAKE_EVENTS && it.attr("hosting_type") in activityHostingTypes } +
                kind("ProcessStartTime").filter { it.number("hosting_type_id") in ACTIVITY_HOSTING_TYPE_IDS }
            ).filter { record ->
            // A monotonic start-info time only places the launch if the device barely slept before reading it.
            record.kind != WAKE_START_INFO || record.attr(WAKE_START_INFO_CLOCK) == "elapsed" ||
                (record.number(WAKE_START_INFO_SLEEP) ?: Long.MAX_VALUE) <= MERGE_WINDOW
        }.mapNotNull { record -> record.targetUid?.let { (record.boot to it) to record } }
            .groupBy({ it.first }, { it.second }).mapValues { (_, list) -> list.sortedBy { it.elapsedNanos } }
        val kills = kind("am_kill").filter { it.source == SYSTEM_WAKE_EVENTS }.groupBy { Triple(it.boot, it.attr("user")?.toIntOrNull(), it.processName) }
        val deaths = kind("AppProcessDied").groupBy { Triple(it.boot, it.targetUid, it.processName) }

        coldStarts(index).mapNotNull { start ->
            val reference = start.reference
            val request = start.stats?.let { requestsByStart[it.id] }?.singleOrNull { it.kind in peerTriggers }
            val target = target(start, request) ?: return@mapNotNull null
            val component = start.component?.let { qualifiedWakeComponent(target, it) }
            val action = start.info?.action ?: start.stats?.action
            val reason = start.info?.attr("reason")
            val activity = reason in activityReasons || start.event?.attr("hosting_type") in activityHostingTypes ||
                start.stats?.number("hosting_type_id") in ACTIVITY_HOSTING_TYPE_IDS
            val window = (start.elapsed - JOIN_WINDOW)..(start.elapsed + JOIN_WINDOW)

            fun correlated(): Trigger? {
                val key = start.boot to start.uid
                jobs[key]?.lastOrNull { it.elapsedNanos in window }?.let { return Trigger(ReviveTrigger.JOB, Confidence.JOINED, "android", it.attr("job_name")) }
                syncs[key]?.lastOrNull { it.elapsedNanos in window }?.let { return Trigger(ReviveTrigger.SYNC, Confidence.JOINED, "android", component) }
                wakeups[key]?.lastOrNull { it.elapsedNanos in window }?.let { return Trigger(ReviveTrigger.ALARM, Confidence.JOINED, "android", it.attr("tag")) }
                batches[start.boot]?.lastOrNull { batch ->
                    batch.elapsedNanos in window && batch.attr("uids")?.split(',')?.any { it.toIntOrNull() == start.uid } == true
                }?.let { return Trigger(ReviveTrigger.ALARM, Confidence.JOINED, "android", component) }
                return null
            }

            fun systemCaller(request: WakeRecord): Trigger = when (request.kind) {
                "BroadcastDeliveryEventReported" -> Trigger(ReviveTrigger.SYSTEM_BROADCAST, Confidence.JOINED, "android", request.action)

                "ServiceRequestEventReported" -> if (grantedSpecialAccess(target, component)) {
                    Trigger(ReviveTrigger.SYSTEM_BIND, Confidence.JOINED, "android", component)
                } else {
                    correlated() ?: Trigger(ReviveTrigger.UNKNOWN, Confidence.JOINED, "android", component)
                }

                else -> correlated() ?: Trigger(ReviveTrigger.UNKNOWN, Confidence.JOINED, "android", component ?: request.action)
            }

            fun joined(request: WakeRecord): Trigger {
                val caller = request.callerUid
                val callerPackage = request.callerPackage ?: packageOf(caller)
                return when {
                    (caller != null && caller == start.uid) || (caller == null && request.callerPackage == target) -> Trigger(
                        ReviveTrigger.SELF_PROCESS,
                        Confidence.JOINED,
                        target,
                        component ?: request.action,
                        requesterInBackground(requesterState[request.kind]?.let { request.number(it)?.toInt() }),
                    )

                    WakePushTransports.matches(callerPackage, request.action) ->
                        Trigger(ReviveTrigger.PUSH_TRANSPORT, Confidence.JOINED, callerPackage, request.action ?: component)

                    caller != null && !isApp(caller) -> systemCaller(request)

                    caller != null || request.callerPackage != null -> Trigger(
                        peerTriggers.getValue(request.kind),
                        Confidence.JOINED,
                        callerPackage ?: caller.toString(),
                        if (request.kind == "BroadcastDeliveryEventReported") request.action ?: component else component,
                        requesterInBackground(requesterState[request.kind]?.let { request.number(it)?.toInt() }),
                    )

                    else -> correlated() ?: Trigger(ReviveTrigger.UNKNOWN, Confidence.JOINED, null, component ?: action)
                }
            }

            val restart = restarts[start.boot].orEmpty().lastOrNull { schedule ->
                val delay = schedule.attr("delay_ms")?.toLongOrNull()?.coerceAtLeast(0) ?: 0
                component != null && schedule.elapsedNanos <= start.elapsed &&
                    start.elapsed <= schedule.elapsedNanos + delay * 1_000_000 + JOIN_WINDOW &&
                    qualifiedWakeComponent(target, schedule.component) == component
            }
            val classified = when {
                restart != null -> Trigger(ReviveTrigger.STICKY_RESTART, Confidence.JOINED, target, component)

                // The app showed UI. Whoever launched it, the user saw the start.
                activity -> Trigger(
                    ReviveTrigger.PEER_ACTIVITY,
                    Confidence.DIRECT,
                    request?.let { it.callerPackage ?: packageOf(it.callerUid) ?: it.callerUid?.toString() },
                    component,
                    requestId = request?.id,
                )

                reason in directReasons -> Trigger(
                    directReasons.getValue(reason!!),
                    Confidence.DIRECT,
                    if (reason == "BOOT COMPLETE") "android" else null,
                    if (reason == "BOOT COMPLETE") action ?: "android.intent.action.BOOT_COMPLETED" else component ?: action,
                )

                start.stats?.number("trigger_type") in triggerTypes -> Trigger(
                    triggerTypes.getValue(start.stats!!.number("trigger_type")!!),
                    Confidence.DIRECT,
                    null,
                    action ?: component,
                )

                // Only the holder of com.google.android.c2dm.permission.SEND can deliver this action to an FCM receiver.
                WakePushTransports.matches(GMS, action) && request == null ->
                    Trigger(ReviveTrigger.PUSH_TRANSPORT, Confidence.DIRECT, GMS, action)

                request != null -> joined(request).copy(requestId = request.id)

                reason == "SERVICE" && grantedSpecialAccess(target, component) ->
                    Trigger(ReviveTrigger.SYSTEM_BIND, Confidence.SNAPSHOT, "android", component)

                // Only the platform may send a protected broadcast (`dumpsys package protected-broadcasts`).
                reason == "BROADCAST" && start.info?.attr("protected_broadcast") == "true" && action != null ->
                    Trigger(ReviveTrigger.SYSTEM_BROADCAST, Confidence.DIRECT, "android", action)

                else -> correlated() ?: Trigger(
                    ReviveTrigger.UNKNOWN,
                    Confidence.DIRECT,
                    null,
                    if (reason == "BROADCAST" || start.event?.attr("hosting_type") == "broadcast") action ?: component else component ?: action,
                )
            }

            // A process started by another process of the same app while that one is visible is not a revive.
            if (classified.trigger == ReviveTrigger.SELF_PROCESS && classified.actorInBackground == false) return@mapNotNull null
            val startWall = wallNanos(reference, start.elapsed)
            val after = start.elapsed + PRODUCTIVE_WINDOW
            val userInitiated = activity || within(shown[start.boot to start.uid], start.elapsed - MERGE_WINDOW, after).isNotEmpty()
            val justification = when {
                userInitiated -> Justification.USER_INITIATED
                classified.trigger == ReviveTrigger.SYSTEM_BIND && grantedSpecialAccess(target, component) -> Justification.USER_GRANTED_ROLE
                within(notifications[start.boot to start.uid], start.elapsed, after).isNotEmpty() -> Justification.PRODUCTIVE
                else -> Justification.NONE
            }
            val killWindow = (start.elapsed - KILL_WINDOW) until start.elapsed
            // Only a kill meaning the user or the system wanted the app gone makes the restart notable.
            val killed = start.process?.let { process ->
                kills[Triple(start.boot, userOfUid(start.uid), process)].orEmpty().lastOrNull { kill ->
                    kill.elapsedNanos in killWindow && kill.attr("reason")?.let(WakeKillReasons::qualifies) == true
                }?.attr("reason") ?: deaths[Triple(start.boot, start.uid, process)].orEmpty().map { died ->
                    died to (died.number("reason")?.toInt() to died.number("sub_reason")?.toInt())
                }.lastOrNull { (died, codes) -> died.elapsedNanos in killWindow && WakeKillReasons.qualifies(codes.first, codes.second) }
                    ?.let { (_, codes) -> WakeKillReasons.name(codes.first, codes.second) }
            }
            Detected(
                WakeFinding.revive(
                    target, classified.trigger, classified.actor, classified.detail, classified.actorInBackground,
                    killed, justification, classified.confidence, startWall,
                ),
            ) to classified.requestId
        }
    }
}
