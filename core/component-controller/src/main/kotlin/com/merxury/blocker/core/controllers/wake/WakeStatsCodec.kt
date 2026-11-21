// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import com.google.protobuf.CodedInputStream
import com.google.protobuf.WireFormat
import com.merxury.blocker.core.model.wake.WakeBatch
import com.merxury.blocker.core.model.wake.WakeObservation
import com.merxury.blocker.core.wake.proto.WakeStats
import kotlin.math.abs

private val identifier = Regex("""[A-Za-z0-9_.$:/{}-]{1,420}""")

internal fun wakeAction(value: String?): String? = when {
    value.isNullOrEmpty() -> null
    value.matches(identifier) && "://" !in value -> value
    else -> "<redacted>"
}

internal object WakeStatsCodec {
    // This key is private to Blocker; never remove all shell configurations.
    fun configId(appUid: Int, boot: Int) = 0x424c000000000000L or (appUid.toLong() shl 16) or (boot.toLong() and 0xffff)
    private const val CLOCK_TOLERANCE_NS = 120_000_000_000L

    fun config(id: Long): ByteArray {
        val config = WakeStats.Config.newBuilder().setId(id)
            .addAllowedLogSource("AID_SYSTEM")
            .setHashStringsInMetricReport(false)
        for (id in wakeAtoms.keys) {
            val matcher = id.toLong()
            config.addAtomMatcher(
                WakeStats.Matcher.newBuilder().setId(matcher)
                    .setSimpleAtomMatcher(WakeStats.Simple.newBuilder().setAtomId(id)),
            )
            config.addEventMetric(WakeStats.EventMetric.newBuilder().setId(10000 + matcher).setWhat(matcher))
        }
        return config.build().toByteArray()
    }

    fun decode(bytes: ByteArray, bootWallNs: Long, nowElapsedNs: Long, owner: Int, id: Long): WakeBatch {
        val list = WakeStats.ReportList.parseFrom(bytes)
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
        // App-defined alarm tags and task names can contain account identifiers or URIs.
        if (name == "tag" || name == "job_name") {
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
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
