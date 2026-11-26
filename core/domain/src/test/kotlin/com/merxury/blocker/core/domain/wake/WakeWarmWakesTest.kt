// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.wake.WakeDetectionEvidence
import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.database.wake.WakeRelation
import com.merxury.blocker.core.model.component.UIDS_PER_USER
import com.merxury.blocker.core.model.wake.Justification
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import org.junit.Test

class WakeWarmWakesTest {
    private val target = "target.app"
    private val peer = "peer.app"
    private val uid = 10022
    private val peerUid = 10011
    private val packages = mapOf(uid to setOf(target), peerUid to setOf(peer))
    private val second = 1_000_000_000L
    private val elapsed = 300 * second
    private val request = wakeRecord(
        "request", "ServiceRequestEventReported", elapsed - second, callerUid = peerUid, targetUid = uid,
        callerPackage = peer, targetPackage = target, component = ".Worker", startType = 1,
        attributes = mapOf("requester_process_state" to "15"),
    )
    private val screenOff = wakeRecord("off", "InteractiveStateChanged", elapsed - second, attributes = mapOf("state" to "0"))
    private val digest = "1234567890abcdef12345678"

    private fun unfreeze(reason: Int = 6, state: Int = 2) = wakeRecord(
        "freeze",
        "AppFreezeChanged",
        elapsed,
        targetUid = null,
        targetPackage = null,
        process = "$target:work",
        pid = 42,
        attributes = mapOf("state" to "$state", "unfreeze_reason_v2" to "$reason"),
    )

    private fun coldStart(time: Long) = wakeRecord(
        "start",
        "am_proc_start",
        time,
        source = SYSTEM_WAKE_EVENTS,
        targetUid = uid,
        targetPackage = null,
        process = "$target:work",
        pid = 42,
    )

    private fun timed(kind: String, attributes: Map<String, String> = emptyMap()) = wakeRecord(
        kind,
        kind,
        elapsed + second,
        targetUid = null,
        targetPackage = null,
        attributes = mapOf("attribution_node" to "$uid", "state" to "1") + attributes,
    )

    private fun findings(
        vararg records: WakeRecord,
        packages: Map<Int, Set<String>> = this.packages,
        relations: List<WakeRelation> = emptyList(),
    ) = WakeDetection.findings(WakeDetectionEvidence(records.toList(), relations, emptyList(), emptyList(), emptyList(), emptyList(), packages))

    @Test fun givenScreenOffUnfreezeAndWarmServiceRequest_whenDetecting_thenTheBackgroundCallerAndComponentAreNamed() {
        val finding = findings(screenOff, unfreeze(), request).single()
        assertThat(finding.trigger).isEqualTo(ReviveTrigger.PEER_SERVICE)
        assertThat(finding.actor).isEqualTo(peer)
        assertThat(finding.target).isEqualTo(target)
        assertThat(finding.detail).isEqualTo("$target.Worker")
        assertThat(finding.actorInBackground).isTrue()
        assertThat(finding.justification).isEqualTo(Justification.NONE)
        assertThat(finding.afterKill).isNull()

        val self = request.copy(callerUid = uid, callerPackage = target, attributes = "{}")
        assertThat(findings(screenOff, unfreeze(), self).single().trigger).isEqualTo(ReviveTrigger.SELF_PROCESS)
    }

    @Test fun givenScreenOffUnfreezeAndTimedWork_whenDetecting_thenJobSyncAndAlarmDeliveriesAreAttributed() {
        val wake = unfreeze(reason = 3)
        val job = timed("ScheduledJobStateChanged", mapOf("job_name" to "$target/.Job"))
        val jobWake = findings(screenOff, wake, job).single()
        assertThat(jobWake.trigger).isEqualTo(ReviveTrigger.JOB)
        assertThat(jobWake.detail).isEqualTo("$target/.Job")
        assertThat(findings(screenOff, wake, timed("SyncStateChanged")).single().trigger).isEqualTo(ReviveTrigger.SYNC)

        val alarm = timed("WakeupAlarmOccurred", mapOf("tag" to digest))
        val alarmWake = findings(screenOff, wake, alarm).single()
        assertThat(alarmWake.trigger).isEqualTo(ReviveTrigger.ALARM)
        assertThat(alarmWake.detail).isEqualTo(digest)
        assertThat(alarmWake.justification).isEqualTo(Justification.NONE)
        val batch = timed("AlarmBatchDelivered", mapOf("uids" to "$peerUid,$uid"))
        assertThat(findings(screenOff, wake, batch).single().trigger).isEqualTo(ReviveTrigger.ALARM)
    }

    @Test fun givenUnfreezeWithoutScreenOffEvidence_whenDetecting_thenNoWarmWakeIsRecorded() {
        val wake = unfreeze()
        val on = screenOff.copy(id = "on", elapsedNanos = elapsed, attributes = """{"state":"1"}""")
        assertThat(findings(screenOff, on, wake, request)).isEmpty()
        assertThat(findings(wake, request)).isEmpty()
        assertThat(findings(screenOff.copy(boot = "previous"), wake, request)).isEmpty()
    }

    @Test fun givenNonWakeReasonsOrNoMatchingCause_whenDetecting_thenNoWarmWakeIsRecorded() {
        for (reason in listOf(0, 1, 2, 5, 8, 10, 11, 12, 13, 15, 17, 19, 20, 22, 24, 25, 26, 29, 100)) {
            assertThat(findings(screenOff, unfreeze(reason), request)).isEmpty()
        }
        assertThat(findings(screenOff, unfreeze(state = 1), request)).isEmpty()
        assertThat(findings(screenOff, unfreeze(reason = 18))).isEmpty()
        assertThat(findings(screenOff, unfreeze(), request.copy(startType = 3))).isEmpty()
        assertThat(findings(screenOff, unfreeze(), request.copy(elapsedNanos = elapsed - 6 * second))).isEmpty()
        assertThat(findings(screenOff, unfreeze(), timed("ScheduledJobStateChanged", mapOf("state" to "2")))).isEmpty()
        val foreground = request.copy(attributes = """{"requester_process_state":"2"}""")
        assertThat(findings(screenOff, unfreeze(), foreground)).isEmpty()
        assertThat(findings(screenOff, unfreeze(), request.copy(attributes = "{}"))).isEmpty()
        assertThat(findings(screenOff, unfreeze(), request, request.copy(id = "other", component = ".Other"))).isEmpty()
    }

    @Test fun givenColdStartWithinTheJoinWindow_whenDetecting_thenNoWarmWakeIsCountedTwice() {
        for (offset in listOf(-WakeRevives.JOIN_WINDOW, WakeRevives.JOIN_WINDOW)) {
            assertThat(findings(screenOff, unfreeze(), request, coldStart(elapsed + offset)).single().trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        }
        val otherBoot = coldStart(elapsed).copy(boot = "previous")
        assertThat(findings(screenOff, unfreeze(), request, otherBoot).map { it.trigger }).containsExactly(ReviveTrigger.UNKNOWN, ReviveTrigger.PEER_SERVICE)
    }

    @Test fun givenAnotherProcessOfTheSameUidColdStarted_whenUnfreezing_thenTheWarmWakeIsStillDetected() {
        val start = coldStart(elapsed - 2 * second).copy(pid = 11, processName = target)

        assertThat(findings(screenOff, start, unfreeze(), request).map { it.trigger })
            .containsExactly(ReviveTrigger.UNKNOWN, ReviveTrigger.PEER_SERVICE)
        assertThat(findings(screenOff, start.copy(pid = null), unfreeze(), request).map { it.trigger })
            .containsExactly(ReviveTrigger.UNKNOWN, ReviveTrigger.PEER_SERVICE)
        assertThat(findings(screenOff, start.copy(pid = null, processName = "$target:work"), unfreeze(), request).map { it.trigger })
            .containsExactly(ReviveTrigger.UNKNOWN)
        assertThat(findings(screenOff, start.copy(pid = null, processName = null), unfreeze(), request).map { it.trigger })
            .containsExactly(ReviveTrigger.UNKNOWN)
    }

    @Test fun givenServiceRequestClaimedByColdStart_whenAnotherProcessUnfreezes_thenTheRequestIsUsedOnce() {
        val start = coldStart(elapsed - 2 * second).copy(kind = "ProcessStartTime", source = "statsd", pid = 11, targetPackage = target)
        val relation = WakeRelation(request.id, start.id, "MATCHING_CALLER")

        assertThat(findings(screenOff, start, unfreeze(), request, relations = listOf(relation)).map { it.trigger })
            .containsExactly(ReviveTrigger.PEER_SERVICE)
    }

    @Test fun givenMultipleProfilesOrSharedUid_whenResolvingUnfreeze_thenOnlyTheLivePidCanDisambiguate() {
        val profiles = packages + (uid + UIDS_PER_USER to setOf(target))
        val shared = packages + (uid to setOf(target, "shared.app"))
        val start = coldStart(elapsed - 60 * second).copy(component = "$target/.Worker")
        for (owners in listOf(profiles, shared)) {
            assertThat(findings(screenOff, unfreeze(), request, packages = owners)).isEmpty()
            assertThat(findings(screenOff, unfreeze(), request, start, packages = owners).map { it.trigger })
                .containsExactly(ReviveTrigger.UNKNOWN, ReviveTrigger.PEER_SERVICE).inOrder()
        }
        val stats = start.copy(kind = "ProcessStartTime", source = "statsd", processName = null, targetPackage = target)
        assertThat(findings(screenOff, unfreeze(), request, stats, packages = profiles).map { it.trigger })
            .containsExactly(ReviveTrigger.UNKNOWN, ReviveTrigger.PEER_SERVICE).inOrder()
        val reused = start.copy(id = "reused", elapsedNanos = elapsed - 30 * second, processName = peer, targetUid = peerUid)
        assertThat(findings(screenOff, unfreeze(), request, start, reused).map { it.trigger }).containsExactly(ReviveTrigger.UNKNOWN, ReviveTrigger.UNKNOWN)
        val death = start.copy(id = "died", kind = "am_proc_died", elapsedNanos = elapsed - 30 * second)
        assertThat(findings(screenOff, unfreeze(), request, start, death).single().trigger).isEqualTo(ReviveTrigger.UNKNOWN)
    }

    @Test fun givenPersistentPushDaemon_whenDetecting_thenOnlySilentWarmPushesAreRecorded() {
        val push = request.copy(callerPackage = "com.xiaomi.xmsf", attributes = """{"requester_process_state":"0"}""")
        val notification = wakeRecord("notification", "NotificationReported", elapsed + 2 * second, attributes = mapOf("event_id" to "162"))

        val finding = findings(screenOff, unfreeze(), push).single()
        assertThat(finding.trigger).isEqualTo(ReviveTrigger.PUSH_TRANSPORT)
        assertThat(finding.justification).isEqualTo(Justification.NONE)
        assertThat(findings(screenOff, unfreeze(), push, notification)).isEmpty()
    }

    @Test fun givenNotificationOrGrantedSystemBind_whenDetecting_thenTheSameJustificationsApplyAsForColdStarts() {
        val notification = wakeRecord("notification", "NotificationReported", elapsed + 2 * second, attributes = mapOf("event_id" to "162"))
        assertThat(findings(screenOff, unfreeze(), request, notification).single().justification).isEqualTo(Justification.PRODUCTIVE)
        assertThat(findings(screenOff, unfreeze(), request, notification.copy(elapsedNanos = elapsed + 11 * second)).single().justification)
            .isEqualTo(Justification.NONE)
        val binding = request.copy(callerUid = 1000, callerPackage = "android", attributes = """{"request_type":"2"}""")
        val grant = wakeRecord("grant", "SystemBinding", elapsed, component = "$target/.Worker")
        assertThat(findings(screenOff, unfreeze(reason = 4), binding)).isEmpty()
        assertThat(findings(screenOff, unfreeze(reason = 4), binding, grant).single().justification).isEqualTo(Justification.USER_GRANTED_ROLE)
    }
}
