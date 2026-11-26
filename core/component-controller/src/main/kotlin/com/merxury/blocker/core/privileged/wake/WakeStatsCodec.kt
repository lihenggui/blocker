// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.wake

import com.google.protobuf.CodedInputStream
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.WireFormat
import com.merxury.blocker.core.model.wake.WakeBatch
import com.merxury.blocker.core.model.wake.WakeObservation
import com.merxury.blocker.core.model.wake.normalizeWakeTag
import com.merxury.blocker.core.wake.proto.WakeStats
import java.io.IOException
import kotlin.math.abs

private val identifier = Regex("""[A-Za-z0-9_.$:/{}-]{1,420}""")

internal fun wakeAction(value: String?): String? = when {
    value.isNullOrEmpty() -> null
    value.matches(identifier) && "://" !in value -> value
    else -> "<redacted>"
}

/** The status of the statsd source on a release whose statsd cannot record for the wake feature. */
internal const val STATSD_UNAVAILABLE_ON_RELEASE = "statsd metrics unavailable on this Android release"

/**
 * statsd answered a report request with its usage text, as Android 9 does, with exit code 0, for
 * options it does not know. Installing the config again cannot help, unlike a report that lacks
 * the config.
 */
internal class WakeReportUnsupportedException(cause: Throwable? = null) : IOException(STATSD_UNAVAILABLE_ON_RELEASE, cause)

internal object WakeStatsCodec {
    // This key is private to Blocker; never remove all shell configurations.
    fun configId(appUid: Int, boot: Int) = 0x424c000000000000L or (appUid.toLong() shl 16) or (boot.toLong() and 0xffff)
    private const val CLOCK_TOLERANCE_NS = 120_000_000_000L

    private const val BREADCRUMB = 47

    // Overnight deep Doze can defer the 30-minute WorkManager runs for more than two hours.
    // Keep metrics active overnight while bounding recording after disable or uninstall.
    const val LEASE_SECONDS = 12 * 60 * 60L

    private val JOB_METADATA_PREFIX = Regex("""^(?:#[^#/\s]+#)?(?:@[^@/\s]+@)?""")
    private val JOB_COMPONENT_REGEX = Regex("""[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*/\.?[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*""")

    fun leaseLabel(id: Long) = id.hashCode() and Int.MAX_VALUE

    // ProcessStartType.PROCESS_START_TYPE_COLD in AOSP stats/atoms.proto.
    const val PROCESS_START_TYPE_COLD = 3L

    // proc_start_type of BroadcastDeliveryEventReported and ProviderAcquisitionEventReported.
    // Detection and attribution only join cold deliveries. ServiceRequestEventReported stays
    // unfiltered because reviewed paths match warm service requests too.
    val COLD_START_FIELD = mapOf(475 to 5, 477 to 3)

    fun config(id: Long, owner: Int): ByteArray {
        val config = WakeStats.Config.newBuilder().setId(id)
            .addAllowedLogSource("AID_SYSTEM")
            .addAllowedLogSource("AID_STATSD")
            .setHashStringsInMetricReport(false)
        val heartbeat = WakeStats.Simple.newBuilder().setAtomId(BREADCRUMB)
        for ((field, value) in listOf(1 to owner, 2 to leaseLabel(id), 3 to 1)) {
            heartbeat.addFieldValueMatcher(WakeStats.FieldValueMatcher.newBuilder().setField(field).setEqInt(value.toLong()))
        }
        config.addAtomMatcher(WakeStats.Matcher.newBuilder().setId(BREADCRUMB.toLong()).setSimpleAtomMatcher(heartbeat))
        val activation = WakeStats.EventActivation.newBuilder().setAtomMatcherId(BREADCRUMB.toLong())
            .setTtlSeconds(LEASE_SECONDS).setActivationType(1)
        for (id in wakeAtoms.keys) {
            val matcher = id.toLong()
            val simple = WakeStats.Simple.newBuilder().setAtomId(id)
            COLD_START_FIELD[id]?.let { field ->
                simple.addFieldValueMatcher(WakeStats.FieldValueMatcher.newBuilder().setField(field).setEqInt(PROCESS_START_TYPE_COLD))
            }
            config.addAtomMatcher(WakeStats.Matcher.newBuilder().setId(matcher).setSimpleAtomMatcher(simple))
            config.addMetricActivation(WakeStats.MetricActivation.newBuilder().setMetricId(10000 + matcher).addEventActivation(activation))
            config.addEventMetric(WakeStats.EventMetric.newBuilder().setId(10000 + matcher).setWhat(matcher))
        }
        return config.build().toByteArray()
    }

    /**
     * The observations of report [bytes] of config [id] owned by [owner].
     *
     * @throws WakeReportUnsupportedException when the bytes are statsd's usage text
     * @throws IllegalArgumentException when they are no report, such as an error message, or a
     * report without this config's data; installing the config again may help
     */
    fun decode(bytes: ByteArray, bootWallNs: Long, nowElapsedNs: Long, owner: Int, id: Long): WakeBatch {
        if (isUsageText(bytes)) throw WakeReportUnsupportedException()
        // A report is binary; text, such as an error some ROM prints, would parse as unknown fields.
        require(!isText(bytes)) { "statsd answered with no report: ${answerStart(bytes)}" }
        val list = try {
            WakeStats.ReportList.parseFrom(bytes)
        } catch (e: InvalidProtocolBufferException) {
            throw IllegalArgumentException("statsd answered with no report: ${answerStart(bytes)}", e)
        }
        require(list.hasConfigKey() && list.configKey.id == id && list.configKey.uid == owner) {
            "No report for this configuration owner"
        }
        require(list.reportsList.any { it.metricsCount > 0 }) { "Configuration not registered" }
        val events = mutableListOf<WakeObservation>()
        val gaps = mutableSetOf<String>()
        val subscriptions = mutableSetOf<String>()
        for (report in list.reportsList) {
            if (report.dataCorruptedReasonCount > 0) gaps += "statsd: report loss"
            if (!report.hasCurrentReportWallClockNanos() || !report.hasCurrentReportElapsedNanos() ||
                abs(report.currentReportWallClockNanos - report.currentReportElapsedNanos - bootWallNs) > CLOCK_TOLERANCE_NS
            ) {
                gaps += "statsd: boot or clock discontinuity"
                continue
            }
            for (metric in report.metricsList) {
                val schema = wakeAtoms[(metric.metricId - 10000).toInt()] ?: continue
                subscriptions += schema.name
                if (metric.dataCorruptedReasonCount > 0) gaps += "${schema.name}: data loss"
                if (metric.hasIsActive() && !metric.isActive) gaps += "${schema.name}: inactive"
                for (event in metric.eventMetrics.dataList) {
                    val aggregate = event.aggregatedAtomInfo
                    val times = if (event.hasAggregatedAtomInfo()) aggregate.elapsedTimestampNanosList else listOf(event.elapsedTimestampNanos)
                    val envelope = if (event.hasAggregatedAtomInfo()) aggregate.atom else event.atom
                    val wire = envelope.newCodedInput()
                    val tag = wire.readTag()
                    if (tag != (schema.id shl 3 or 2)) continue
                    val fields = decodeFields(wire.readByteArray(), schema)
                    for (time in times) {
                        if (time in 1..nowElapsedNs) events += observation(schema.name, time, fields)
                    }
                }
            }
        }
        return WakeBatch("statsd", events, gaps.toList(), subscriptions)
    }

    // The first line of an answer that is no report, as printable text, for its status.
    private fun answerStart(bytes: ByteArray): String = bytes.copyOf(minOf(bytes.size, 120)).decodeToString()
        .lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().filter { it in ' '..'~' }.trim().take(80).ifEmpty { "${bytes.size} bytes" }

    private fun isText(bytes: ByteArray): Boolean = bytes.isNotEmpty() &&
        bytes.take(256).all { it in 0x20..0x7e || it == '\n'.code.toByte() || it == '\r'.code.toByte() || it == '\t'.code.toByte() }

    // statsd's help starts "usage: adb shell cmd stats ..." on every release that prints it.
    private fun isUsageText(bytes: ByteArray): Boolean = bytes.copyOf(minOf(bytes.size, 64)).decodeToString().trimStart().startsWith("usage:", ignoreCase = true)

    private fun decodeFields(payload: ByteArray, schema: AtomSchema): Map<String, String> {
        val input = CodedInputStream.newInstance(payload)
        val result = mutableMapOf<String, String>()
        while (!input.isAtEnd) {
            val tag = input.readTag()
            val field = schema.fields.find { it.number == WireFormat.getTagFieldNumber(tag) }
            val wire = WireFormat.getTagWireType(tag)
            val value = when {
                field == null -> null

                field.encoding == Encoding.STRING && wire == 2 -> metadataString(input.readStringRequireUtf8(), field.name)

                field.encoding == Encoding.NUMBER && wire == 0 -> input.readInt64().toString()

                field.encoding == Encoding.NUMBERS && wire == 0 -> input.readInt32().toString()

                field.encoding == Encoding.NUMBERS && wire == 2 -> {
                    val packed = CodedInputStream.newInstance(input.readByteArray())
                    buildList { while (!packed.isAtEnd) add(packed.readInt32()) }.joinToString(",")
                }

                field.encoding == Encoding.UID_CHAIN && wire == 2 -> {
                    val node = CodedInputStream.newInstance(input.readByteArray())
                    var uid: Int? = null
                    while (!node.isAtEnd) {
                        val key = node.readTag()
                        if (key == 8) uid = node.readInt32() else node.skipField(key)
                    }
                    uid?.toString().orEmpty()
                }

                else -> null
            }
            if (field == null || value == null) {
                if (!input.skipField(tag)) break
            } else if (value.isNotBlank()) {
                result[field.name] = result[field.name]?.let { "$it,$value" } ?: value
            }
        }
        return result
    }

    private fun metadataString(value: String, name: String): String {
        if (name == "job_name") {
            val candidate = value.replaceFirst(JOB_METADATA_PREFIX, "")
            if (candidate.length <= 420 && '/' in candidate && "://" !in candidate && candidate.matches(JOB_COMPONENT_REGEX)) return candidate
        }
        // App-defined alarm tags and task names can contain account identifiers or URIs.
        if (name == "tag" || name == "job_name") {
            // Normalize first so one logical alarm or job keeps one digest across runs and versions.
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(normalizeWakeTag(value).toByteArray())
            return digest.take(12).joinToString("") { "%02x".format(it) }
        }
        if (name == "action") return wakeAction(value).orEmpty()
        return value.takeIf { it.matches(identifier) && "://" !in it }.orEmpty()
    }

    private fun observation(kind: String, time: Long, fields: Map<String, String>): WakeObservation {
        val rest = fields.toMutableMap()
        fun number(key: String) = rest.remove(key)?.toIntOrNull()?.takeIf { it >= 0 }
        return WakeObservation(
            kind, time, number("callerUid"), number("targetUid"),
            rest.remove("callerPackage"), rest.remove("package"), rest.remove("component"),
            rest.remove("action"), number("startType"), number("pid"), rest.remove("process"), rest,
        )
    }
}
