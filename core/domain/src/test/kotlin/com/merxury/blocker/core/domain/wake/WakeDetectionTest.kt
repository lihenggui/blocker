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

internal class WakeDetectionTest : WakeDetectionTestFixture() {
    @Test fun givenOneStartFromEverySource_whenDetecting_thenItIsOneReviveWithTheBestRecord() {
        val info = wakeRecord(
            "info",
            "ApplicationStartInfo",
            START - 400_000_000,
            source = "START_INFO",
            component = "$TARGET/.Wake",
            startType = 3,
            process = TARGET,
            attributes = mapOf("reason" to "ALARM"),
        )
        val event = wakeRecord("event", "am_proc_start", START + 200_000_000, source = SYSTEM_WAKE_EVENTS, component = "{$TARGET/$TARGET.Wake}", process = TARGET, pid = 777)
        val stats = coldStart.copy(pid = 777)
        val findings = WakeDetection.findings(evidence(listOf(info, stats, event)))
        val only = findings.single()
        assertThat(only.trigger).isEqualTo(ReviveTrigger.ALARM)
        assertThat(only.confidence).isEqualTo(Confidence.DIRECT)
        // The event log times the start exactly; start-info only describes it.
        assertThat(only.startNanos).isEqualTo((WALL * 1_000_000) + START + 200_000_000)
        // start-info alone is a revive only when its time is known within the merge window.
        assertThat(WakeDetection.findings(evidence(listOf(info.copy(attributes = """{"reason":"ALARM","clock":"uptime","sleep_ns":"${20 * SECOND}"}"""))))).isEmpty()
        val alone = WakeDetection.findings(evidence(listOf(info.copy(attributes = """{"reason":"ALARM","clock":"uptime","sleep_ns":"0"}""")))).single()
        assertThat(alone.trigger).isEqualTo(ReviveTrigger.ALARM)
        assertThat(alone.startNanos).isEqualTo(WALL * 1_000_000 + START - 400_000_000)
        // A second start-info record of the same moment does not become a second revive.
        val twin = info.copy(id = "twin", elapsedNanos = START - 390_000_000, attributes = """{"reason":"CONTENT PROVIDER","clock":"uptime","sleep_ns":"0"}""")
        assertThat(WakeDetection.findings(evidence(listOf(info.copy(attributes = """{"reason":"ALARM","clock":"uptime","sleep_ns":"0"}"""), twin)))).hasSize(1)
        // A monotonic record may lie up to its unseen deep sleep after its recorded time. It describes the one
        // start in that span, and none if the span holds several starts of the process.
        val slept = info.copy(attributes = """{"reason":"ALARM","clock":"uptime","sleep_ns":"${20 * SECOND}"}""")
        val after = event.copy(id = "after", elapsedNanos = START + 15 * SECOND, wallMillis = event.wallMillis + 15_000, pid = 900)
        assertThat(WakeDetection.findings(evidence(listOf(slept, after))).single().trigger).isEqualTo(ReviveTrigger.ALARM)
        assertThat(WakeDetection.findings(evidence(listOf(slept, event, after))).map { it.trigger }).containsExactly(ReviveTrigger.UNKNOWN, ReviveTrigger.UNKNOWN)
        // statsd and the event log name the same pid; different pids are different starts.
        assertThat(WakeDetection.findings(evidence(listOf(stats.copy(pid = 778), event)))).hasSize(2)
        // A second start of the same process outside the merge window stays separate.
        val later = event.copy(id = "later", elapsedNanos = START + 10 * SECOND, wallMillis = event.wallMillis + 10_000)
        assertThat(WakeDetection.findings(evidence(listOf(info, stats, event, later)))).hasSize(2)
        // System processes are not revives.
        assertThat(WakeDetection.findings(evidence(listOf(coldStart.copy(targetUid = 1000))))).isEmpty()
    }

    @Test fun givenIsolatedAndSandboxProcesses_whenTheyStart_thenTheyAreNotRevivesOfTheirHost() {
        // The am_proc_start shape of a Chrome renderer: [user, pid, uid, process, hosting type, component].
        val renderer = wakeRecord(
            "renderer",
            "am_proc_start",
            START,
            source = SYSTEM_WAKE_EVENTS,
            targetUid = 90000,
            targetPackage = null,
            component = "{com.android.chrome/org.chromium.content.app.SandboxedProcessService0}",
            process = "com.android.chrome:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0",
            pid = 4321,
        )
        val isolated = renderer.copy(id = "isolated", targetUid = 1099123, processName = "com.google.android.webview:sandboxed_process0")
        val sandbox = renderer.copy(id = "sdk", targetUid = 20145, processName = "com.google.android.sdksandbox")
        assertThat(WakeDetection.findings(evidence(listOf(renderer, isolated, sandbox)))).isEmpty()
        assertThat(WakeDetection.findings(evidence(listOf(renderer.copy(targetUid = 10145))))).hasSize(1)
        // ProcessStartTime files isolated starts under the host uid with the package as process name (as seen on
        // the emulator): zygote-hosted ones carry hosting type 14, the others share their pid with the event log's
        // isolated start.
        val zygote = coldStart.copy(
            id = "zygote",
            targetUid = 10145,
            targetPackage = "com.android.chrome",
            processName = null,
            component = renderer.component,
            pid = 4321,
            attributes = """{"hosting_type_id":"14"}""",
        )
        val service = zygote.copy(id = "isolated-service", attributes = """{"hosting_type_id":"11"}""")
        assertThat(WakeDetection.findings(evidence(listOf(zygote)))).isEmpty()
        assertThat(WakeDetection.findings(evidence(listOf(service, renderer)))).isEmpty()
        assertThat(WakeDetection.findings(evidence(listOf(service, renderer.copy(pid = 4322))))).hasSize(1)
        assertThat(WakeDetection.findings(evidence(listOf(service)))).hasSize(1)
        val hold = WakeHoldProcess("p", "com.android.chrome", 1099123, "com.android.chrome:sandboxed_process0", 0, 5_000)
        val period = WakeHoldPeriod("p", WALL, WALL + 10_000, 0)
        assertThat(WakeDetection.findings(evidence(emptyList(), periods = listOf(period), processes = listOf(hold)))).isEmpty()
    }

    @Test fun givenSharedUid_whenResolvingTheTarget_thenOnlyAComponentOrRequestNamesThePackage() {
        val shared = mapOf(10091 to setOf("com.android.providers.contacts", "com.android.providers.blockednumber"))
        fun findings(vararg records: WakeRecord) = WakeDetection.findings(WakeDetectionEvidence(records.toList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), shared))
        val event = wakeRecord(
            "acore", "am_proc_start", START, source = SYSTEM_WAKE_EVENTS, targetUid = 10091, targetPackage = null,
            component = "{com.android.providers.contacts/com.android.providers.contacts.ContactsProvider2}", process = "android.process.acore",
            attributes = mapOf("hosting_type" to "content provider"),
        )
        assertThat(findings(event).single().target).isEqualTo("com.android.providers.contacts")
        // start-info lists the start under the package that first created the process; that alone does not name it.
        val info = wakeRecord(
            "info", "ApplicationStartInfo", START, source = "START_INFO", targetUid = 10091, targetPackage = "com.android.providers.blockednumber",
            startType = 3, process = "android.process.acore", attributes = mapOf("reason" to "CONTENT PROVIDER"),
        )
        assertThat(findings(info)).isEmpty()
        assertThat(findings(info, event).single().target).isEqualTo("com.android.providers.contacts")
        // A process name is never a package, even when the uid is unknown.
        assertThat(findings(event.copy(component = null, targetUid = 10092)).map { it.target }).doesNotContain("android.process.acore")
        assertThat(findings(event.copy(component = null))).isEmpty()
    }

    @Test fun givenActivityHostedStarts_whenDetecting_thenTheyAreUserInitiatedActivityStarts() {
        for (type in listOf("activity", "next-activity", "next-top-activity", "top-activity")) {
            val start = wakeRecord(
                "event",
                "am_proc_start",
                START,
                source = SYSTEM_WAKE_EVENTS,
                component = "{$TARGET/$TARGET.Main}",
                process = TARGET,
                attributes = mapOf("hosting_type" to type),
            )
            val finding = revive(start)
            assertThat(finding.trigger).isEqualTo(ReviveTrigger.PEER_ACTIVITY)
            assertThat(finding.justification).isEqualTo(Justification.USER_INITIATED)
            assertThat(finding.actor).isNull()
        }
        // ProcessStartTime reports next-top-activity as hosting_type_id 9 (checked on an API 35 emulator).
        val stats = coldStart.copy(component = "{$TARGET/$TARGET.Main}", attributes = """{"hosting_type_id":"9"}""")
        val launcher = wakeRecord("launch", "AppStartOccurred", START + SECOND, callerPackage = "launcher.app", component = "$TARGET.Main", startType = 3)
        val launched = revive(stats, launcher, relations = listOf(WakeRelation("launch", stats.id, "MATCHING_COMPONENT")))
        assertThat(launched.trigger).isEqualTo(ReviveTrigger.PEER_ACTIVITY)
        assertThat(launched.actor).isEqualTo("launcher.app")
        assertThat(launched.justification).isEqualTo(Justification.USER_INITIATED)
        val info = wakeRecord("info", "ApplicationStartInfo", START, source = "START_INFO", startType = 3, process = TARGET, attributes = mapOf("reason" to "START ACTIVITY"))
        assertThat(revive(*started(info)).justification).isEqualTo(Justification.USER_INITIATED)
    }

    @Test fun givenScreenOffActivity_whenDetecting_thenItIsAnUnjustifiedSelfStart() {
        val shown = wakeRecord("shown", "AppStartOccurred", START + SECOND, component = "$TARGET.Wake")
        val records = listOf(activityStart, shown, screenState(0))
        val detected = WakeDetection.detect(evidence(records))
        val finding = detected.single().finding

        assertThat(finding.trigger).isEqualTo(ReviveTrigger.PEER_ACTIVITY)
        assertThat(finding.actor).isEqualTo(TARGET)
        assertThat(finding.detail).isEqualTo("$TARGET.Wake")
        assertThat(finding.confidence).isEqualTo(Confidence.DIRECT)
        assertThat(finding.actorInBackground).isTrue()
        assertThat(finding.justification).isEqualTo(Justification.NONE)
        val verdict = WakeDetection.verdicts(detected, ZoneOffset.UTC).single()
        assertThat(verdict.noneRevives).isEqualTo(1)
        assertThat(verdict.peerBackgroundNoneRevives).isEqualTo(0)
        assertThat(verdict.wakesOthers).isEqualTo(0)
    }

    @Test fun givenAppBalLaunch_whenDetecting_thenTheEffectiveCallerIsAnUnjustifiedPeer() {
        val shown = wakeRecord("shown", "AppStartOccurred", START + SECOND, component = "$TARGET.Wake", startType = 3)
        val records = listOf(activityStart, shown, screenState(1), bal(PEER_UID, 10033, component = "ComponentInfo{$TARGET/$TARGET.Wake}"))
        val detected = WakeDetection.detect(evidence(records, listOf(WakeRelation(shown.id, activityStart.id, "MATCHING_COMPONENT"))))
        val finding = detected.single().finding

        assertThat(finding.trigger).isEqualTo(ReviveTrigger.PEER_ACTIVITY)
        assertThat(finding.actor).isEqualTo(PEER)
        assertThat(finding.detail).isEqualTo("$TARGET.Wake")
        assertThat(finding.confidence).isEqualTo(Confidence.JOINED)
        assertThat(finding.actorInBackground).isTrue()
        assertThat(finding.justification).isEqualTo(Justification.NONE)
        val verdicts = WakeDetection.verdicts(detected, ZoneOffset.UTC).associateBy { it.target }
        assertThat(verdicts.getValue(TARGET).peerBackgroundNoneRevives).isEqualTo(1)
        assertThat(verdicts.getValue(PEER).wakesOthers).isEqualTo(1)
    }

    @Test fun givenComponentlessBal_whenAJoinedCallerMatches_thenTheActivityIsAnUnjustifiedRevive() {
        val allowed = bal(PEER_UID, 10033, elapsed = START - 3 * SECOND, component = null)
        val shown = request("AppStartOccurred", 10033)
        val relation = WakeRelation(shown.id, activityStart.id, "MATCHING_COMPONENT")

        for (request in listOf(shown, shown.copy(callerUid = PEER_UID))) {
            val finding = revive(activityStart, screenState(1), allowed, request, relations = listOf(relation))
            assertThat(finding.actor).isEqualTo(PEER)
            assertThat(finding.justification).isEqualTo(Justification.NONE)
        }
        val packageOnly = shown.copy(callerUid = null, callerPackage = PEER)
        val packageMatch = revive(activityStart, screenState(1), allowed.copy(elapsedNanos = START - SECOND), packageOnly, relations = listOf(relation))
        assertThat(packageMatch.actor).isEqualTo(PEER)
        assertThat(packageMatch.justification).isEqualTo(Justification.NONE)
        val unrelated = shown.copy(callerUid = TARGET_UID)
        assertThat(revive(activityStart, screenState(1), allowed, unrelated, relations = listOf(relation)).justification)
            .isEqualTo(Justification.USER_INITIATED)
        val unknown = shown.copy(callerUid = null)
        assertThat(revive(activityStart, screenState(1), allowed.copy(elapsedNanos = START), unknown, relations = listOf(relation)).justification)
            .isEqualTo(Justification.USER_INITIATED)
    }

    @Test fun givenNoJoinedRequest_whenComponentlessBalIsAmbiguousOrSelfInitiated_thenTheActivityStaysUserInitiated() {
        val allowed = bal(PEER_UID, 1000, elapsed = START - SECOND, component = "")
        val screen = screenState(1)

        assertThat(revive(activityStart, screen, allowed).justification).isEqualTo(Justification.NONE)
        assertThat(revive(activityStart, screen, allowed, allowed.copy(id = "other", callerUid = 10033)).justification)
            .isEqualTo(Justification.USER_INITIATED)
        assertThat(revive(activityStart, screen, allowed.copy(callerUid = TARGET_UID)).justification)
            .isEqualTo(Justification.USER_INITIATED)
        assertThat(revive(activityStart, allowed).justification).isEqualTo(Justification.USER_INITIATED)
    }

    @Test fun givenTwoColdActivitiesInOneComponentlessBalWindow_whenDetecting_thenNeitherIsAttributedToTheCaller() {
        val other = "other.app"
        val otherStart = activityStart.copy(
            id = "other-start",
            elapsedNanos = START + 2 * SECOND,
            wallMillis = activityStart.wallMillis + 2_000,
            targetUid = 10033,
            targetPackage = other,
            component = "$other/.Main",
            processName = other,
        )
        val allowed = bal(PEER_UID, 1000, elapsed = START + SECOND, component = null)
        val findings = WakeDetection.findings(evidence(listOf(activityStart, otherStart, screenState(1), allowed)))
            .filter { it.trigger != null }

        assertThat(findings.map { it.target }).containsExactly(TARGET, other)
        assertThat(findings.map { it.actor }).doesNotContain(PEER)
        assertThat(findings.map { it.justification }).containsExactly(Justification.USER_INITIATED, Justification.USER_INITIATED)
    }

    @Test fun givenSystemEffectiveBalCaller_whenDetecting_thenAttributionFallsBackToTheAppInitiator() {
        val allowed = bal(1000, PEER_UID)
        val finding = revive(activityStart, screenState(1), allowed)

        assertThat(finding.actor).isEqualTo(PEER)
        assertThat(finding.justification).isEqualTo(Justification.NONE)
    }

    @Test fun givenOtherScreenStates_whenDetecting_thenOnlyTheLatestPriorStateOfTheSameBootApplies() {
        val priorBoot = screenState(0, boot = "prior")
        val future = screenState(0, elapsed = START + SECOND)
        val previousOff = screenState(0, elapsed = START - SECOND)
        val currentOn = screenState(1)

        assertThat(revive(activityStart, priorBoot, future).justification).isEqualTo(Justification.USER_INITIATED)
        assertThat(revive(activityStart, currentOn, previousOff).justification).isEqualTo(Justification.USER_INITIATED)
        assertThat(revive(activityStart, screenState(1, START + SECOND), previousOff).justification).isEqualTo(Justification.NONE)
    }

    @Test fun givenScreenOnActivity_whenBalIsUnrelated_thenTheActivityStaysUserInitiated() {
        val cases = listOf(
            bal(PEER_UID, 1000, component = "$TARGET/.Other"),
            bal(PEER_UID, 1000, component = "$PEER/.Wake"),
            bal(PEER_UID, 1000, elapsed = START - 3 * SECOND, component = ""),
            bal(PEER_UID, 1000).copy(boot = "previous"),
            bal(PEER_UID, 1000, elapsed = START - WakeRevives.JOIN_WINDOW - 1),
            bal(PEER_UID, 1000, elapsed = START + WakeRevives.JOIN_WINDOW + 1),
        )
        for (unrelated in cases) {
            assertThat(revive(activityStart, screenState(1), unrelated).justification).isEqualTo(Justification.USER_INITIATED)
        }
        for (elapsed in listOf(START - WakeRevives.JOIN_WINDOW, START + WakeRevives.JOIN_WINDOW)) {
            val finding = revive(activityStart, screenState(1), bal(PEER_UID, 1000, elapsed))
            assertThat(finding.actor).isEqualTo(PEER)
            assertThat(finding.justification).isEqualTo(Justification.NONE)
        }
    }

    @Test fun givenSystemAndLauncherBal_whenDetecting_thenTheActivityStaysUserInitiated() {
        val home = wakeRecord("home", "RoleHolder", START, targetPackage = PEER, action = "android.app.role.HOME", attributes = mapOf("user" to "0"))
        val launcher = wakeRecord("shown", "AppStartOccurred", START, component = "$TARGET.Wake", startType = 3, attributes = mapOf("source_type" to "1"))
        val relation = WakeRelation(launcher.id, activityStart.id, "MATCHING_COMPONENT")

        assertThat(revive(activityStart, screenState(1), bal(1000, 1001)).justification).isEqualTo(Justification.USER_INITIATED)
        assertThat(revive(activityStart, screenState(1), bal(PEER_UID, 1000), home).justification).isEqualTo(Justification.USER_INITIATED)
        assertThat(revive(activityStart, screenState(1), bal(PEER_UID, 1000, component = null), home).justification)
            .isEqualTo(Justification.USER_INITIATED)
        assertThat(revive(activityStart, screenState(1), bal(PEER_UID, 1000, component = null), launcher, relations = listOf(relation)).justification)
            .isEqualTo(Justification.USER_INITIATED)
        assertThat(revive(activityStart, screenState(1), bal(PEER_UID, 1000), launcher, relations = listOf(relation)).justification)
            .isEqualTo(Justification.USER_INITIATED)
    }

    @Test fun givenStealthActivityAfterAServiceStart_whenJudging_thenItDoesNotSupplyUserInitiation() {
        val shown = wakeRecord("shown", "AppStartOccurred", START + SECOND, component = "$TARGET.Main")
        val bal = bal(TARGET_UID, 1000, shown.elapsedNanos, "$TARGET/.Main")

        assertThat(revive(coldStart, shown, screenState(0, shown.elapsedNanos)).justification).isEqualTo(Justification.NONE)
        assertThat(revive(coldStart, shown, screenState(1), bal).justification).isEqualTo(Justification.NONE)
        assertThat(revive(coldStart, shown, screenState(1)).justification).isEqualTo(Justification.USER_INITIATED)
    }

    @Test fun givenStartInfoForComponentsOfOneStartingProcess_whenDetecting_thenTheEarliestDescribesTheOneRevive() {
        // Observed on an API 35 emulator: a job start, an activity launched 7 ms later and the app's provider, one process start.
        fun info(id: String, reason: String, offset: Long, component: String?) = wakeRecord(
            id,
            "ApplicationStartInfo",
            START + offset,
            source = "START_INFO",
            component = component,
            startType = 3,
            process = TARGET,
            // Read right after the start: no deep sleep between them.
            attributes = mapOf("reason" to reason, "clock" to "uptime", "sleep_ns" to "0"),
        )
        val job = info("job", "JOB", 0, "$TARGET/androidx.work.impl.background.systemjob.SystemJobService")
        val activity = info("activity", "START ACTIVITY", 7_000_000, "$TARGET/$TARGET.Main")
        val provider = info("provider", "CONTENT PROVIDER", 39_000_000, null)
        val event = wakeRecord(
            "event",
            "am_proc_start",
            START + 11_000_000,
            source = SYSTEM_WAKE_EVENTS,
            component = "{$TARGET/androidx.work.impl.background.systemjob.SystemJobService}",
            process = TARGET,
            attributes = mapOf("hosting_type" to "service"),
        )
        val only = WakeDetection.findings(evidence(listOf(job, activity, provider, event))).single()
        assertThat(only.trigger).isEqualTo(ReviveTrigger.JOB)
        // The activity shown in the same moment makes the start user initiated.
        assertThat(only.justification).isEqualTo(Justification.USER_INITIATED)
        // A process of the app started right after the user opened it is part of that launch.
        val main = wakeRecord("main", "am_proc_start", START, source = SYSTEM_WAKE_EVENTS, component = "{$TARGET/$TARGET.Main}", process = TARGET, attributes = mapOf("hosting_type" to "next-top-activity"))
        val helper = wakeRecord(
            "helper",
            "am_proc_start",
            START + 800_000_000,
            source = SYSTEM_WAKE_EVENTS,
            component = "{$TARGET/$TARGET.Helper}",
            process = "$TARGET:helper",
            attributes = mapOf("hosting_type" to "service"),
        )
        val launch = WakeDetection.findings(evidence(listOf(main, helper))).associateBy { it.detail }
        assertThat(launch.getValue("$TARGET/$TARGET.Helper").justification).isEqualTo(Justification.USER_INITIATED)
        assertThat(WakeDetection.findings(evidence(listOf(helper))).single().justification).isEqualTo(Justification.NONE)
    }

    @Test fun givenBroadcastStartsWithoutCaller_whenDetecting_thenProtectedActionsAndNamedReceiversArePreserved() {
        fun broadcast(protected: Boolean) = wakeRecord(
            "info", "ApplicationStartInfo", START, source = "START_INFO", component = "$TARGET/.Receiver", action = "android.intent.action.TIME_SET",
            startType = 3, process = TARGET, attributes = mapOf("reason" to "BROADCAST") + if (protected) mapOf("protected_broadcast" to "true") else emptyMap(),
        )
        val system = revive(*started(broadcast(true)))
        assertThat(system.trigger).isEqualTo(ReviveTrigger.SYSTEM_BROADCAST)
        assertThat(system.actor).isEqualTo("android")
        assertThat(system.detail).isEqualTo("android.intent.action.TIME_SET")
        val receiver = revive(*started(broadcast(false)))
        assertThat(receiver.trigger).isEqualTo(ReviveTrigger.SYSTEM_BROADCAST)
        assertThat(receiver.confidence).isEqualTo(Confidence.SNAPSHOT)
        assertThat(receiver.detail).isEqualTo("$TARGET/$TARGET.Receiver")
        val service = wakeRecord("svc", "ApplicationStartInfo", START, source = "START_INFO", component = "$TARGET/.Sync", action = "sync.ACTION", startType = 3, process = TARGET, attributes = mapOf("reason" to "SERVICE"))
        assertThat(revive(*started(service)).detail).isEqualTo("$TARGET.Sync")
    }

    @Test fun givenOnlyEventLogStarts_whenBuildingActivity_thenHostedComponentsAreBlockable() {
        val cases = listOf(
            Triple("broadcast", ReviveTrigger.SYSTEM_BROADCAST, OwnWakeKind.SYSTEM_EVENT),
            Triple("content provider", ReviveTrigger.PEER_PROVIDER, OwnWakeKind.SELF_START),
            Triple("service", ReviveTrigger.UNKNOWN, OwnWakeKind.SELF_START),
        )
        for ((hosting, trigger, kind) in cases) {
            val event = wakeRecord(
                "log-start",
                "am_proc_start",
                START,
                source = SYSTEM_WAKE_EVENTS,
                component = "{$TARGET/.Wake}",
                targetPackage = null,
                process = TARGET,
                attributes = mapOf("hosting_type" to hosting),
            )
            val finding = WakeDetection.detect(evidence(listOf(event)))
            val rows = WakeDetection.daily(finding, ZoneOffset.UTC)
            val day = rows.single().day
            val activity = buildAppWakeActivity(TARGET, day, day, rows, emptyList())
            val method = activity.own.single()
            val path = method.paths.single()
            val type = when (trigger) {
                ReviveTrigger.SYSTEM_BROADCAST -> ComponentType.RECEIVER
                ReviveTrigger.PEER_PROVIDER -> ComponentType.PROVIDER
                else -> null
            }
            assertThat(method.kind).isEqualTo(kind)
            assertThat(activity.peers).isEmpty()
            assertThat(path.trigger).isEqualTo(trigger)
            assertThat(path.actor).isEqualTo("android".takeIf { trigger == ReviveTrigger.SYSTEM_BROADCAST })
            assertThat(path.component).isEqualTo("$TARGET.Wake")
            assertThat(path.componentType).isEqualTo(type)
            assertThat(path.blockMethod).isEqualTo(WakeBlockMethod.COMPONENT)
            assertThat(finding.single().finding.confidence).isEqualTo(if (trigger == ReviveTrigger.UNKNOWN) Confidence.DIRECT else Confidence.SNAPSHOT)
            if (trigger == ReviveTrigger.SYSTEM_BROADCAST) {
                assertThat(revive(event.copy(elapsedNanos = 180 * SECOND)).actor).isEqualTo("android")
                assertThat(revive(event.copy(elapsedNanos = 180 * SECOND + 1)).actor).isNull()
            }
        }
    }

    @Test fun givenTrimmedApp_whenAProtectedBroadcastRestartsIt_thenItStaysNormal() {
        fun broadcast(protected: Boolean) = wakeRecord(
            "info", "ApplicationStartInfo", START, source = "START_INFO", component = "$TARGET/.Receiver", action = "android.intent.action.PACKAGE_FULLY_REMOVED",
            startType = 3, process = TARGET, attributes = mapOf("reason" to "BROADCAST") + if (protected) mapOf("protected_broadcast" to "true") else emptyMap(),
        )
        val trim = wakeRecord("kill", "am_kill", START - 5 * SECOND, source = SYSTEM_WAKE_EVENTS, targetUid = null, process = TARGET, attributes = mapOf("user" to "0", "reason" to "empty #3"))
        fun day(protected: Boolean) = WakeDetection.verdicts(WakeDetection.detect(evidence(listOf(*started(broadcast(protected)), trim))), ZoneOffset.UTC).single { it.target == TARGET }
        val system = day(protected = true)
        assertThat(system.afterKillRevives).isEqualTo(1)
        assertThat(system.afterKillNoneRevives).isEqualTo(0)
        assertThat(system.verdict).isEqualTo(WakeVerdict.NORMAL.name)
        assertThat(WakeDetection.findings(evidence(listOf(*started(broadcast(true)), trim))).single().afterKill).isEqualTo("empty #3")
        // The same restart by an action any app may send still counts.
        val fallback = day(protected = false)
        assertThat(fallback.afterKillNoneRevives).isEqualTo(1)
        assertThat(fallback.verdict).isEqualTo(WakeVerdict.SUSPICIOUS.name)
    }

    @Test fun givenStartInfoReasons_whenDetecting_thenReasonsNameTheTriggerDirectly() {
        fun reason(name: String, action: String? = null) = revive(
            *started(wakeRecord("info", "ApplicationStartInfo", START, source = "START_INFO", component = "$TARGET/.Wake", action = action, startType = 3, process = TARGET, attributes = mapOf("reason" to name))),
        )
        assertThat(reason("ALARM").trigger).isEqualTo(ReviveTrigger.ALARM)
        assertThat(reason("JOB").trigger).isEqualTo(ReviveTrigger.JOB)
        assertThat(reason("PUSH").trigger).isEqualTo(ReviveTrigger.PUSH_TRANSPORT)
        val boot = reason("BOOT COMPLETE", "android.intent.action.LOCKED_BOOT_COMPLETED")
        assertThat(boot.trigger).isEqualTo(ReviveTrigger.SYSTEM_BROADCAST)
        assertThat(boot.detail).isEqualTo("android.intent.action.LOCKED_BOOT_COMPLETED")
        assertThat(reason("BACKUP").trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        val launcher = reason("LAUNCHER")
        assertThat(launcher.trigger).isEqualTo(ReviveTrigger.PEER_ACTIVITY)
        assertThat(reason("LAUNCHER RECENTS").justification).isEqualTo(Justification.USER_INITIATED)
        assertThat(launcher.justification).isEqualTo(Justification.USER_INITIATED)
        val unexplained = reason("SERVICE")
        assertThat(unexplained.trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        assertThat(unexplained.detail).isEqualTo("$TARGET.Wake")
        assertThat(unexplained.pathKey).isEqualTo("UNKNOWN||$TARGET|$TARGET.Wake")
    }

    @Test fun givenProcessStartTriggerTypes_whenDetecting_thenAlarmPushAndJobAreDirect() {
        val action = "target.app.WAKE"
        fun type(value: String, component: String? = coldStart.component) = revive(coldStart.copy(component = component, action = action, attributes = """{"trigger_type":"$value"}"""))
        assertThat(type("1").trigger).isEqualTo(ReviveTrigger.ALARM)
        assertThat(type("1").detail).isEqualTo("$TARGET/$TARGET.Wake")
        assertThat(type("1", component = null).detail).isEqualTo(action)
        assertThat(type("2").trigger).isEqualTo(ReviveTrigger.PUSH_TRANSPORT)
        assertThat(type("2").detail).isEqualTo(action)
        assertThat(type("3").trigger).isEqualTo(ReviveTrigger.PUSH_TRANSPORT)
        assertThat(type("4").trigger).isEqualTo(ReviveTrigger.JOB)
        assertThat(type("4").confidence).isEqualTo(Confidence.DIRECT)
        assertThat(type("4").detail).isEqualTo("$TARGET.Wake")
        assertThat(type("4", component = null).detail).isEqualTo(action)
        val fcm = revive(coldStart.copy(action = "com.google.android.c2dm.intent.RECEIVE"))
        assertThat(fcm.trigger).isEqualTo(ReviveTrigger.PUSH_TRANSPORT)
        assertThat(fcm.actor).isEqualTo("com.google.android.gms")
    }

    @Test fun givenJoinedAppCallers_whenDetecting_thenPeerKindSelfAndPushAreSeparated() {
        val service = joined(request("ServiceRequestEventReported", PEER_UID, PEER, state = "requester_process_state" to "1018"))
        assertThat(service.trigger).isEqualTo(ReviveTrigger.PEER_SERVICE)
        assertThat(service.actor).isEqualTo(PEER)
        assertThat(service.actorInBackground).isTrue()
        assertThat(service.confidence).isEqualTo(Confidence.JOINED)
        assertThat(service.pathKey).isEqualTo("PEER_SERVICE|$PEER|$TARGET|$TARGET.Wake")
        val visible = joined(request("ServiceRequestEventReported", PEER_UID, PEER, state = "requester_process_state" to "1002"))
        assertThat(visible.actorInBackground).isFalse()
        assertThat(joined(request("ServiceRequestEventReported", PEER_UID, PEER, state = "requester_process_state" to "999")).actorInBackground).isNull()
        // ActivityManager writes raw process states (seen on an API 35 emulator): 2 top, 10 service, -1 unknown.
        assertThat(joined(request("ServiceRequestEventReported", PEER_UID, PEER, state = "requester_process_state" to "2")).actorInBackground).isFalse()
        assertThat(joined(request("ServiceRequestEventReported", PEER_UID, PEER, state = "requester_process_state" to "10")).actorInBackground).isTrue()
        assertThat(joined(request("ServiceRequestEventReported", PEER_UID, PEER, state = "requester_process_state" to "4294967295")).actorInBackground).isNull()
        assertThat(joined(request("ServiceRequestEventReported", PEER_UID, PEER, state = "requester_process_state" to "20")).actorInBackground).isNull()
        val broadcast = joined(request("BroadcastDeliveryEventReported", PEER_UID, PEER, "peer.WAKE", "sender_proc_state" to "1004"))
        assertThat(broadcast.trigger).isEqualTo(ReviveTrigger.PEER_BROADCAST)
        assertThat(broadcast.detail).isEqualTo("peer.WAKE")
        assertThat(broadcast.actorInBackground).isTrue()
        val provider = joined(request("ProviderAcquisitionEventReported", PEER_UID, null, state = "client_process_state" to "1005"))
        assertThat(provider.trigger).isEqualTo(ReviveTrigger.PEER_PROVIDER)
        assertThat(provider.actor).isEqualTo(PEER)
        assertThat(provider.actorInBackground).isFalse()
        val activity = joined(request("AppStartOccurred", PEER_UID, PEER).copy(callerUid = null))
        assertThat(activity.trigger).isEqualTo(ReviveTrigger.PEER_ACTIVITY)
        assertThat(joined(request("ServiceRequestEventReported", TARGET_UID, TARGET)).trigger).isEqualTo(ReviveTrigger.SELF_PROCESS)
        // While another process of the app is visible, the app was not revived.
        val visibleSelf = request("ServiceRequestEventReported", TARGET_UID, TARGET, state = "requester_process_state" to "1002")
        assertThat(WakeDetection.findings(evidence(listOf(coldStart, visibleSelf), listOf(WakeRelation(visibleSelf.id, coldStart.id, "MATCHING_CALLER"))))).isEmpty()
        val push = joined(request("BroadcastDeliveryEventReported", 10050, "com.xiaomi.xmsf", "com.xiaomi.mipush.RECEIVE_MESSAGE"))
        assertThat(push.trigger).isEqualTo(ReviveTrigger.PUSH_TRANSPORT)
        assertThat(push.actor).isEqualTo("com.xiaomi.xmsf")
        // Google Play services only counts as a push transport for its c2dm delivery.
        assertThat(joined(request("ServiceRequestEventReported", 10050, "com.google.android.gms")).trigger).isEqualTo(ReviveTrigger.PEER_SERVICE)
    }

    @Test fun givenOemTransportRequests_whenDetecting_thenDedicatedServicesAndKnownHmsActionsCountAsPush() {
        val dedicated = listOf("com.huawei.android.pushagent", "com.hihonor.pushagent", "com.meizu.cloud", "com.coloros.mcs", "com.oplus.mcs", "com.sec.spp.push")
        for (pkg in dedicated) {
            val push = joined(request("ServiceRequestEventReported", 10050, pkg))
            assertThat(push.trigger).isEqualTo(ReviveTrigger.PUSH_TRANSPORT)
            assertThat(push.actor).isEqualTo(pkg)
        }
        val actions = listOf("com.huawei.push.action.MESSAGING_EVENT", "com.huawei.intent.action.PUSH", "com.huawei.android.push.intent.RECEIVE")
        for (action in actions) {
            val push = joined(request("BroadcastDeliveryEventReported", 10050, "com.huawei.hwid", action))
            assertThat(push.trigger).isEqualTo(ReviveTrigger.PUSH_TRANSPORT)
            assertThat(push.detail).isEqualTo(action)
        }
        val nonPush = joined(request("ServiceRequestEventReported", 10050, "com.huawei.hwid", "com.huawei.account.LOGIN"))
        assertThat(nonPush.trigger).isEqualTo(ReviveTrigger.PEER_SERVICE)
    }

    @Test fun givenPushStartInfoWithAJoinedDelivery_whenDetecting_thenOnlyKnownTransportAddsAttribution() {
        val info = wakeRecord("info", "ApplicationStartInfo", START, source = "START_INFO", startType = 3, process = TARGET, attributes = mapOf("reason" to "PUSH"))
        val pushAction = "com.huawei.android.push.intent.RECEIVE"
        val push = request("BroadcastDeliveryEventReported", 10050, "com.huawei.hwid", pushAction)
        fun finding(request: WakeRecord, vararg extra: WakeRecord) = joined(request, info, *extra)

        val silent = finding(push)
        assertThat(silent.trigger).isEqualTo(ReviveTrigger.PUSH_TRANSPORT)
        assertThat(silent.actor).isEqualTo("com.huawei.hwid")
        assertThat(silent.detail).isEqualTo(pushAction)
        assertThat(silent.confidence).isEqualTo(Confidence.JOINED)
        assertThat(silent.justification).isEqualTo(Justification.NONE)

        val notification = wakeRecord("notification", "NotificationReported", START + 10 * SECOND, attributes = mapOf("event_id" to "162"))
        assertThat(finding(push, notification).justification).isEqualTo(Justification.PRODUCTIVE)
        assertThat(finding(push, notification.copy(elapsedNanos = notification.elapsedNanos + 1)).justification).isEqualTo(Justification.NONE)

        val nonPush = finding(push.copy(action = "com.huawei.account.LOGIN"))
        assertThat(nonPush.trigger).isEqualTo(ReviveTrigger.PUSH_TRANSPORT)
        assertThat(nonPush.actor).isNull()
        assertThat(nonPush.detail).isEqualTo("$TARGET.Wake")
        assertThat(nonPush.confidence).isEqualTo(Confidence.DIRECT)
    }

    @Test fun givenSystemCallers_whenDetecting_thenBroadcastBindJobSyncAndAlarmAreSeparated() {
        val system = request("ServiceRequestEventReported", 1000, "android")
        val broadcast = joined(request("BroadcastDeliveryEventReported", 1000, "android", "android.intent.action.PACKAGE_REPLACED"))
        assertThat(broadcast.trigger).isEqualTo(ReviveTrigger.SYSTEM_BROADCAST)
        assertThat(broadcast.actor).isEqualTo("android")
        assertThat(broadcast.detail).isEqualTo("android.intent.action.PACKAGE_REPLACED")
        val listener = wakeRecord("listener", "SystemBinding", START - 60 * SECOND, source = "LISTENERS", component = "$TARGET/$TARGET.Wake", action = "LISTENERS", attributes = mapOf("user" to "0"))
        val bind = joined(system, listener)
        assertThat(bind.trigger).isEqualTo(ReviveTrigger.SYSTEM_BIND)
        assertThat(bind.justification).isEqualTo(Justification.USER_GRANTED_ROLE)
        val job = wakeRecord("job", "ScheduledJobStateChanged", START - SECOND, attributes = mapOf("attribution_node" to "$TARGET_UID", "state" to "1", "job_name" to "abc"))
        assertThat(joined(system, job).trigger).isEqualTo(ReviveTrigger.JOB)
        assertThat(joined(system, job).detail).isEqualTo("$TARGET.Wake")
        val sync = wakeRecord("sync", "SyncStateChanged", START + SECOND, attributes = mapOf("attribution_node" to "$TARGET_UID", "state" to "1"))
        assertThat(joined(system, sync).trigger).isEqualTo(ReviveTrigger.SYNC)
        assertThat(joined(system, sync).detail).isEqualTo("$TARGET.Wake")
        val alarm = wakeRecord("alarm", "WakeupAlarmOccurred", START - 2 * SECOND, attributes = mapOf("attribution_node" to "$TARGET_UID", "tag" to "0011aa"))
        assertThat(joined(system, alarm).trigger).isEqualTo(ReviveTrigger.ALARM)
        // A system binding of a component without special access is not a role binding.
        val unexplained = joined(system)
        assertThat(unexplained.trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        assertThat(unexplained.actor).isEqualTo("android")
        assertThat(unexplained.justification).isEqualTo(Justification.NONE)
        // A stale job outside the join window does not explain the start.
        assertThat(joined(system, job.copy(elapsedNanos = START - 6 * SECOND)).trigger).isEqualTo(ReviveTrigger.UNKNOWN)
    }
}
