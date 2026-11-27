// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.wake.WakeDetectionEvidence
import com.merxury.blocker.core.domain.wake.WakeDetectionTestValues.PEER
import com.merxury.blocker.core.domain.wake.WakeDetectionTestValues.PEER_UID
import com.merxury.blocker.core.domain.wake.WakeDetectionTestValues.SECOND
import com.merxury.blocker.core.domain.wake.WakeDetectionTestValues.START
import com.merxury.blocker.core.domain.wake.WakeDetectionTestValues.TARGET
import com.merxury.blocker.core.domain.wake.WakeDetectionTestValues.TARGET_UID
import com.merxury.blocker.core.domain.wake.WakeDetectionTestValues.WALL
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.UIDS_PER_USER
import com.merxury.blocker.core.model.wake.Confidence
import com.merxury.blocker.core.model.wake.HoldCause
import com.merxury.blocker.core.model.wake.Justification
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeFinding
import com.merxury.blocker.core.model.wake.WakeHoldAssociation
import com.merxury.blocker.core.model.wake.WakeHoldPeriod
import com.merxury.blocker.core.model.wake.WakeHoldProcess
import com.merxury.blocker.core.model.wake.WakeRecord
import com.merxury.blocker.core.model.wake.WakeRelation
import com.merxury.blocker.core.model.wake.WakeVerdict
import com.merxury.blocker.core.privileged.wake.WAKE_AUDIO_PLAYBACK
import com.merxury.blocker.core.privileged.wake.WAKE_NOTIFICATIONS_OFF
import com.merxury.blocker.core.privileged.wake.WAKE_OVERLAY_WINDOWS
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import java.time.ZoneOffset

internal class WakeDetectionContextTest : WakeDetectionTestFixture() {
    @Test fun givenPendingAlarmHistory_whenAnEmptySnapshotIsNewerOrTheRegistrationIsStale_thenTheAlarmIsUnresolved() {
        val hour = 60 * 60 * SECOND
        val start = wakeRecord("start", "am_proc_start", START + hour, source = SYSTEM_WAKE_EVENTS, process = TARGET)
        val digest = "1234567890abcdef12345678"
        val delivery = wakeRecord(
            "delivery",
            "WakeupAlarmOccurred",
            start.elapsedNanos,
            attributes = mapOf("attribution_node" to "$TARGET_UID", "tag" to digest),
        )
        val pending = wakeRecord(
            "pending",
            "PendingAlarm",
            START,
            source = "ALARMS",
            callerPackage = TARGET,
            targetUid = null,
            targetPackage = null,
            attributes = mapOf("record" to "token", "creator_package" to TARGET),
        )
        val intent = wakeRecord(
            "intent",
            "PendingIntentRegistration",
            START,
            source = "PENDING_INTENTS",
            callerUid = TARGET_UID,
            callerPackage = TARGET,
            component = "$TARGET/.Receiver",
            attributes = mapOf("record" to "token", "type" to "broadcastIntent"),
        )
        val relation = WakeRelation(pending.id, intent.id, "PENDING_INTENT_REGISTRATION")
        val empty = wakeRecord("empty", "PendingAlarm", START + SECOND, source = "ALARMS", targetUid = null, targetPackage = null)

        assertThat(revive(start, delivery, pending, intent, relations = listOf(relation)).detail).isEqualTo("$TARGET/$TARGET.Receiver")
        assertThat(revive(start, delivery, pending, intent, empty, relations = listOf(relation)).detail).isEqualTo(digest)
        assertThat(revive(start.copy(elapsedNanos = start.elapsedNanos + 1), delivery, pending, intent, relations = listOf(relation)).detail)
            .isEqualTo(digest)
    }

    @Test fun givenCoarseAlarmBatchAndUnrelatedIdleExemptSchedule_whenDetecting_thenJobsAndSyncsKeepPrecedence() {
        val start = coldStart.copy(component = null)
        val batch = wakeRecord("batch", "AlarmBatchDelivered", START, attributes = mapOf("uids" to "$TARGET_UID"))
        val schedule = wakeRecord(
            "schedule",
            "AlarmScheduled",
            START - SECOND,
            callerUid = TARGET_UID,
            attributes = mapOf("is_allow_while_idle" to "1"),
        )
        val attributes = mapOf("attribution_node" to "$TARGET_UID", "state" to "1")
        val job = wakeRecord("job", "ScheduledJobStateChanged", START, attributes = attributes + ("job_name" to "$TARGET/.Worker"))
        val sync = wakeRecord("sync", "SyncStateChanged", START, attributes = attributes)

        assertThat(revive(start, batch, schedule, job).trigger).isEqualTo(ReviveTrigger.JOB)
        assertThat(revive(start, batch, schedule, sync).trigger).isEqualTo(ReviveTrigger.SYNC)
    }

    @Test fun givenCorrelatedJob_whenDetecting_thenHostedServiceWinsAndJobNameIsTheFallback() {
        val event = wakeRecord("event", "am_proc_start", START, source = SYSTEM_WAKE_EVENTS, component = "{$TARGET/.Worker}", process = TARGET)
        val digest = "1234567890abcdef12345678"
        val job = wakeRecord("job", "ScheduledJobStateChanged", START - SECOND, attributes = mapOf("attribution_node" to "$TARGET_UID", "state" to "1", "job_name" to digest))

        assertThat(revive(event, job).detail).isEqualTo("$TARGET.Worker")
        assertThat(revive(event.copy(component = null), job).detail).isEqualTo(digest)
        val named = job.copy(attributes = """{"attribution_node":"$TARGET_UID","state":"1","job_name":"$TARGET/.ScheduledService"}""")
        assertThat(revive(event.copy(component = null), named).pathKey).isEqualTo("JOB|android|$TARGET|$TARGET.ScheduledService")
    }

    @Test fun givenGrants_whenJudgingSystemBinds_thenRolesCoverPackagesAndBindingsCoverComponents() {
        val system = request("ServiceRequestEventReported", 1000, "android")
        fun role(name: String) = wakeRecord("role", "RoleHolder", START, source = "ROLES", action = name, attributes = mapOf("user" to "0"))
        assertThat(joined(system, role("android.app.role.DIALER")).justification).isEqualTo(Justification.USER_GRANTED_ROLE)
        assertThat(joined(system, role("android.app.role.SYSTEM_GALLERY")).trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        val otherService = wakeRecord("a11y", "SystemBinding", START, source = "ACCESSIBILITY", component = "$TARGET/.Reader", action = "ACCESSIBILITY", attributes = mapOf("user" to "0"))
        assertThat(joined(system, otherService).trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        val vpn = wakeRecord("vpn", "SystemBinding", START, source = "VPN", component = TARGET, action = "VPN", attributes = mapOf("user" to "0"))
        assertThat(joined(system, vpn).justification).isEqualTo(Justification.USER_GRANTED_ROLE)
    }

    @Test fun givenBindingSnapshots_whenTheOwnerGrantIsRevoked_thenWorkAndHistoricGrantsDoNotJustifyTheOwnerBind() {
        val system = request("ServiceRequestEventReported", 1000, "android")
        val owner = wakeRecord(
            "owner",
            "SystemBinding",
            START - SECOND,
            source = "ACCESSIBILITY",
            component = "$TARGET/.Wake",
            action = "ACCESSIBILITY",
            attributes = mapOf("user" to "0"),
        ).copy(boot = "7")
        val workUid = TARGET_UID + UIDS_PER_USER
        val work = owner.copy(id = "work", source = "ACCESSIBILITY/1", attributes = """{"user":"1"}""")
        val anotherComponent = owner.copy(id = "reader", component = "$TARGET/.Reader")
        val empty = owner.copy(id = "empty", elapsedNanos = START, wallMillis = owner.wallMillis - 500, targetPackage = null, component = null)

        assertThat(joined(system, owner, work, anotherComponent).justification).isEqualTo(Justification.USER_GRANTED_ROLE)
        assertThat(joined(system, empty, owner, work, anotherComponent).trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        assertThat(joined(system, owner, empty.copy(boot = "8", elapsedNanos = 1)).trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        val toWork = system.copy(targetUid = workUid)
        val workStart = coldStart.copy(targetUid = workUid)
        val relation = WakeRelation(toWork.id, workStart.id, "MATCHING_CALLER")
        assertThat(revive(workStart, toWork, owner, empty, work, relations = listOf(relation)).justification).isEqualTo(Justification.USER_GRANTED_ROLE)
    }

    @Test fun givenPackageRoleSnapshots_whenTheOwnerRoleIsRevoked_thenOnlyTheCurrentWorkGrantApplies() {
        val system = request("ServiceRequestEventReported", 1000, "android")
        val owner = wakeRecord("owner", "RoleHolder", START - SECOND, source = "ROLES", action = "android.app.role.DIALER", attributes = mapOf("user" to "0"))
        val work = owner.copy(id = "work", elapsedNanos = START, attributes = """{"user":"10"}""")
        val empty = work.copy(id = "empty", targetPackage = null, attributes = """{"user":"0"}""")

        assertThat(joined(system, owner).justification).isEqualTo(Justification.USER_GRANTED_ROLE)
        assertThat(joined(system, owner, work, empty).trigger).isEqualTo(ReviveTrigger.UNKNOWN)
    }

    @Test fun givenWorkAndUnscopedGlobalGrants_whenDetectingOwnerWakes_thenTheyDoNotJustifyTheWake() {
        val system = request("ServiceRequestEventReported", 1000, "android")
        val workWidget = wakeRecord("widget", "WidgetProvider", START, source = "WIDGETS", attributes = mapOf("user" to "10"))
        val companion = wakeRecord("companion", "CompanionAssociation", START, source = "COMPANIONS")
        val wallpaper = wakeRecord("wallpaper", "WallpaperBinding", START, source = "WALLPAPER", component = "$TARGET/.Wake")

        assertThat(joined(system, workWidget).trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        assertThat(joined(system, workWidget, companion, wallpaper).trigger).isEqualTo(ReviveTrigger.UNKNOWN)
    }

    @Test fun givenActiveDeviceAdmin_whenDetecting_thenTheGrantCoversOnlyItsComponent() {
        val system = request("ServiceRequestEventReported", 1000, "android")
        val admin = wakeRecord("admin", "DeviceAdmin", START, source = "DEVICE_POLICY", component = "$TARGET/.Wake", attributes = mapOf("user" to "0"))

        assertThat(joined(system, admin).justification).isEqualTo(Justification.USER_GRANTED_ROLE)
        assertThat(joined(system, admin.copy(component = "$TARGET/.Other")).justification).isEqualTo(Justification.NONE)
    }

    @Test fun givenNoCaller_whenDetecting_thenSnapshotsAndCorrelationsAreUsedBeforeUnknown() {
        val serviceStart = wakeRecord("info", "ApplicationStartInfo", START, source = "START_INFO", component = "$TARGET/.Ime", startType = 3, process = TARGET, attributes = mapOf("reason" to "SERVICE"))
        val ime = wakeRecord("ime", "SystemBinding", START, source = "INPUT", component = "$TARGET/.Ime", action = "INPUT", attributes = mapOf("user" to "0"))
        val bind = revive(*started(serviceStart), ime)
        assertThat(bind.trigger).isEqualTo(ReviveTrigger.SYSTEM_BIND)
        assertThat(bind.confidence).isEqualTo(Confidence.SNAPSHOT)
        assertThat(bind.justification).isEqualTo(Justification.USER_GRANTED_ROLE)
        val batch = wakeRecord("batch", "AlarmBatchDelivered", START - SECOND, targetUid = null, attributes = mapOf("uids" to "10001,$TARGET_UID"))
        val alarm = revive(coldStart, batch)
        assertThat(alarm.trigger).isEqualTo(ReviveTrigger.ALARM)
        assertThat(alarm.confidence).isEqualTo(Confidence.JOINED)
        val unknown = revive(coldStart)
        assertThat(unknown.trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        assertThat(unknown.justification).isEqualTo(Justification.NONE)
    }

    @Test fun givenScheduledRestart_whenTheSameComponentStarts_thenItIsAStickyRestart() {
        val schedule = wakeRecord("restart", "am_schedule_service_restart", START - 2 * SECOND, source = SYSTEM_WAKE_EVENTS, component = "$TARGET/.Wake", attributes = mapOf("delay_ms" to "1000"))
        val sticky = revive(coldStart.copy(attributes = """{"trigger_type":"4"}"""), schedule)
        assertThat(sticky.trigger).isEqualTo(ReviveTrigger.STICKY_RESTART)
        assertThat(sticky.actor).isEqualTo(TARGET)
        assertThat(revive(coldStart, schedule.copy(component = "$TARGET/.Other")).trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        assertThat(revive(coldStart, schedule.copy(elapsedNanos = START - 20 * SECOND)).trigger).isEqualTo(ReviveTrigger.UNKNOWN)
    }

    @Test fun givenWorkProfileRestartSchedule_whenTheOwnerStartsTheSameComponent_thenItIsNotSticky() {
        val schedule = wakeRecord(
            "restart",
            "am_schedule_service_restart",
            START - SECOND,
            source = SYSTEM_WAKE_EVENTS,
            component = "$TARGET/.Wake",
            attributes = mapOf("delay_ms" to "1000", "user" to "10"),
        )

        assertThat(revive(coldStart, schedule).trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        assertThat(revive(coldStart.copy(targetUid = TARGET_UID + 10 * UIDS_PER_USER), schedule).trigger).isEqualTo(ReviveTrigger.STICKY_RESTART)
    }

    @Test fun givenRecentDeaths_whenStarting_thenTheKillReasonIsRecorded() {
        // Kills name the process, which only the event log and start-info report for a start.
        val coldStart = wakeRecord("event", "am_proc_start", START, source = SYSTEM_WAKE_EVENTS, component = "{$TARGET/$TARGET.Wake}", process = TARGET)
        val kill = wakeRecord(
            "kill",
            "am_kill",
            START - 30 * SECOND,
            source = SYSTEM_WAKE_EVENTS,
            targetUid = null,
            process = TARGET,
            attributes = mapOf("user" to "0", "reason" to "empty #17"),
        )
        assertThat(revive(coldStart, kill).afterKill).isEqualTo("empty #17")
        assertThat(revive(coldStart, kill.copy(processName = "$TARGET:push")).afterKill).isNull()
        assertThat(revive(coldStart, kill.copy(attributes = """{"user":"10","reason":"empty #17"}""")).afterKill).isNull()
        fun killedBy(reason: String) = revive(coldStart, kill.copy(attributes = """{"user":"0","reason":"$reason"}""")).afterKill
        assertThat(killedBy("stop $TARGET due to from pid 4321 (com.android.settings)")).isEqualTo("stop $TARGET due to from pid 4321 (com.android.settings)")
        assertThat(killedBy("remove task")).isEqualTo("remove task")
        // Housekeeping kills do not make a restart notable.
        for (housekeeping in listOf("stop user 0 due to finish user", "change $TARGET", "stop $TARGET due to deletePackageX", "isolated not needed", "crash")) {
            assertThat(killedBy(housekeeping)).isNull()
        }
        val died = wakeRecord("died", "AppProcessDied", START - 50 * SECOND, process = TARGET, attributes = mapOf("reason" to "3"))
        assertThat(revive(coldStart, died).afterKill).isEqualTo("REASON_LOW_MEMORY")
        fun diedOf(reason: Int, sub: Int) = revive(coldStart, died.copy(attributes = """{"reason":"$reason","sub_reason":"$sub"}""")).afterKill
        assertThat(diedOf(13, 3)).isEqualTo("REASON_OTHER:SUBREASON_TOO_MANY_EMPTY")
        assertThat(diedOf(10, 22)).isEqualTo("REASON_USER_REQUESTED:SUBREASON_REMOVE_TASK")
        // A force stop code alone is also used for package removal and clearing data; isolated trimming is housekeeping.
        assertThat(diedOf(10, 21)).isNull()
        assertThat(diedOf(13, 17)).isNull()
        assertThat(diedOf(11, 0)).isNull()
    }

    @Test fun givenKillsBeforeAColdStart_whenDetecting_thenBackoffUpToTenMinutesStillCounts() {
        val startTime = 900 * SECOND
        val start = wakeRecord("event", "am_proc_start", startTime, source = SYSTEM_WAKE_EVENTS, process = TARGET)
        val kill = wakeRecord("kill", "am_kill", 0, source = SYSTEM_WAKE_EVENTS, process = TARGET, attributes = mapOf("user" to "0", "reason" to "empty #17"))
        val died = wakeRecord("died", "AppProcessDied", 0, process = TARGET, attributes = mapOf("reason" to "3"))
        for ((death, reason) in listOf(kill to "empty #17", died to "REASON_LOW_MEMORY")) {
            assertThat(revive(start, death.copy(elapsedNanos = startTime - 5 * 60 * SECOND)).afterKill).isEqualTo(reason)
            assertThat(revive(start, death.copy(elapsedNanos = startTime - 10 * 60 * SECOND)).afterKill).isEqualTo(reason)
            assertThat(revive(start, death.copy(elapsedNanos = startTime - 10 * 60 * SECOND - 1)).afterKill).isNull()
            assertThat(revive(start, death.copy(elapsedNanos = startTime)).afterKill).isNull()
        }
    }

    @Test fun givenWhatFollowsTheStart_whenJudging_thenVisibleAndProductiveStartsAreJustified() {
        val shown = wakeRecord("shown", "AppStartOccurred", START + 2 * SECOND)
        assertThat(revive(coldStart, shown).justification).isEqualTo(Justification.USER_INITIATED)
        fun notification(event: String, offset: Long) = wakeRecord("n", "NotificationReported", START + offset, attributes = mapOf("event_id" to event))
        assertThat(revive(coldStart, notification("162", 9 * SECOND)).justification).isEqualTo(Justification.PRODUCTIVE)
        assertThat(revive(coldStart, notification("163", SECOND)).justification).isEqualTo(Justification.PRODUCTIVE)
        assertThat(revive(coldStart, notification("908", SECOND)).justification).isEqualTo(Justification.NONE)
        assertThat(revive(coldStart, notification("162", 11 * SECOND)).justification).isEqualTo(Justification.NONE)
        val activity = wakeRecord(
            "info",
            "ApplicationStartInfo",
            START,
            source = "START_INFO",
            startType = 3,
            process = TARGET,
            attributes = mapOf("reason" to "START ACTIVITY"),
        )
        assertThat(revive(*started(activity)).justification).isEqualTo(Justification.USER_INITIATED)
    }

    @Test fun givenHoldCauses_whenAttributing_thenTotalsNeverExceedProcstats() {
        val period = WakeHoldPeriod("p", WALL, WALL + 4_000_000, 0)
        val process = WakeHoldProcess("p", TARGET, TARGET_UID, TARGET, 3_000, 1_000)
        val enter = wakeRecord("enter", "ForegroundServiceStateChanged", 0, component = "$TARGET/.Music", attributes = mapOf("state" to "1", "fgs_types" to "${1 or 8}"))
        val exit = enter.copy(id = "exit", elapsedNanos = 2_500 * 1_000_000L, wallMillis = WALL + 2_500, attributes = """{"state":"2"}""")
        val peer = WakeHoldAssociation("p", TARGET, TARGET_UID, ".Sync", PEER, PEER_UID, 5_000, 1_000)
        val system = WakeHoldAssociation("p", TARGET, TARGET_UID, "$TARGET.Ime", "system", 1000, 800, 10)
        val holds = WakeDetection.detect(evidence(listOf(enter, exit), periods = listOf(period), processes = listOf(process), associations = listOf(peer, system)))
            .filter { it.finding.hold != null }.associateBy { it.finding.hold }
        assertThat(holds.getValue(HoldCause.FOREGROUND_SERVICE).finding.durationNanos).isEqualTo(2_500_000_000L)
        assertThat(holds.getValue(HoldCause.FOREGROUND_SERVICE).finding.actor).isEqualTo("dataSync,location")
        assertThat(holds.getValue(HoldCause.FOREGROUND_SERVICE).finding.confidence).isEqualTo(Confidence.JOINED)
        assertThat(holds.getValue(HoldCause.BOUND_BY_PEER).finding.durationNanos).isEqualTo(1_000_000_000L)
        assertThat(holds.getValue(HoldCause.BOUND_BY_PEER).finding.pathKey).isEqualTo("HOLD_BOUND_BY_PEER|$PEER|$TARGET|$TARGET.Sync")
        // A foreground service always shows its notification; other causes are unjustified.
        assertThat(holds.getValue(HoldCause.FOREGROUND_SERVICE).finding.justification).isEqualTo(Justification.PRODUCTIVE)
        assertThat(holds.getValue(HoldCause.FOREGROUND_SERVICE).finding.pathKey).isEqualTo("HOLD_FOREGROUND_SERVICE|dataSync,location|$TARGET|$TARGET.Music")
        assertThat(holds.getValue(HoldCause.BOUND_BY_PEER).finding.justification).isEqualTo(Justification.NONE)
        // Only 500 ms remain for the system binding, and nothing is left unexplained.
        assertThat(holds.getValue(HoldCause.BOUND_BY_SYSTEM).finding.durationNanos).isEqualTo(500_000_000L)
        assertThat(holds).doesNotContainKey(HoldCause.UNKNOWN)
        assertThat(holds.values.sumOf { it.finding.durationNanos }).isEqualTo(4_000_000_000L)
        // Each cause takes the package's screen-off share.
        assertThat(holds.getValue(HoldCause.BOUND_BY_PEER).screenOffNanos).isEqualTo(750_000_000L)
        assertThat(holds.values.map { it.finding.startNanos }.distinct()).containsExactly(WALL * 1_000_000)

        val unexplained = WakeDetection.detect(evidence(emptyList(), periods = listOf(period), processes = listOf(process), associations = listOf(peer)))
            .associateBy { it.finding.hold }
        assertThat(unexplained.getValue(HoldCause.UNKNOWN).finding.durationNanos).isEqualTo(3_000_000_000L)
        assertThat(unexplained.getValue(HoldCause.UNKNOWN).finding.confidence).isEqualTo(Confidence.SNAPSHOT)
    }

    @Test fun givenForegroundServiceState_whenStatsdMissedTheStart_thenProcstatsTimeIsAForegroundService() {
        val period = WakeHoldPeriod("p", WALL, WALL + 10_000, 0)
        // 6 s held, 4 s of it in the foreground service state, 3 s of that with the screen off.
        val process = WakeHoldProcess("p", TARGET, TARGET_UID, TARGET, 5_000, 1_000, 3_000, 1_000)
        val holds = WakeDetection.detect(evidence(emptyList(), periods = listOf(period), processes = listOf(process))).associateBy { it.finding.hold }
        val service = holds.getValue(HoldCause.FOREGROUND_SERVICE)
        assertThat(service.finding.durationNanos).isEqualTo(4_000_000_000L)
        assertThat(service.screenOffNanos).isEqualTo(3_000_000_000L)
        assertThat(service.finding.justification).isEqualTo(Justification.PRODUCTIVE)
        assertThat(service.finding.actor).isNull()
        assertThat(holds.getValue(HoldCause.UNKNOWN).finding.durationNanos).isEqualTo(2_000_000_000L)
        assertThat(holds.getValue(HoldCause.UNKNOWN).screenOffNanos).isEqualTo(2_000_000_000L)
        // statsd names the type and component of the part it saw; procstats keeps the total.
        val enter = wakeRecord("enter", "ForegroundServiceStateChanged", 0, component = "$TARGET/.Music", attributes = mapOf("state" to "1", "fgs_types" to "2"))
        val exit = enter.copy(id = "exit", elapsedNanos = 1_000 * 1_000_000L, wallMillis = WALL + 1_000, attributes = """{"state":"2"}""")
        val named = WakeDetection.detect(evidence(listOf(enter, exit), periods = listOf(period), processes = listOf(process)))
            .filter { it.finding.hold == HoldCause.FOREGROUND_SERVICE }.associateBy { it.finding.actor }
        assertThat(named.getValue("mediaPlayback").finding.durationNanos).isEqualTo(1_000_000_000L)
        assertThat(named.getValue(null).finding.durationNanos).isEqualTo(3_000_000_000L)
    }

    @Test fun givenAppThatMayNotNotify_whenItsForegroundServiceRuns_thenOnlyAPerceptibleTypeIsProductive() {
        val period = WakeHoldPeriod("p", WALL, WALL + 10_000, 0)
        val process = WakeHoldProcess("p", TARGET, TARGET_UID, TARGET, 5_000, 1_000, 3_000, 1_000)
        fun off(id: String, millis: Long, app: String = "$TARGET/$TARGET_UID") = wakeRecord(id, "NotificationsOff", millis * 1_000_000, source = "NOTIFICATIONS", targetUid = null, targetPackage = null, attributes = mapOf("apps" to app))
        fun service(vararg records: WakeRecord) = WakeDetection.detect(evidence(records.toList(), periods = listOf(period), processes = listOf(process)))
            .filter { it.finding.hold == HoldCause.FOREGROUND_SERVICE }.maxBy { it.finding.actor != null }.finding.justification

        assertThat(service(off("a", 1_000), off("b", 9_000))).isEqualTo(Justification.NONE)
        // A later snapshot at which this app could notify, and a package sharing its uid that could not.
        assertThat(service(off("a", 1_000), off("b", 9_000, ""))).isEqualTo(Justification.PRODUCTIVE)
        assertThat(service(off("a", 1_000, "$PEER/$TARGET_UID"))).isEqualTo(Justification.PRODUCTIVE)
        // A snapshot outside the period says nothing about it.
        assertThat(service(off("a", 11_000))).isEqualTo(Justification.PRODUCTIVE)
        // Playback is heard without a notification; a sync is not noticed. The named service is the one judged.
        fun enter(types: Int) = wakeRecord("enter", "ForegroundServiceStateChanged", 0, component = ".Service", attributes = mapOf("state" to "1", "fgs_types" to types.toString()))
        assertThat(service(off("a", 1_000), enter(2))).isEqualTo(Justification.PRODUCTIVE)
        assertThat(service(off("a", 1_000), enter(1))).isEqualTo(Justification.NONE)
    }

    @Test fun givenOverlayAndOtherHoldEvidence_whenAttributing_thenTheOverlayExplainsOnlyTheRemainder() {
        val period = WakeHoldPeriod("p", WALL, WALL + 10_000, 0)
        val process = WakeHoldProcess("p", TARGET, TARGET_UID, TARGET, 4_000, 0, 1_000, 0)
        val association = WakeHoldAssociation("p", TARGET, TARGET_UID, ".Service", PEER, PEER_UID, 1_000, 1_000)
        val overlay = snapshot(WAKE_OVERLAY_WINDOWS, 10_000, "packages" to "${TARGET}x,$TARGET")
        fun holds(record: WakeRecord) = WakeDetection.detect(evidence(listOf(record), periods = listOf(period), processes = listOf(process), associations = listOf(association)), ZoneOffset.UTC)
            .associateBy { it.finding.hold }

        val holds = holds(overlay)
        assertThat(holds.keys).containsExactly(HoldCause.FOREGROUND_SERVICE, HoldCause.BOUND_BY_PEER, HoldCause.OVERLAY_WINDOW)
        val held = holds.getValue(HoldCause.OVERLAY_WINDOW)
        assertThat(held.finding.justification).isEqualTo(Justification.NONE)
        assertThat(held.finding.confidence).isEqualTo(Confidence.SNAPSHOT)
        assertThat(held.finding.durationNanos).isEqualTo(2 * SECOND)
        assertThat(held.screenOffNanos).isEqualTo(2 * SECOND)
        assertThat(holds(snapshot(WAKE_OVERLAY_WINDOWS, 0, "packages" to "$TARGET/$TARGET_UID"))).containsKey(HoldCause.OVERLAY_WINDOW)
        assertThat(holds(snapshot(WAKE_OVERLAY_WINDOWS, 0, "packages" to "$TARGET/${TARGET_UID + 10 * UIDS_PER_USER}")).keys)
            .containsExactly(HoldCause.FOREGROUND_SERVICE, HoldCause.BOUND_BY_PEER, HoldCause.UNKNOWN)
        assertThat(holds(overlay.copy(wallMillis = period.endMillis + 1))).containsKey(HoldCause.UNKNOWN)
        assertThat(holds(snapshot(WAKE_OVERLAY_WINDOWS, 0, "packages" to "${TARGET}x"))).containsKey(HoldCause.UNKNOWN)
    }

    @Test fun givenHiddenMediaService_whenJudging_thenItRequiresPlaybackEvidenceBeforeCallingItSilent() {
        val period = WakeHoldPeriod("p", WALL, WALL + 10_000, 0)
        val process = WakeHoldProcess("p", TARGET, TARGET_UID, TARGET, 8_000, 0, 8_000, 0)
        val hidden = snapshot(WAKE_NOTIFICATIONS_OFF, 1_000, "apps" to "$TARGET/$TARGET_UID")
        val quiet = snapshot(WAKE_AUDIO_PLAYBACK, 2_000, "uids" to "")
        val playing = snapshot(WAKE_AUDIO_PLAYBACK, 3_000, "uids" to "$PEER_UID,$TARGET_UID")
        val otherProfile = snapshot(WAKE_AUDIO_PLAYBACK, 4_000, "uids" to (TARGET_UID + UIDS_PER_USER).toString())
        fun service(types: Int, vararg records: WakeRecord) = serviceHold(period, process, types, hidden, *records).finding.justification

        assertThat(service(2)).isEqualTo(Justification.PRODUCTIVE)
        assertThat(service(2, quiet)).isEqualTo(Justification.NONE)
        assertThat(service(2, quiet, otherProfile)).isEqualTo(Justification.NONE)
        assertThat(service(2, quiet, playing)).isEqualTo(Justification.PRODUCTIVE)
        assertThat(service(2, quiet.copy(wallMillis = period.startMillis - 1))).isEqualTo(Justification.PRODUCTIVE)
        assertThat(service(1 or 2, quiet)).isEqualTo(Justification.NONE)
        assertThat(service(2 or 4, quiet)).isEqualTo(Justification.PRODUCTIVE)
    }

    @Test fun givenVisibleNotification_whenJudgingPersistentFgs_thenDurationTypeAndSpecialAccessProtectLegitimateServices() {
        val threshold = 30 * 60 * 1_000L
        val period = WakeHoldPeriod("p", WALL, WALL + 2 * threshold, 0)
        val process = WakeHoldProcess("p", TARGET, TARGET_UID, TARGET, threshold + 1, threshold, threshold, threshold)
        val allowed = snapshot(WAKE_NOTIFICATIONS_OFF, 1_000, "apps" to "")
        val quiet = snapshot(WAKE_AUDIO_PLAYBACK, 2_000, "uids" to "")
        fun service(types: Int, fgsOff: Long = threshold, vararg records: WakeRecord) = serviceHold(period, process.copy(fgsScreenOffMillis = fgsOff), types, allowed, quiet, *records).finding.justification

        for (type in listOf(0, 1 shl 30)) {
            assertThat(service(type)).isEqualTo(Justification.NONE)
            assertThat(service(type, threshold - 1)).isEqualTo(Justification.PRODUCTIVE)
        }
        for (type in listOf(1 shl 0, 1 shl 1, 1 shl 3, 1 shl 4, 1 shl 10, (1 shl 30) or (1 shl 8))) {
            assertThat(service(type)).isEqualTo(Justification.PRODUCTIVE)
        }
        val granted = wakeRecord("vpn", "SystemBinding", 0, component = TARGET, action = "VPN", attributes = mapOf("user" to "0"))
        val bound = wakeRecord("ime", "SystemBinding", 0, component = "$TARGET/.Service", action = "INPUT", attributes = mapOf("user" to "0"))
        assertThat(service(1 shl 30, threshold, granted)).isEqualTo(Justification.PRODUCTIVE)
        assertThat(service(1 shl 30, threshold, bound)).isEqualTo(Justification.PRODUCTIVE)
        assertThat(service(1 shl 30, threshold, bound.copy(component = "$TARGET/.Other"))).isEqualTo(Justification.NONE)
    }

    @Test fun givenPersistentDataSyncFgs_whenJudging_thenOnlyTheHiddenServiceIsUnjustified() {
        val threshold = 30 * 60 * 1_000L
        val period = WakeHoldPeriod("p", WALL, WALL + 2 * threshold, 0)
        val process = WakeHoldProcess("p", TARGET, TARGET_UID, TARGET, threshold + 1, threshold, threshold, threshold)
        val visible = snapshot(WAKE_NOTIFICATIONS_OFF, 1_000, "apps" to "")
        val hidden = snapshot(WAKE_NOTIFICATIONS_OFF, 1_000, "apps" to "$TARGET/$TARGET_UID")

        assertThat(serviceHold(period, process, 1, visible).finding.justification).isEqualTo(Justification.PRODUCTIVE)
        assertThat(serviceHold(period, process, 1, hidden).finding.justification).isEqualTo(Justification.NONE)
    }

    private fun snapshot(kind: String, millis: Long, attribute: Pair<String, String>) = wakeRecord("$kind-$millis", kind, millis * 1_000_000, targetUid = null, targetPackage = null, attributes = mapOf(attribute))

    private fun serviceHold(period: WakeHoldPeriod, process: WakeHoldProcess, types: Int, vararg records: WakeRecord): Detected {
        val service = wakeRecord("enter", "ForegroundServiceStateChanged", 0, component = "$TARGET/.Service", attributes = mapOf("state" to "1", "fgs_types" to types.toString()))
        val exit = service.copy(id = "exit", elapsedNanos = (period.endMillis - WALL) * 1_000_000, wallMillis = period.endMillis, attributes = """{"state":"2"}""")
        return WakeDetection.detect(evidence(listOf(service, exit, *records), periods = listOf(period), processes = listOf(process)), ZoneOffset.UTC)
            .single { it.finding.hold == HoldCause.FOREGROUND_SERVICE }
    }

    @Test fun givenPeriodCrossingMidnight_whenDetecting_thenEachDayGetsItsShareOfTheHold() {
        val midnight = WakeDetection.dayStartMillis(WakeDetection.day(WALL * 1_000_000, ZoneOffset.UTC) + 1, ZoneOffset.UTC)
        // 23:30 to 02:30, held for two hours of it with the screen off for one.
        val period = WakeHoldPeriod("p", midnight - 1_800_000, midnight + 9_000_000, 0)
        val process = WakeHoldProcess("p", TARGET, TARGET_UID, TARGET, 3_600_000, 3_600_000)
        val detected = WakeDetection.detect(evidence(emptyList(), periods = listOf(period), processes = listOf(process)), ZoneOffset.UTC)
            .filter { it.finding.hold != null }.sortedBy { it.finding.startNanos }
        assertThat(detected.map { it.finding.startNanos / 1_000_000 }).containsExactly(midnight - 1_800_000, midnight).inOrder()
        assertThat(detected.map { it.finding.durationNanos / 1_000_000 }).containsExactly(1_200_000L, 6_000_000L).inOrder()
        assertThat(detected.map { it.screenOffNanos / 1_000_000 }).containsExactly(600_000L, 3_000_000L).inOrder()
        val verdicts = WakeDetection.verdicts(detected, ZoneOffset.UTC).sortedBy { it.day }
        assertThat(verdicts.map { it.screenOffHoldNoneNanos / 1_000_000 }).containsExactly(600_000L, 3_000_000L).inOrder()
    }

    @Test fun givenOwnProcessesHoldingEachOther_whenAttributing_thenTheAppHoldsItself() {
        // The messaging shape: the system keeps ":rcs", and ":rcs" keeps the main process through a provider.
        val period = WakeHoldPeriod("p", WALL, WALL + 10_000, 0)
        val processes = listOf(WakeHoldProcess("p", TARGET, TARGET_UID, TARGET, 0, 4_000), WakeHoldProcess("p", TARGET, TARGET_UID, "$TARGET:rcs", 0, 10_000))
        val system = WakeHoldAssociation("p", TARGET, TARGET_UID, ".Binding", "system", 1000, 10_000, 100)
        val own = WakeHoldAssociation("p", TARGET, TARGET_UID, ".Storage", "$TARGET:rcs", TARGET_UID, 4_000, 1_000)
        val snapshot = wakeRecord("held", "HeldConnection", 0, source = "SERVICES", callerUid = 1000, component = "$TARGET/.Binding", attributes = mapOf("host_process" to "$TARGET:rcs"))
        val role = wakeRecord("role", "RoleHolder", 0, source = "ROLES", action = "android.app.role.SMS", attributes = mapOf("user" to "0"))
        fun holds(records: List<WakeRecord>, vararg associations: WakeHoldAssociation) = WakeDetection.detect(evidence(records, periods = listOf(period), processes = processes, associations = associations.toList()))
            .filter { it.finding.hold != null }.associateBy { it.finding.hold }

        // Bound time counts for the app's own processes, and nothing is left unexplained.
        val plain = holds(listOf(snapshot), system, own)
        val self = plain.getValue(HoldCause.BOUND_BY_PEER).finding
        assertThat(self.actor).isEqualTo(TARGET)
        assertThat(self.detail).isEqualTo("$TARGET.Storage")
        assertThat(self.durationNanos).isEqualTo(4_000_000_000L)
        assertThat(self.justification).isEqualTo(Justification.NONE)
        assertThat(plain).doesNotContainKey(HoldCause.UNKNOWN)
        // The holding process is kept by a binding the user granted, so the hold is granted too.
        val granted = holds(listOf(snapshot, role), system, own)
        assertThat(granted.getValue(HoldCause.BOUND_BY_SYSTEM).finding.justification).isEqualTo(Justification.USER_GRANTED_ROLE)
        assertThat(granted.getValue(HoldCause.BOUND_BY_PEER).finding.justification).isEqualTo(Justification.USER_GRANTED_ROLE)
        // Not when the granted binding keeps another process, or when no snapshot tells where it runs.
        val elsewhere = snapshot.copy(attributes = """{"host_process":"$TARGET"}""")
        assertThat(holds(listOf(elsewhere, role), system, own).getValue(HoldCause.BOUND_BY_PEER).finding.justification).isEqualTo(Justification.NONE)
        assertThat(holds(listOf(role), system, own).getValue(HoldCause.BOUND_BY_PEER).finding.justification).isEqualTo(Justification.NONE)
        // Outside causes come first, however long the app's own binding lasted.
        val longer = holds(listOf(snapshot), system, own.copy(boundMillis = 50_000))
        assertThat(longer.getValue(HoldCause.BOUND_BY_SYSTEM).finding.durationNanos).isEqualTo(10_000_000_000L)
        assertThat(longer.getValue(HoldCause.BOUND_BY_PEER).finding.durationNanos).isEqualTo(4_000_000_000L)
    }

    @Test fun givenSpecialAccessAndProviders_whenAttributingHolds_thenRoleAndProviderCausesApply() {
        val period = WakeHoldPeriod("p", WALL, WALL + 10_000, 0)
        val process = WakeHoldProcess("p", TARGET, TARGET_UID, TARGET, 0, 10_000)
        val ime = wakeRecord("ime", "SystemBinding", 0, source = "INPUT", component = "$TARGET/.Ime", action = "INPUT", attributes = mapOf("user" to "0"))
        val provider = wakeRecord("held", "HeldConnection", 0, source = "PROVIDERS", component = "$TARGET/.Data")
        val holds = WakeDetection.detect(
            evidence(
                listOf(ime, provider),
                periods = listOf(period),
                processes = listOf(process),
                associations = listOf(
                    WakeHoldAssociation("p", TARGET, TARGET_UID, ".Ime", "system", 1000, 4_000, 5),
                    WakeHoldAssociation("p", TARGET, TARGET_UID, ".Data", PEER, PEER_UID, 9_999, 3_000),
                    WakeHoldAssociation("p", TARGET, TARGET_UID, ".Self", TARGET, TARGET_UID + UIDS_PER_USER, 3_000, 3_000),
                ),
            ),
        ).associateBy { it.finding.hold }
        assertThat(holds.getValue(HoldCause.BOUND_BY_SYSTEM).finding.justification).isEqualTo(Justification.USER_GRANTED_ROLE)
        assertThat(holds.getValue(HoldCause.PROVIDER_CLIENT).finding.actor).isEqualTo(PEER)
        assertThat(holds.getValue(HoldCause.PROVIDER_CLIENT).finding.justification).isEqualTo(Justification.NONE)
        // The same package in another user is not an external cause.
        assertThat(holds.getValue(HoldCause.UNKNOWN).finding.durationNanos).isEqualTo(3_000_000_000L)
    }
}
