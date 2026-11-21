// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import com.merxury.blocker.core.wake.proto.WakeStats
import org.junit.Test
import java.io.ByteArrayOutputStream

class WakeStatsCodecTest {
    private val id = WakeStatsCodec.configId(10111, 7)
    private fun wire(write: CodedOutputStream.() -> Unit): ByteArray {
        val output = ByteArrayOutputStream()
        CodedOutputStream.newInstance(output).apply {
            write()
            flush()
        }
        return output.toByteArray()
    }

    private fun report(event: WakeStats.Event, atomId: Int, epoch: Long = 1000L): ByteArray = WakeStats.ReportList.newBuilder()
        .setConfigKey(WakeStats.ConfigKey.newBuilder().setId(id).setUid(2000))
        .addReports(
            WakeStats.Report.newBuilder().setCurrentReportElapsedNanos(990L).setCurrentReportWallClockNanos(epoch + 990L)
                .addMetrics(
                    WakeStats.Metric.newBuilder().setMetricId(10000L + atomId)
                        .setEventMetrics(WakeStats.Events.newBuilder().addData(event)),
                ),
        ).build().toByteArray()

    @Test fun sparseProviderRecordPreservesUidAndDoesNotInventComponent() {
        val atom = wire {
            writeByteArray(
                477,
                wire {
                    writeInt32(1, 10120)
                    writeInt32(2, 10111)
                    writeInt32(3, 3)
                },
            )
        }
        val bytes = report(WakeStats.Event.newBuilder().setElapsedTimestampNanos(32).setAtom(ByteString.copyFrom(atom)).build(), 477)
        val fact = WakeStatsCodec.decode(bytes, 1000, 990, 2000, id).observations.single()
        assertThat(fact.callerUid).isEqualTo(10111)
        assertThat(fact.targetUid).isEqualTo(10120)
        assertThat(fact.startType).isEqualTo(3)
        assertThat(fact.component).isNull()
        assertThat(fact.targetPackage).isNull()
    }

    @Test fun redactedActionStaysDistinctFromAnAbsentAction() {
        val atom = wire { writeByteArray(476, wire { writeString(3, "content://private/path") }) }
        val bytes = report(WakeStats.Event.newBuilder().setElapsedTimestampNanos(32).setAtom(ByteString.copyFrom(atom)).build(), 476)
        val fact = WakeStatsCodec.decode(bytes, 1000, 990, 2000, id).observations.single()
        assertThat(fact.action).isEqualTo("<redacted>")
    }

    @Test fun aggregateExpandsEachBootRelativeTimeWithoutClaimingPreviousBoot() {
        val atom = wire {
            writeByteArray(
                476,
                wire {
                    writeString(8, ".Sync")
                    writeString(10, "test.target")
                },
            )
        }
        val event = WakeStats.Event.newBuilder().setAggregatedAtomInfo(
            WakeStats.Aggregate.newBuilder()
                .setAtom(ByteString.copyFrom(atom)).addAllElapsedTimestampNanos(listOf(2L, 4L)),
        ).build()
        assertThat(WakeStatsCodec.decode(report(event, 476), 1000, 990, 2000, id).observations.map { it.elapsedNanos }).containsExactly(2L, 4L)
        val old = WakeStatsCodec.decode(report(event, 476, 300_000_000_000L), 1000, 990, 2000, id)
        assertThat(old.observations).isEmpty()
        assertThat(old.gaps).isNotEmpty()
    }

    @Test fun nativeReportConfigurationIsScopedPerInstallationAndDoesNotSubscribeToMessageContents() {
        val config = WakeStats.Config.parseFrom(WakeStatsCodec.config(id, 2000))
        assertThat(config.id).isNotEqualTo(WakeStatsCodec.configId(10112, 7))
        assertThat(config.allowedLogSourceList).containsExactly("AID_SYSTEM", "AID_STATSD")
        assertThat(config.metricActivationList.map { it.metricId }).containsExactlyElementsIn(config.eventMetricList.map { it.id })
        assertThat(config.metricActivationList.flatMap { it.eventActivationList }.map { it.ttlSeconds }.distinct())
            .containsExactly(WakeStatsCodec.LEASE_SECONDS)
        assertThat(config.atomMatcherList.single { it.id == 47L }.simpleAtomMatcher.fieldValueMatcherList.map { it.eqInt })
            .containsExactly(2000L, WakeStatsCodec.leaseLabel(id).toLong(), 1L).inOrder()
        assertThat(config.atomMatcherList.map { it.simpleAtomMatcher.atomId }).containsAtLeast(475, 476, 477, 8, 367, 368)
    }

    @Test fun incompleteUserListCannotAuthorizeOwnerOnlyChanges() {
        val owner = "Users:\n  UserInfo{0:Owner:4c13} running"
        assertThat(WakeSystemEvidence.users(owner)).containsExactly(0)
        assertThat(WakeSystemEvidence.users("$owner\n  UserInfo{10:Work:30}")).containsExactly(0, 10)
        assertThat(WakeSystemEvidence.users("$owner\n  <profile unavailable>")).isEmpty()
    }

    @Test fun onlySystemEventHeadersCanSupplyProcessStarts() {
        val payload = "am_proc_start: [0,1419,10144,example.app,service,{example.app/.Sync}]"
        val lines = listOf(
            "241.653 10144 615 653 I $payload",
            "241.653 615 653 I $payload",
            "241.653 1000 615 653 I other: $payload",
            "241.653 system 615 653 I $payload",
        )
        assertThat(WakeSystemEvidence.logcat(lines.joinToString("\n"), 242000, 242000000000L).map { it.kind }).containsExactly("am_proc_start")
    }

    @Test fun givenAnHourAsleepWhenImportingEpochEventsThenTheirWallTimesAndRelativeOrderSurvive() {
        val wall = 1_700_000_000_000L
        val elapsed = 5_400_000_000_000L
        val tag = "system 615 653 I am_proc_start: [0,1419,10144,example.app,service,{example.app/.Sync}]"
        val log = listOf("1699994599.000", "1699996340.000", "1699999940.000", "1700000001.000")
            .joinToString("\n") { "$it $tag" }
        val first = WakeSystemEvidence.logcat(log, wall, elapsed)
        assertThat(first.map { it.elapsedNanos }).containsExactly(1_740_000_000_000L, 5_340_000_000_000L).inOrder()
        assertThat(first.last().eventWallNanos).isEqualTo((wall - 60_000) * 1_000_000)
        val repeated = WakeSystemEvidence.logcat(log, wall + 1, elapsed + 1_200_000)
        assertThat(repeated.map { it.eventWallNanos }).containsExactlyElementsIn(first.map { it.eventWallNanos }).inOrder()
    }

    @Test fun serviceProviderAndAlarmSnapshotsKeepImmediateSourceOnly() {
        val sample = "* ContentProviderRecord{abcdef u0 example.app/.Provider}\n  Connections:\n    -> 23:example.client/u0a170 s1/1 u0/0"
        assertThat(WakeSystemEvidence.snapshot(WakeCommand.PROVIDERS, sample, 100).single().attributes["client_process"]).isEqualTo("example.client")
        val pending = "#0: PendingIntentRecord{123 example.client broadcastIntent}\n uid=10123 packageName=example.client\n requestIntent=act=example.UPDATE dat=content://secret/path cmp=example.app/.Receiver"
        val intent = WakeSystemEvidence.snapshot(WakeCommand.INTENTS, pending, 100).single()
        assertThat(intent.callerUid).isEqualTo(10123)
        assertThat(intent.toString()).doesNotContain("secret")
        val privateAction = pending.replace("example.UPDATE", "content://private/action")
        assertThat(WakeSystemEvidence.snapshot(WakeCommand.INTENTS, privateAction, 100).single().action).isEqualTo("<redacted>")
        val start = WakeSystemEvidence.logcat("  241.653  1000   615   653 I am_proc_start: [0,1419,10144,example.app,service,{example.app/.Sync}]", 242000, 242000000000L).single()
        assertThat(start.elapsedNanos).isEqualTo(241653000000L)
        assertThat(start.eventWallNanos).isEqualTo(241653000000L)
        assertThat(start.callerUid).isNull()
    }
}
