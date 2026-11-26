// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.wake.WakeDetectionEvidence
import com.merxury.blocker.core.database.wake.WakeHoldAssociation
import com.merxury.blocker.core.database.wake.WakeHoldPeriod
import com.merxury.blocker.core.database.wake.WakeHoldProcess
import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.database.wake.WakeRelation
import com.merxury.blocker.core.model.component.UIDS_PER_USER
import com.merxury.blocker.core.model.wake.Confidence
import com.merxury.blocker.core.model.wake.HoldCause
import com.merxury.blocker.core.model.wake.Justification
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import com.merxury.blocker.core.model.wake.WakeFinding
import com.merxury.blocker.core.model.wake.WakeVerdict
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import java.time.ZoneOffset

private const val TARGET = "target.app"
private const val TARGET_UID = 10022
private const val PEER = "peer.app"
private const val PEER_UID = 10011
private const val WALL = 1_795_335_233_000L
private const val SECOND = 1_000_000_000L
private const val START = 100 * SECOND

internal fun wakeRecord(
    id: String,
    kind: String,
    elapsed: Long,
    source: String = "statsd",
    callerUid: Int? = null,
    targetUid: Int? = TARGET_UID,
    callerPackage: String? = null,
    targetPackage: String? = TARGET,
    component: String? = null,
    action: String? = null,
    startType: Int? = null,
    process: String? = null,
    attributes: Map<String, String> = emptyMap(),
    pid: Int? = null,
) = WakeRecord(
    id, "boot", elapsed, WALL + elapsed / 1_000_000, source, kind, callerUid, targetUid, callerPackage, targetPackage,
    component, action, startType, pid, process, JsonObject(attributes.mapValues { JsonPrimitive(it.value) }).toString(),
)

/** A start-info record and the event log's start it describes: start-info alone is never a revive. */
internal fun started(info: WakeRecord) = arrayOf(
    info,
    wakeRecord(
        "${info.id}-event",
        "am_proc_start",
        info.elapsedNanos,
        source = SYSTEM_WAKE_EVENTS,
        targetUid = info.targetUid,
        targetPackage = null,
        component = info.component?.let { "{$it}" },
        process = info.processName,
    ),
)

class WakeDetectionTest {
    private val coldStart = wakeRecord("start", "ProcessStartTime", START, component = "$TARGET/.Wake", startType = 3, process = TARGET)

    private fun evidence(
        records: List<WakeRecord>,
        relations: List<WakeRelation> = emptyList(),
        periods: List<WakeHoldPeriod> = emptyList(),
        processes: List<WakeHoldProcess> = emptyList(),
        associations: List<WakeHoldAssociation> = emptyList(),
    ) = WakeDetectionEvidence(records, relations, emptyList(), periods, processes, associations, mapOf(TARGET_UID to setOf(TARGET), TARGET_UID + UIDS_PER_USER to setOf(TARGET), PEER_UID to setOf(PEER)))

    private fun revive(vararg records: WakeRecord, relations: List<WakeRelation> = emptyList()): WakeFinding = WakeDetection.findings(evidence(records.toList(), relations)).single { it.trigger != null }

    private fun joined(request: WakeRecord, vararg records: WakeRecord) = revive(coldStart, request, *records, relations = listOf(WakeRelation(request.id, coldStart.id, "MATCHING_CALLER")))

    private fun request(kind: String, caller: Int, callerPackage: String? = null, action: String? = null, state: Pair<String, String>? = null) = wakeRecord("request", kind, START - SECOND, callerUid = caller, callerPackage = callerPackage, component = ".Wake", action = action, startType = 3, attributes = listOfNotNull(state).toMap())

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
        assertThat(launch.getValue("$TARGET.Helper").justification).isEqualTo(Justification.USER_INITIATED)
        assertThat(WakeDetection.findings(evidence(listOf(helper))).single().justification).isEqualTo(Justification.NONE)
    }

    @Test fun givenBroadcastStartsWithoutCaller_whenDetecting_thenOnlyProtectedActionsAreSystemBroadcasts() {
        fun broadcast(protected: Boolean) = wakeRecord(
            "info", "ApplicationStartInfo", START, source = "START_INFO", component = "$TARGET/.Receiver", action = "android.intent.action.TIME_SET",
            startType = 3, process = TARGET, attributes = mapOf("reason" to "BROADCAST") + if (protected) mapOf("protected_broadcast" to "true") else emptyMap(),
        )
        val system = revive(*started(broadcast(true)))
        assertThat(system.trigger).isEqualTo(ReviveTrigger.SYSTEM_BROADCAST)
        assertThat(system.actor).isEqualTo("android")
        assertThat(system.detail).isEqualTo("android.intent.action.TIME_SET")
        // An android.* action that is not protected may come from any app.
        val unknown = revive(*started(broadcast(false)))
        assertThat(unknown.trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        assertThat(unknown.detail).isEqualTo("android.intent.action.TIME_SET")
        val service = wakeRecord("svc", "ApplicationStartInfo", START, source = "START_INFO", component = "$TARGET/.Sync", action = "sync.ACTION", startType = 3, process = TARGET, attributes = mapOf("reason" to "SERVICE"))
        assertThat(revive(*started(service)).detail).isEqualTo("$TARGET.Sync")
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
        val unknown = day(protected = false)
        assertThat(unknown.afterKillNoneRevives).isEqualTo(1)
        assertThat(unknown.verdict).isEqualTo(WakeVerdict.SUSPICIOUS.name)
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
        assertThat(type("1").detail).isEqualTo(action)
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
        val listener = wakeRecord("listener", "SystemBinding", START - 60 * SECOND, source = "LISTENERS", component = "$TARGET/$TARGET.Wake", action = "LISTENERS")
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
        fun role(name: String) = wakeRecord("role", "RoleHolder", START, source = "ROLES", action = name)
        assertThat(joined(system, role("android.app.role.DIALER")).justification).isEqualTo(Justification.USER_GRANTED_ROLE)
        assertThat(joined(system, role("android.app.role.SYSTEM_GALLERY")).trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        val otherService = wakeRecord("a11y", "SystemBinding", START, source = "ACCESSIBILITY", component = "$TARGET/.Reader", action = "ACCESSIBILITY")
        assertThat(joined(system, otherService).trigger).isEqualTo(ReviveTrigger.UNKNOWN)
        val vpn = wakeRecord("vpn", "SystemBinding", START, source = "VPN", component = TARGET, action = "VPN")
        assertThat(joined(system, vpn).justification).isEqualTo(Justification.USER_GRANTED_ROLE)
    }

    @Test fun givenNoCaller_whenDetecting_thenSnapshotsAndCorrelationsAreUsedBeforeUnknown() {
        val serviceStart = wakeRecord("info", "ApplicationStartInfo", START, source = "START_INFO", component = "$TARGET/.Ime", startType = 3, process = TARGET, attributes = mapOf("reason" to "SERVICE"))
        val ime = wakeRecord("ime", "SystemBinding", START, source = "INPUT", component = "$TARGET/.Ime", action = "INPUT")
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
        val role = wakeRecord("role", "RoleHolder", 0, source = "ROLES", action = "android.app.role.SMS")
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
        val ime = wakeRecord("ime", "SystemBinding", 0, source = "INPUT", component = "$TARGET/.Ime", action = "INPUT")
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
