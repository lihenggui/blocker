// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import com.merxury.blocker.core.model.component.appIdOfUid
import com.merxury.blocker.core.model.wake.WakeObservation
import java.security.MessageDigest

const val WAKE_START_INFO = "ApplicationStartInfo"

/** Timestamp clock of a start-info record: `uptime` (CLOCK_MONOTONIC) or `elapsed` (with deep sleep). */
const val WAKE_START_INFO_CLOCK = "clock"

/**
 * How much later than its recorded elapsed time an `uptime` record may have happened: the deep sleep
 * between the previous read of this boot (or boot) and this read. Zero for `elapsed` records.
 */
const val WAKE_START_INFO_SLEEP = "sleep_ns"

/**
 * Decodes `dumpsys activity start-info` (AppStartInfoTracker, Android 15).
 *
 * The tracker keeps each list sorted by launch timestamp, mixing boots, and persists it without the
 * package name (ApplicationStartInfo.writeToProto). A record whose `package=` is null was therefore
 * loaded from disk at boot and belongs to an earlier boot; a record created since boot names its
 * package. Only those are returned.
 *
 * Timestamps are CLOCK_MONOTONIC, which stops in deep sleep, except the launch time of a service
 * start, which ActiveServices takes from elapsedRealtimeNanos. The fork time, monotonic for every
 * reason, anchors the record when present.
 */
internal object WakeStartInfo {
    private val header = Regex("""^package=(\S+) process=(\S+) startupState=(\d+) reason=(.*?) startType=(\S*) launchMode=(\d+) wasForceStopped=(true|false)""")
    private val ids = Regex("""(?:^|\s)(realUid|packageUid|definingUid|user)=(-?\d+)""")
    private val token = Regex("""(?:\{|\s)(act|cmp)=([^\s}]+)""")
    private val timestamp = Regex("""(\d+)=(\d+)""")

    // ApplicationStartInfo.startTypeToString mapped onto ProcessStartType of the statsd atoms.
    private val startTypes = mapOf("COLD" to 3, "WARM" to 1, "HOT" to 2)

    private class Draft(val pkg: String) {
        var uid: Int? = null
        var realUid: Int? = null
        var fields: MatchResult? = null
        var action: String? = null
        var component: String? = null
        var times: Map<Int, Long> = emptyMap()
    }

    /**
     * [protectedBroadcasts] marks broadcast starts whose action only the system may send. [previous] is
     * the (uptime, elapsed) clock pair of the previous read in this boot: a monotonic time after it had at
     * least that read's deep sleep before it.
     */
    fun parse(
        output: String,
        nowElapsedNanos: Long,
        nowUptimeNanos: Long,
        nowWallMillis: Long,
        protectedBroadcasts: Set<String> = emptySet(),
        previous: Pair<Long, Long>? = null,
    ): List<WakeObservation> {
        val result = mutableListOf<WakeObservation>()
        val sleep = (nowElapsedNanos - nowUptimeNanos).coerceAtLeast(0)
        var pkg: String? = null
        var draft: Draft? = null
        fun finish() {
            val record = draft ?: return
            draft = null
            val fields = record.fields?.groupValues ?: return
            val uid = record.uid ?: return
            // An isolated or sandbox process of a running app is not a start of the app.
            if (record.realUid?.let { appIdOfUid(it) !in 10_000..19_999 } == true) return
            // Created before this boot: the tracker reloaded it from disk without its package name.
            if (fields[1] == "null") return
            val reason = fields[4]
            // START_TIMESTAMP_LAUNCH is recorded when the start is created and never changes.
            val launch = record.times[0] ?: record.times.values.minOrNull() ?: return
            val fork = record.times[1]
            val (anchor, elapsedClock) = when {
                fork != null -> fork to false

                // ActiveServices stamps a service start, job services included, with the elapsed clock.
                reason == "SERVICE" || reason == "JOB" -> launch to true

                else -> launch to false
            }
            if (anchor > (if (elapsedClock) nowElapsedNanos else nowUptimeNanos)) return
            val process = fields[2]
            val signature = "$uid|$process|$reason|$launch".toByteArray()
            // The elapsed time lies in [anchor + floor, anchor + sleep] for a monotonic record.
            val floor = previous?.takeIf { !elapsedClock && anchor >= it.first }?.let { (it.second - it.first).coerceIn(0, sleep) } ?: 0
            // An upper bound of the wall time: a monotonic record cannot be later than if no sleep followed it.
            val wall = nowWallMillis * 1_000_000 - ((if (elapsedClock) nowElapsedNanos else nowUptimeNanos) - anchor)
            result += WakeObservation(
                WAKE_START_INFO,
                anchor + floor,
                targetUid = uid,
                targetPackage = record.pkg,
                component = record.component,
                action = wakeAction(record.action),
                startType = startTypes[fields[5]],
                processName = process,
                attributes = mapOf(
                    "reason" to reason,
                    "was_force_stopped" to fields[7],
                    "signature" to MessageDigest.getInstance("SHA-256").digest(signature).take(12).joinToString("") { "%02x".format(it) },
                    WAKE_START_INFO_CLOCK to if (elapsedClock) "elapsed" else "uptime",
                    WAKE_START_INFO_SLEEP to (if (elapsedClock) 0 else sleep - floor).toString(),
                ) + if (reason == "BROADCAST" && record.action in protectedBroadcasts) mapOf("protected_broadcast" to "true") else emptyMap(),
                eventWallNanos = wall,
            )
        }
        for (raw in output.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("package: ") -> {
                    finish()
                    pkg = line.substringAfter("package: ").trim()
                }

                line.startsWith("Historical Process Start for userId=") -> finish()

                line.startsWith("ApplicationStartInfo ") -> {
                    finish()
                    draft = pkg?.let(::Draft)
                }

                line.startsWith("pid=") -> draft?.let { record ->
                    val values = ids.findAll(line).associate { it.groupValues[1] to it.groupValues[2].toIntOrNull() }
                    record.uid = values["packageUid"]
                    record.realUid = values["realUid"]
                }

                line.startsWith("package=") -> draft?.fields = header.find(line)

                line.startsWith("intent=") -> draft?.let { record ->
                    for (match in token.findAll(line)) {
                        when (match.groupValues[1]) {
                            "act" -> record.action = match.groupValues[2]
                            "cmp" -> record.component = match.groupValues[2]
                        }
                    }
                }

                line.startsWith("timestamps:") -> draft?.let { record ->
                    record.times = timestamp.findAll(line.substringAfter(':')).associate { it.groupValues[1].toInt() to it.groupValues[2].toLong() }
                }
            }
        }
        finish()
        return result
    }
}
