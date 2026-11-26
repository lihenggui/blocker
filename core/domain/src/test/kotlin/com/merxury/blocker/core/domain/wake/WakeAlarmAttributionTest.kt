// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.wake.WakeDetectionEvidence
import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.model.wake.Justification
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WakeAlarmAttributionTest {
    private val target = "target.app"
    private val caller = "peer.app"
    private val uid = 10022
    private val callerUid = 10011
    private val second = 1_000_000_000L
    private val elapsed = 300 * second
    private val digest = "1234567890abcdef12345678"
    private val cold = wakeRecord("start", "am_proc_start", elapsed, source = SYSTEM_WAKE_EVENTS, process = target)
    private val off = wakeRecord("off", "InteractiveStateChanged", elapsed - second, attributes = mapOf("state" to "0"))
    private val warm = wakeRecord(
        "unfreeze",
        "AppFreezeChanged",
        elapsed,
        targetUid = null,
        targetPackage = null,
        pid = 42,
        process = target,
        attributes = mapOf("state" to "2", "unfreeze_reason_v2" to "3"),
    )
    private val alarm = wakeRecord(
        "delivery",
        "WakeupAlarmOccurred",
        elapsed - second,
        targetUid = null,
        attributes = mapOf("attribution_node" to "$uid", "tag" to digest),
    )

    private fun registeredAlarm(
        token: String = "token",
        creator: String = target,
        app: String = target,
        component: String? = "$app/.Receiver",
        action: String? = null,
        time: Long = elapsed - 30 * second,
        boot: String = cold.boot,
    ): List<WakeRecord> = listOf(
        wakeRecord(
            "alarm-$token",
            "PendingAlarm",
            time,
            source = "ALARMS",
            targetUid = null,
            targetPackage = null,
            callerPackage = creator,
            attributes = mapOf("record" to token, "creator_package" to creator),
        ).copy(boot = boot),
        wakeRecord(
            "intent-$token", "PendingIntentRegistration", time, source = "PENDING_INTENTS", targetUid = null,
            targetPackage = app, callerUid = if (creator == target) uid else callerUid, callerPackage = creator, component = component,
            action = action, attributes = mapOf("record" to token, "type" to "broadcastIntent"),
        ).copy(boot = boot),
    )

    private fun detect(records: List<WakeRecord>): List<Detected> {
        val relations = WakeAttribution.relationships(records)
        return WakeDetection.detect(WakeDetectionEvidence(records, relations, emptyList(), emptyList(), emptyList(), emptyList(), mapOf(uid to setOf(target), callerUid to setOf(caller))))
    }

    private fun activity(records: List<WakeRecord>) = WakeDetection.daily(detect(records), ZoneOffset.UTC).let { rows ->
        val day = rows.single().day
        buildAppWakeActivity(target, day, day, rows, emptyList())
    }

    @Test fun givenUniquePendingAlarm_whenDetectingColdAndWarmDeliveries_thenTheReceiverIsBlockable() {
        for (start in listOf(cold, warm)) {
            val result = activity(listOf(off, start, alarm) + registeredAlarm())
            val method = result.own.single()
            val path = method.paths.single()
            assertThat(method.kind).isEqualTo(OwnWakeKind.TIMED)
            assertThat(method.component).isEqualTo("$target.Receiver")
            assertThat(path.trigger).isEqualTo(ReviveTrigger.ALARM)
            assertThat(path.componentType).isNull()
            assertThat(path.blockMethod).isEqualTo(WakeBlockMethod.COMPONENT)
        }
    }

    @Test fun givenAlarmFromAnotherApp_whenDetecting_thenTheTimedPathRetainsItsCallerAndComponent() {
        val delivery = alarm.copy(targetPackage = caller, attributes = """{"attribution_node":"$callerUid","tag":"$digest"}""")
        for (start in listOf(cold, warm)) {
            for (observed in listOf(delivery, alarm)) {
                val result = activity(listOf(off, start, observed) + registeredAlarm(creator = caller))
                assertThat(result.peers).isEmpty()
                val method = result.own.single()
                val path = method.paths.single()
                assertThat(method.kind).isEqualTo(OwnWakeKind.TIMED)
                assertThat(path.trigger).isEqualTo(ReviveTrigger.PENDING_INTENT)
                assertThat(path.actor).isEqualTo(caller)
                assertThat(path.component).isEqualTo("$target.Receiver")
                assertThat(path.blockMethod).isEqualTo(WakeBlockMethod.COMPONENT)
            }
        }
    }

    @Test fun givenStaleDailyAlarmSnapshot_whenRoutineSnapshotIsCloseToDelivery_thenTheCreatorIsAttributed() {
        val sinceDaily = Duration.ofHours(20).toNanos()
        val prior = registeredAlarm(time = elapsed, creator = caller, token = "daily")
        val current = registeredAlarm(time = elapsed + sinceDaily + second, creator = caller, token = "routine")
        val start = cold.copy(elapsedNanos = elapsed + sinceDaily)
        val delivery = alarm.copy(elapsedNanos = alarm.elapsedNanos + sinceDaily)

        val path = activity(listOf(start, delivery) + prior + current).own.single().paths.single()

        assertThat(path.trigger).isEqualTo(ReviveTrigger.PENDING_INTENT)
        assertThat(path.actor).isEqualTo(caller)
    }

    @Test fun givenActionOnlyRegistration_whenDetecting_thenDottedActionIsPreservedWithoutInventingAComponent() {
        val batch = alarm.copy(kind = "AlarmBatchDelivered", attributes = """{"uids":"$uid"}""")
        val action = "$target.RefreshReceivers"
        val result = activity(listOf(cold, batch) + registeredAlarm(creator = caller, component = null, action = action))
        val path = result.own.single().paths.single()
        assertThat(path.trigger).isEqualTo(ReviveTrigger.PENDING_INTENT)
        assertThat(path.actor).isEqualTo(caller)
        assertThat(path.action).isEqualTo(action)
        assertThat(path.component).isNull()
        assertThat(path.blockMethod).isEqualTo(WakeBlockMethod.BACKGROUND_RESTRICTION)
    }

    @Test fun givenAmbiguousOrOutOfWindowRegistrations_whenDetecting_thenTheDigestRemainsUnresolved() {
        val unrelated = registeredAlarm(token = "other", app = "other.app", time = elapsed - 2 * second)
        val candidates = listOf(
            registeredAlarm() + registeredAlarm(token = "different", component = "$target/.Other"),
            registeredAlarm(boot = "previous"),
            registeredAlarm(time = elapsed + 6 * second),
            registeredAlarm() + unrelated,
            registeredAlarm().filter { it.kind == "PendingIntentRegistration" },
        )
        for (records in candidates) {
            val path = activity(listOf(cold, alarm) + records).own.single().paths.single()
            assertThat(path.component).isNull()
            assertThat(path.blockMethod).isEqualTo(WakeBlockMethod.BACKGROUND_RESTRICTION)
            assertThat(detect(listOf(cold, alarm) + records).single().finding.detail).isEqualTo(digest)
        }
        val near = activity(listOf(cold, alarm) + registeredAlarm(time = elapsed + WakeRevives.JOIN_WINDOW))
        assertThat(near.own.single().component).isEqualTo("$target.Receiver")
    }

    @Test fun givenKnownAlarmCreator_whenRegistrationsDiffer_thenOnlyThatCreatorsPendingIntentMatches() {
        val registrations = registeredAlarm() + registeredAlarm(token = "peer", creator = caller, component = "$target/.Other")
        assertThat(activity(listOf(cold, alarm) + registrations).own.single().component).isEqualTo("$target.Receiver")
        val peerDelivery = alarm.copy(targetPackage = caller, attributes = """{"attribution_node":"$callerUid","tag":"$digest"}""")
        assertThat(activity(listOf(cold, peerDelivery) + registrations).own.single().component).isEqualTo("$target.Other")
    }

    @Test fun givenDirectAlarmStart_whenARegistrationNamesTheTarget_thenTheComponentIsBlockable() {
        val start = cold.copy(kind = "ProcessStartTime", source = "statsd", startType = 3, attributes = """{"trigger_type":"1"}""")
        val path = activity(listOf(start) + registeredAlarm()).own.single().paths.single()
        assertThat(path.trigger).isEqualTo(ReviveTrigger.ALARM)
        assertThat(path.component).isEqualTo("$target.Receiver")
        assertThat(path.blockMethod).isEqualTo(WakeBlockMethod.COMPONENT)
    }

    @Test fun givenDozeExemptSchedule_whenDetecting_thenOnlyObservedDeliveryCanExplainAnAlarmWake() {
        val job = alarm.copy(id = "job", kind = "ScheduledJobStateChanged", attributes = """{"attribution_node":"$uid","state":"1"}""")
        val schedule = wakeRecord(
            "schedule",
            "AlarmScheduled",
            elapsed - 2 * second,
            callerUid = uid,
            targetUid = null,
            targetPackage = null,
            attributes = mapOf("is_allow_while_idle" to "1"),
        )
        assertThat(detect(listOf(off, warm, schedule))).isEmpty()
        for (start in listOf(cold, warm)) {
            val records = listOf(off, start, job, alarm)
            assertThat(detect(records).single().finding.trigger).isEqualTo(ReviveTrigger.JOB)
            assertThat(detect(records + schedule.copy(boot = "previous")).single().finding.trigger).isEqualTo(ReviveTrigger.JOB)
            for (evidence in listOf(schedule, schedule.copy(attributes = """{"is_alarm_clock":"1"}"""))) {
                val finding = detect(records + evidence).single().finding
                assertThat(finding.trigger).isEqualTo(ReviveTrigger.ALARM)
                assertThat(finding.justification).isEqualTo(Justification.NONE)
            }
        }
    }
}
