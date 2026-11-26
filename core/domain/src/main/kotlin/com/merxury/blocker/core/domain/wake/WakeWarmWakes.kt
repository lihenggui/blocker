// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.model.component.isRegularAppUid
import com.merxury.blocker.core.model.component.userOfUid
import com.merxury.blocker.core.model.wake.Confidence
import com.merxury.blocker.core.model.wake.Justification
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeFinding
import com.merxury.blocker.core.model.wake.qualifiedWakeComponent

internal object WakeWarmWakes {
    // AppFreezeChanged.Action and UnfreezeReason in AOSP stats/atoms.proto (atom 254).
    // Housekeeping (including PING=15, FEATURE_FLAGS=19 and BACKUP=22) and unknown values
    // are excluded. Alarms, jobs and syncs can unfreeze via receivers or services.
    private const val UNFREEZE_APP = 2L
    private val wakeReasons = setOf(
        3L, // START_RECEIVER
        4L, // BIND_SERVICE
        6L, // START_SERVICE
        7L, // GET_PROVIDER
        16L, // FILE_LOCKS
        18L, // BINDER_TXNS
    )

    fun detect(index: WakeEvidenceIndex, coldStarts: List<ColdStart>, claimedRequestIds: Set<String>): List<Detected> = with(index) {
        val unfreezes = kind("AppFreezeChanged")
        if (unfreezes.isEmpty()) return emptyList()
        val coldByUid = coldStarts.groupBy { it.boot to it.uid }
        val services = byUid("ServiceRequestEventReported") { record ->
            record.targetUid.takeUnless { record.startType == 3 || record.id in claimedRequestIds }
        }
        unfreezes.mapNotNull { record ->
            val elapsed = record.elapsedNanos
            if (record.number("state") != UNFREEZE_APP || record.number("unfreeze_reason_v2") !in wakeReasons || !isScreenOff(record.boot, elapsed)) {
                return@mapNotNull null
            }
            val (uid, target) = unfreezeTarget(record) ?: return@mapNotNull null
            val key = record.boot to uid
            val from = elapsed - WakeRevives.JOIN_WINDOW
            val through = elapsed + WakeRevives.JOIN_WINDOW
            if (coldByUid[key].orEmpty().any { it.elapsed in from..through && it.matchesProcess(record) }) return@mapNotNull null

            val productive = postedNotification(record.boot, uid, target, elapsed)
            val service = within(services[key], from, through).mapNotNull { request ->
                serviceWake(request, uid, target, productive)
            }.distinct().singleOrNull()
            val cause = service ?: scheduledWake(record.boot, uid, target, elapsed) ?: return@mapNotNull null
            val justification = when {
                cause.trigger == ReviveTrigger.SYSTEM_BIND -> Justification.USER_GRANTED_ROLE
                productive -> Justification.PRODUCTIVE
                else -> Justification.NONE
            }
            Detected(WakeFinding.revive(target, cause.trigger, cause.actor, cause.detail, cause.actorInBackground, null, justification, cause.confidence, wallNanos(record, elapsed)))
        }
    }

    private fun ColdStart.matchesProcess(record: WakeRecord): Boolean {
        val eventPid = event?.pid
        val statsPid = stats?.pid
        val pid = record.pid
        if (pid != null && (pid == eventPid || pid == statsPid)) return true
        return eventPid == null && statsPid == null && (process == null || process == record.processName)
    }

    private fun WakeEvidenceIndex.serviceWake(request: WakeRecord, uid: Int, target: String, productive: Boolean): WakeTrigger? {
        if ((request.targetPackage ?: packageOf(uid)) != target) return null
        val component = qualifiedWakeComponent(target, request.component)
        if (request.component != null && component == null) return null
        val callerUid = request.callerUid
        if (callerUid == 1000 && component != null && request.number("request_type") == 2L && grantedSpecialAccess(target, component, userOfUid(uid))) {
            return WakeTrigger(ReviveTrigger.SYSTEM_BIND, Confidence.JOINED, "android", component)
        }
        if (callerUid != null && !isRegularAppUid(callerUid)) return null
        val caller = request.callerPackage ?: packageOf(callerUid) ?: return null
        if (!caller.isAppPackage()) return null
        if (caller == target && callerUid != null && callerUid != uid) return null
        if (WakePushTransports.matches(caller, request.action)) {
            if (productive) return null
            return WakeTrigger(ReviveTrigger.PUSH_TRANSPORT, Confidence.JOINED, caller, component?.let { "$target/$it" } ?: request.action)
        }
        val background = requesterInBackground(request.number("requester_process_state")?.toInt())
        if (background == false || (caller != target && background != true)) return null
        val trigger = if (caller == target) ReviveTrigger.SELF_PROCESS else ReviveTrigger.PEER_SERVICE
        return WakeTrigger(trigger, Confidence.JOINED, caller, component ?: request.action, background)
    }
}
