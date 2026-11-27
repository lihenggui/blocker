// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.wake.WakeDetectionEvidence
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import com.merxury.blocker.core.model.wake.WakeContinuation
import com.merxury.blocker.core.model.wake.WakeFinding
import com.merxury.blocker.core.model.wake.WakeRecord
import com.merxury.blocker.core.model.wake.WakeRelation
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WakeChainTest {
    private val second = 1_000_000_000L
    private fun uid(app: Int) = 10_100 + app
    private fun pkg(app: Int) = "app$app"

    /** app0 cold-starts app1, which cold-starts app2, and so on. Each hop's caller is in the background. */
    private fun chain(length: Int, links: (Int) -> List<WakeContinuation> = { hop -> listOf(WakeContinuation("request$hop", "request${hop - 1}")) }): WakeDetectionEvidence {
        val records = mutableListOf<WakeRecord>()
        val relations = mutableListOf<WakeRelation>()
        for (hop in 1..length) {
            val time = hop * 10 * second
            records += wakeRecord("start$hop", "ProcessStartTime", time, targetUid = uid(hop), targetPackage = pkg(hop), component = "${pkg(hop)}/.Wake", startType = 3, process = pkg(hop))
            records += wakeRecord(
                "request$hop", "ServiceRequestEventReported", time - second, callerUid = uid(hop - 1), targetUid = uid(hop),
                callerPackage = pkg(hop - 1), targetPackage = pkg(hop), component = ".Wake", startType = 3,
                attributes = mapOf("requester_process_state" to "1018"),
            )
            relations += WakeRelation("request$hop", "start$hop", "MATCHING_COMPONENT")
        }
        return WakeDetectionEvidence(records, relations, (2..length).flatMap(links), emptyList(), emptyList(), emptyList(), (0..length).associate { uid(it) to setOf(pkg(it)) })
    }

    private fun WakeDetectionEvidence.revives(): Map<String, WakeFinding> = WakeDetection.findings(this).associateBy { it.target }

    @Test fun givenTwoAndThreeHopChains_whenDetecting_thenEachDownstreamReviveNamesTheOriginAndItsHop() {
        val two = chain(2).revives()
        val origin = two.getValue("app1")
        assertThat(origin.chainRoot).isNull()
        assertThat(origin.chainDepth).isEqualTo(0)
        assertThat(origin.pathKey).isEqualTo("PEER_SERVICE|app0|app1|app1.Wake")
        assertThat(two.getValue("app2").chainRoot).isEqualTo(origin.pathKey)
        assertThat(two.getValue("app2").chainDepth).isEqualTo(1)
        // The chained revive keeps its own classification.
        assertThat(two.getValue("app2").trigger).isEqualTo(ReviveTrigger.PEER_SERVICE)
        assertThat(two.getValue("app2").actor).isEqualTo("app1")

        val three = chain(3).revives()
        assertThat(three.getValue("app3").chainRoot).isEqualTo(origin.pathKey)
        assertThat(three.getValue("app3").chainDepth).isEqualTo(2)
        assertThat(three.getValue("app2").chainDepth).isEqualTo(1)
    }

    @Test fun givenPushReasonAndJoinedTransport_whenDetecting_thenTheRequestCanContinueAChain() {
        val evidence = chain(2)
        val records = evidence.records.map { record ->
            if (record.id == "request2") record.copy(callerPackage = "com.coloros.mcs", action = "vendor.PUSH") else record
        } + wakeRecord(
            "push-info", "ApplicationStartInfo", 20 * second, source = "START_INFO", targetUid = uid(2), targetPackage = pkg(2),
            startType = 3, process = pkg(2), attributes = mapOf("reason" to "PUSH"),
        )

        val revives = evidence.copy(records = records).revives()
        val push = revives.getValue(pkg(2))
        assertThat(push.trigger).isEqualTo(ReviveTrigger.PUSH_TRANSPORT)
        assertThat(push.actor).isEqualTo("com.coloros.mcs")
        assertThat(push.detail).isEqualTo("app2/app2.Wake")
        assertThat(push.chainRoot).isEqualTo(revives.getValue(pkg(1)).pathKey)
    }

    @Test fun givenCyclicOrOverlongLinks_whenDetecting_thenTheWalkTerminates() {
        val cycle = chain(2) { listOf(WakeContinuation("request2", "request1"), WakeContinuation("request1", "request2")) }.revives()
        assertThat(cycle.values.map { it.chainRoot }).containsExactly(null, null)
        val long = WakeRevives.MAX_CHAIN_DEPTH + 3
        val deep = chain(long).revives()
        val last = deep.getValue(pkg(long))
        assertThat(last.chainDepth).isEqualTo(WakeRevives.MAX_CHAIN_DEPTH)
        assertThat(last.chainRoot).isEqualTo(deep.getValue(pkg(long - WakeRevives.MAX_CHAIN_DEPTH)).pathKey)
    }

    @Test fun givenChainedRevives_whenJudging_thenTheyCountForTheOriginAndNotForTheirOwnPackage() {
        val detected = WakeDetection.detect(chain(3))
        val verdicts = WakeDetection.verdicts(detected, ZoneOffset.UTC).associateBy { it.target }
        // The origin revive still counts for app1. Its caller app0 is credited with both downstream starts.
        assertThat(verdicts.getValue("app1").noneRevives).isEqualTo(1)
        assertThat(verdicts.getValue("app1").peerBackgroundNoneRevives).isEqualTo(1)
        assertThat(verdicts.getValue("app0").downstreamStarts).isEqualTo(2)
        for (consequence in listOf("app2", "app3")) {
            val day = verdicts.getValue(consequence)
            assertThat(day.revives).isEqualTo(1)
            assertThat(day.noneRevives).isEqualTo(0)
            assertThat(day.peerBackgroundNoneRevives).isEqualTo(0)
            assertThat(day.downstreamStarts).isEqualTo(0)
        }
        // The origin path counts direct and transitive starts.
        val rows = WakeDetection.daily(detected, ZoneOffset.UTC).associateBy { it.target }
        assertThat(rows.getValue("app1").downstreamStarts).isEqualTo(2)
        assertThat(rows.getValue("app2").downstreamStarts).isEqualTo(0)
        assertThat(rows.getValue("app3").chainRoot).isEqualTo(rows.getValue("app1").pathKey)
        assertThat(rows.getValue("app3").chainDepth).isEqualTo(2)
    }

    @Test fun givenChainedReviveAfterAKill_whenJudging_thenOnlyTheInformationalCounterIncludesIt() {
        val evidence = chain(2)
        val kill = wakeRecord(
            "kill",
            "am_kill",
            15 * second,
            source = SYSTEM_WAKE_EVENTS,
            targetUid = null,
            process = "app2",
            attributes = mapOf("user" to "0", "reason" to "empty #3"),
        )
        // The event log names the restarted process, which the kill is matched on, and shares statsd's pid.
        val event = wakeRecord(
            "event2", "am_proc_start", 20 * second, source = SYSTEM_WAKE_EVENTS, targetUid = uid(2), targetPackage = pkg(2),
            component = "{app2/app2.Wake}", process = "app2", pid = 222,
        )
        val records = evidence.records.map { if (it.id == "start2") it.copy(pid = 222) else it } + event + kill
        val day = WakeDetection.verdicts(WakeDetection.detect(evidence.copy(records = records)), ZoneOffset.UTC).single { it.target == "app2" }
        // Recorded, but the kill and restart of a chain's victim are the origin's doing.
        assertThat(day.afterKillRevives).isEqualTo(1)
        assertThat(day.afterKillNoneRevives).isEqualTo(0)
        assertThat(day.noneRevives).isEqualTo(0)
    }

    // The WakeAttributionTest chain: client.app cold-starts target.app, whose ":peer" process cold-starts third.app.
    private val request = WakeRecord(
        "request", "boot", 20, 1000, "statsd", "ServiceRequestEventReported",
        10011, 10022, "client.app", "target.app", ".Service", "wake", 3, null, null, "{}",
    )
    private val middle = request.copy(
        id = "start",
        elapsedNanos = 30,
        kind = "ProcessStartTime",
        callerUid = null,
        callerPackage = null,
        pid = 123,
        component = "target.app/.Service",
        processName = "target.app",
        attributes = """{"hosting_type_id":"16"}""",
    )
    private val witness = middle.copy(id = "system", source = SYSTEM_WAKE_EVENTS, kind = "am_proc_start", processName = "target.app:peer")
    private val outgoing = request.copy(
        id = "next",
        elapsedNanos = 40,
        callerUid = 10022,
        callerPackage = "target.app",
        targetUid = 10033,
        targetPackage = "third.app",
        attributes = """{"request_process_name":":peer","unique_caller_package":"target.app"}""",
    )
    private val last = middle.copy(
        id = "last",
        elapsedNanos = 50,
        pid = 456,
        targetUid = 10033,
        targetPackage = "third.app",
        component = "third.app/.Service",
        processName = "third.app",
    )

    private fun attributed(vararg records: WakeRecord): WakeFinding {
        val list = records.toList()
        val relations = WakeAttribution.relationships(list)
        val evidence = WakeDetectionEvidence(
            list,
            relations,
            WakeAttribution.continuations(list, relations),
            emptyList(),
            emptyList(),
            emptyList(),
            mapOf(10011 to setOf("client.app"), 10022 to setOf("target.app"), 10033 to setOf("third.app")),
        )
        return WakeDetection.findings(evidence).single { it.target == "third.app" }
    }

    @Test fun givenAttributedContinuation_whenDetecting_thenOnlyAnUnambiguousLinkChainsTheRevive() {
        val chained = attributed(request, middle, witness, outgoing, last)
        assertThat(chained.chainRoot).isEqualTo("PEER_SERVICE|client.app|target.app|target.app.Service")
        assertThat(chained.chainDepth).isEqualTo(1)
        // Without the witnessed intermediate process, or with two candidate processes, there is no link and no chain.
        assertThat(attributed(request, middle, outgoing, last).chainRoot).isNull()
        assertThat(attributed(request, middle, witness, witness.copy(id = "reused", elapsedNanos = 38), outgoing, last).chainRoot).isNull()
    }
}
