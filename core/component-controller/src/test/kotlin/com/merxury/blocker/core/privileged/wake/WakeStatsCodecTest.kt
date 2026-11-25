// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.wake

import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import com.merxury.blocker.core.wake.proto.WakeStats
import org.junit.Assert.assertThrows
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

    @Test fun givenSparseProviderEvent_whenDecoding_thenUidsSurviveAndComponentIsUnknown() {
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

    @Test fun givenPrivateAction_whenDecoding_thenActionIsMarkedRedacted() {
        val atom = wire { writeByteArray(476, wire { writeString(3, "content://private/path") }) }
        val bytes = report(WakeStats.Event.newBuilder().setElapsedTimestampNanos(32).setAtom(ByteString.copyFrom(atom)).build(), 476)
        val fact = WakeStatsCodec.decode(bytes, 1000, 990, 2000, id).observations.single()
        assertThat(fact.action).isEqualTo("<redacted>")
    }

    @Test fun givenAggregatedEvents_whenDecoding_thenOnlyCurrentBootTimestampsAreExpanded() {
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

    @Test fun givenRecorderOwner_whenBuildingConfig_thenAllMetricsRequireItsLease() {
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

    @Test fun givenAndroid9UsageText_whenDecoding_thenRecorderUnavailableOnThisReleaseNotAMissingConfig() {
        // Android 9's dump-report does not know --keep_data and --include_current_bucket: it prints
        // its help and exits with 0, so the read succeeds with text instead of a report.
        val usage = "usage: adb shell cmd stats print-stats-log [tag_required] [timestamp_nsec_optional]\n\n" +
            "usage: adb shell cmd stats meminfo\n"

        val failure = assertThrows(WakeReportUnsupportedException::class.java) { WakeStatsCodec.decode(usage.toByteArray(), 1000, 990, 2000, id) }

        assertThat(failure).hasMessageThat().isEqualTo(STATSD_UNAVAILABLE_ON_RELEASE)
        // An IllegalArgumentException would make the collector install the config again.
        assertThat(failure).isNotInstanceOf(IllegalArgumentException::class.java)
        assertThrows(WakeReportUnsupportedException::class.java) { WakeStatsCodec.decode("  Usage: dump-report".toByteArray(), 1000, 990, 2000, id) }
    }

    @Test fun givenBytesThatAreNoProtobuf_whenDecoding_thenNoReportWithWhatArrived() {
        // A length-delimited field 1 that claims more bytes than follow.
        val garbage = byteArrayOf(0x0a, 0x7f, 0x01)

        val failure = assertThrows(IllegalArgumentException::class.java) { WakeStatsCodec.decode(garbage, 1000, 990, 2000, id) }

        assertThat(failure).hasMessageThat().isEqualTo("statsd answered with no report: 3 bytes")
    }

    @Test fun givenErrorText_whenDecoding_thenNoReportWithTheTextNotUnavailableOnThisRelease() {
        // Text that would parse as unknown protobuf fields.
        val error = "Error: permission denied for config 42\nmore\n".toByteArray()

        val failure = assertThrows(IllegalArgumentException::class.java) { WakeStatsCodec.decode(error, 1000, 990, 2000, id) }

        assertThat(failure).hasMessageThat().isEqualTo("statsd answered with no report: Error: permission denied for config 42")
        assertThat(failure).isNotInstanceOf(WakeReportUnsupportedException::class.java)
    }

    @Test fun givenReportWithoutThisConfig_whenDecoding_thenMissingConfig() {
        val other = WakeStats.ReportList.newBuilder().setConfigKey(WakeStats.ConfigKey.newBuilder().setId(id).setUid(0)).build().toByteArray()

        assertThrows(IllegalArgumentException::class.java) { WakeStatsCodec.decode(other, 1000, 990, 2000, id) }
        assertThrows(IllegalArgumentException::class.java) { WakeStatsCodec.decode(ByteArray(0), 1000, 990, 2000, id) }
    }
}
