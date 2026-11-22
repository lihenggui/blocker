// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** One procstats period. Durations are cumulative within it, so a later read replaces an earlier one. */
data class WakeProcStatsPeriod(
    val id: String,
    val startWallMillis: Long,
    val endWallMillis: Long,
    val holds: List<WakeProcStatsHold>,
    val associations: List<WakeProcStatsAssociation>,
)

/**
 * Uptime an app process spent alive while neither user-visible nor cached or frozen, and the part of
 * it spent in the foreground service state. A process hosting several packages is shared evenly
 * between them. From Android 15 the screen split is the period's, not the process's own.
 */
data class WakeProcStatsHold(
    val target: String,
    val uid: Int,
    val process: String,
    val screenOffMillis: Long,
    val screenOnMillis: Long,
    val fgsScreenOffMillis: Long = 0,
    val fgsScreenOnMillis: Long = 0,
)

/** A binding or provider connection from another process: how long it existed, and how long its client was above cached. */
data class WakeProcStatsAssociation(
    val target: String,
    val uid: Int,
    val component: String,
    val sourceProcess: String,
    val sourceUid: Int,
    val boundMillis: Long,
    val activeMillis: Long,
)

/** Decodes `dumpsys procstats -c` (ProcessStats.dumpCheckinLocked). */
internal object WakeProcStats {
    private const val CHECKIN_VERSION = "5"
    private const val NEWEST_VERIFIED_SDK = 37
    private val clock = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
    private val count = Regex("""Only have (\d+) data sets""")

    /**
     * Every release prints `vers,5`, but the meaning of a state letter depends on the release
     * (DumpUtils.STATE_TAGS). Each table lists the letters of that release and the subset that is
     * alive without being visible: persistent, top, bound-top, home, last-activity, cached and frozen
     * are excluded, and so is service-restarting, which ProcessState records for a dead process.
     * Android 12 and 13 merge bound-top with bound-FGS in "d", so it cannot count as a hold.
     * [foreground] is the process's own foreground service state, "g" from Android 12; before that a
     * foreground service shares "f" with other important foreground causes.
     */
    private data class StateTable(val letters: Set<Char>, val held: Set<Char>, val foreground: Set<Char>)

    private val legacy = StateTable("ptfbuwsxrhlace".toSet(), "fbuswr".toSet(), emptySet())
    private val snowCone = StateTable("ptdgfbuwsxrhlace".toSet(), "gfbuswr".toSet(), setOf('g'))
    private val upsideDown = StateTable("ptyzgfbusxrwhlae".toSet(), "gyfbusrw".toSet(), setOf('g'))

    private fun table(sdk: Int) = when (sdk) {
        in 24..30 -> legacy
        in 31..33 -> snowCone
        in 34..NEWEST_VERIFIED_SDK -> upsideDown
        else -> throw IllegalArgumentException("procstats: state tags unverified for API $sdk")
    }

    private const val MAX_COMMITTED_READS = 3

    /**
     * From Android 15 a process takes a screen change only when its own state changes next.
     * AppProfiler.updateLowMemStateLSP no longer applies a new memory factor to every process
     * (android14-release still does; android15-release and main compute `allChanged` but do not use
     * it), so a process held throughout books its time to an old screen state. The period's own
     * screen durations, the "total" line, are booked on every change, so held time is split by them.
     */
    private const val GLOBAL_SCREEN_SPLIT_SDK = 35

    fun committedCount(output: String): Int = count.find(output)?.groupValues?.get(1)?.toInt()
        ?: throw IllegalArgumentException("procstats: committed period count unavailable")

    /**
     * `--last N` indexes committed files oldest first and refuses index 0. Walk back from the newest
     * until the period last seen as current, so its final numbers replace the partial ones. Newer
     * committed periods found on the way are returned too.
     */
    fun committed(previous: String, count: Int, read: (Int) -> WakeProcStatsPeriod?): List<WakeProcStatsPeriod> {
        val found = mutableListOf<WakeProcStatsPeriod>()
        for (index in (count - 1) downTo maxOf(1, count - MAX_COMMITTED_READS)) {
            val period = read(index) ?: break
            if (period.id < previous) break
            found += period
            if (period.id == previous) break
        }
        return found
    }

    fun parse(output: String, sdk: Int, zone: ZoneId): WakeProcStatsPeriod {
        val states = table(sdk)
        var version: String? = null
        var period: List<String>? = null
        val held = mutableMapOf<Triple<String, Int, String>, LongArray>()
        val processes = mutableMapOf<Pair<Int, String>, LongArray>()
        var screen: LongArray? = null
        val associations = mutableMapOf<List<Any>, LongArray>()
        for (line in output.lineSequence()) {
            val fields = line.split(',')
            when (fields[0]) {
                "err" -> throw IllegalArgumentException("procstats: ${line.substringAfter(',').take(120)}")

                "vers" -> version = fields.getOrNull(1)

                "period" -> period = fields

                "total" -> if (sdk >= GLOBAL_SCREEN_SPLIT_SDK) {
                    val totals = LongArray(2)
                    for (entry in fields.drop(1)) {
                        val tag = entry.substringBefore(':')
                        val millis = entry.substringAfter(':', "").toLongOrNull()
                        require(tag.length == 2 && millis != null && millis >= 0 && tag[0] in "01" && tag[1] in "nmlc") { "procstats: malformed total $tag" }
                        totals[tag[0] - '0'] += millis
                    }
                    screen = totals
                }

                // The process itself, once, however many packages it hosts.
                "proc" -> {
                    if (fields.size < 3) continue
                    val uid = fields[2].toIntOrNull() ?: continue
                    if (!isRegularApp(uid)) continue
                    accumulate(states, sdk, fields.drop(3), processes.getOrPut(uid to fields[1]) { LongArray(4) })
                }

                "pkgproc" -> {
                    if (fields.size < 5) continue
                    val uid = fields[2].toIntOrNull() ?: continue
                    if (!isRegularApp(uid)) continue
                    val pkg = fields[1]
                    val process = expand(pkg, fields[4])
                    accumulate(states, sdk, fields.drop(5), held.getOrPut(Triple(pkg, uid, process)) { LongArray(4) })
                }

                "pkgasc" -> {
                    if (fields.size < 10) continue
                    val uid = fields[2].toIntOrNull() ?: continue
                    val sourceUid = fields[6].toIntOrNull() ?: continue
                    // An app's own processes holding each other count too.
                    if (!isRegularApp(uid)) continue
                    // count, bound duration and active count precede the active time per client state.
                    val bound = fields[8].toLongOrNull()?.coerceAtLeast(0) ?: 0
                    val active = fields.drop(10).sumOf { it.substringAfter(':', "").toLongOrNull()?.coerceAtLeast(0) ?: 0 }
                    if (bound == 0L && active == 0L) continue
                    val pkg = fields[1]
                    val key = listOf(pkg, uid, expand(pkg, fields[4]), fields[5], sourceUid)
                    val totals = associations.getOrPut(key) { LongArray(2) }
                    totals[0] += bound
                    totals[1] += active
                }
            }
        }
        require(version != null) { "procstats: no checkin header" }
        require(version == CHECKIN_VERSION) { "procstats: unsupported checkin version $version" }
        val header = requireNotNull(period) { "procstats: no period" }
        require(header.size >= 4) { "procstats: malformed period" }
        val start = try {
            LocalDateTime.parse(header[1], clock).atZone(zone).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("procstats: unreadable period start", e)
        }
        val startRealtime = header[2].toLongOrNull()
        val endRealtime = header[3].toLongOrNull()
        require(startRealtime != null && endRealtime != null && endRealtime >= startRealtime) { "procstats: malformed period" }
        shareSharedProcesses(held, processes)
        if (sdk >= GLOBAL_SCREEN_SPLIT_SDK) {
            val totals = requireNotNull(screen) { "procstats: no total line" }
            held.replaceAll { _, process -> splitByScreen(process, totals[0], totals[0] + totals[1]) }
        }
        return WakeProcStatsPeriod(
            header[1],
            start,
            start + (endRealtime - startRealtime),
            held.mapNotNull { (key, totals) ->
                if (totals[0] + totals[1] == 0L) null else WakeProcStatsHold(key.first, key.second, key.third, totals[0], totals[1], totals[2], totals[3])
            },
            associations.map { (key, totals) ->
                WakeProcStatsAssociation(key[0] as String, key[1] as Int, key[2] as String, key[3] as String, key[4] as Int, totals[0], totals[1])
            },
        )
    }

    private fun accumulate(states: StateTable, sdk: Int, entries: List<String>, totals: LongArray) {
        for (entry in entries) {
            val tag = entry.substringBefore(':')
            val millis = entry.substringAfter(':', "").toLongOrNull()
            require(tag.length == 3 && millis != null && millis >= 0 && tag[0] in "01" && tag[1] in "nmlc" && tag[2] in states.letters) {
                "procstats: unknown state $tag for API $sdk"
            }
            if (tag[2] in states.held) totals[tag[0] - '0'] += millis
            if (tag[2] in states.foreground) totals[2 + (tag[0] - '0')] += millis
        }
    }

    /**
     * A process that hosts several packages (android.process.acore, android.process.media) is listed
     * under each of them with the whole process time. Which package keeps it cannot be told apart, so
     * each gets an even share of the process's own time and their sum is what procstats reports for it.
     */
    private fun shareSharedProcesses(held: MutableMap<Triple<String, Int, String>, LongArray>, processes: Map<Pair<Int, String>, LongArray>) {
        for ((process, keys) in held.keys.groupBy { it.second to it.third }) {
            if (keys.size < 2) continue
            val whole = processes[process] ?: LongArray(4) { column -> keys.maxOf { held.getValue(it)[column] } }
            keys.sortedBy { it.first }.forEachIndexed { index, key ->
                held[key] = LongArray(4) { column -> whole[column] / keys.size + if (index < whole[column] % keys.size) 1 else 0 }
            }
        }
    }

    /** Splits held and foreground service time by the period's screen-off share. */
    private fun splitByScreen(process: LongArray, off: Long, total: Long): LongArray {
        fun offPart(millis: Long) = if (total == 0L) 0L else millis / total * off + millis % total * off / total
        val held = process[0] + process[1]
        val foreground = process[2] + process[3]
        return longArrayOf(offPart(held), held - offPart(held), offPart(foreground), foreground - offPart(foreground))
    }

    // ProcessProfileRecord.getUidForAttribution files isolated processes under the host app uid, so
    // they are already part of its rows. SDK sandbox processes keep their own uid and are skipped.
    private fun isRegularApp(uid: Int) = uid % 100_000 in 10_000..19_999

    // DumpUtils.collapseString drops the package prefix of names inside the package.
    private fun expand(pkg: String, name: String) = when {
        name.isEmpty() -> pkg
        name.startsWith('.') -> pkg + name
        else -> name
    }
}
