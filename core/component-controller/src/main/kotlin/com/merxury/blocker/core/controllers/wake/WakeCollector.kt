// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import com.merxury.blocker.core.model.wake.WakeAccess
import com.merxury.blocker.core.model.wake.WakeBatch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.time.ZoneId
import javax.inject.Inject

const val WAKE_START_INFO_SOURCE = "START_INFO"

private const val CLOCK_BOOT = "start-info clock/boot"
private const val CLOCK_UPTIME = "start-info clock/uptime"
private const val CLOCK_ELAPSED = "start-info clock/elapsed"

// How long after boot the event log may begin. logd starts in early init; unmeasured, chosen generously.
private const val BOOT_LOG_DELAY_MILLIS = 120_000L

data class WakeCapture(
    val boot: String,
    val nowElapsed: Long,
    val wallMillis: Long,
    val packages: List<WakePackageUid>,
    val profiles: List<Int>,
    val batches: List<WakeBatch>,
    val binderFallback: Boolean,
    val procstats: List<WakeProcStatsPeriod> = emptyList(),
    val exemptions: WakeExemptionSnapshot? = null,
)

/** Where the previous successful import of this boot ended. */
data class WakeResume(val eventsSinceMillis: Long?, val procstatsPeriod: String?)

/** Each part is null when its command failed. */
data class WakeExemptionSnapshot(
    val idleWhitelist: Map<String, Set<String>>?,
    val standbyBuckets: Map<String, Int>?,
    val backgroundRestricted: Set<String>?,
)

class WakeCollector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val reader: WakeSystemReader,
) {
    private val metadata get() = context.getSharedPreferences("wake_stats_owner", Context.MODE_PRIVATE)

    suspend fun access() = reader.access()

    suspend fun capture(diagnostic: Boolean, access: WakeAccess, resume: suspend (boot: String) -> WakeResume = { WakeResume(null, null) }): WakeCapture {
        val boot = Settings.Global.getString(context.contentResolver, Settings.Global.BOOT_COUNT) ?: "unavailable"
        val packages = mutableListOf<WakePackageUid>()
        val profiles = mutableListOf<Int>()
        val batches = mutableListOf<WakeBatch>()
        val periods = mutableListOf<WakeProcStatsPeriod>()
        var exemptions: WakeExemptionSnapshot? = null
        var binderFallback = false
        try {
            reader.withSession(access) { session ->
                binderFallback = !session.supportsRecorder
                batches += WakeBatch("authorization", emptyList())
                fun WakeCommand.source(profile: Int) = if (profile == 0) name else "$name/$profile"
                fun read(command: WakeCommand, profile: Int = 0, argument: Long = 0, handle: (String) -> Unit): Boolean {
                    val source = command.source(profile)
                    try {
                        val output = session.read(command, profile, argument = argument).decodeToString()
                        check(!output.startsWith("Permission Denial") && !output.startsWith("Can't find service") && !output.contains("DUMP TIMEOUT")) { "Service unavailable" }
                        handle(output)
                        if (batches.none { it.source == source }) batches += WakeBatch(source, emptyList())
                        return true
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        batches += WakeBatch(source, emptyList(), listOf(e.message ?: "unavailable"))
                        return false
                    }
                }
                val cursor = if (boot != "unavailable") resume(boot) else WakeResume(null, null)
                if (boot != "unavailable") {
                    try {
                        batches += readStatsReport(session, boot)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        batches += WakeBatch("statsd", emptyList(), listOf(e.message ?: e.javaClass.simpleName))
                    }
                    readEvents(cursor, ::read)?.let { batches += it }
                } else {
                    batches += WakeBatch("clock", emptyList(), listOf("boot identity unavailable"))
                }
                var current: WakeProcStatsPeriod? = null
                read(WakeCommand.PROCSTATS) { current = WakeProcStats.parse(it, android.os.Build.VERSION.SDK_INT, ZoneId.systemDefault()) }
                current?.let { period ->
                    val previous = cursor.procstatsPeriod
                    // Store the final numbers of the period that committed since the last run.
                    if (previous != null && previous != period.id) {
                        val committed = committedPeriods(previous, ::read)
                        periods += committed
                        if (committed.none { it.id == previous }) {
                            batches += WakeBatch(WakeCommand.PROCSTATS_LAST.name, emptyList(), listOf("committed period $previous unavailable"))
                        }
                    }
                    periods += period
                }
                if (android.os.Build.VERSION.SDK_INT >= 35) {
                    var protected = emptySet<String>()
                    read(WakeCommand.PROTECTED_BROADCASTS) { protected = WakeSystemEvidence.protectedBroadcasts(it) }
                    read(WakeCommand.START_INFO) {
                        val elapsed = SystemClock.elapsedRealtimeNanos()
                        val uptime = SystemClock.uptimeNanos()
                        // The clocks of the previous read narrow when a later monotonic start happened.
                        val previous = if (metadata.getString(CLOCK_BOOT, null) == boot) {
                            metadata.getLong(CLOCK_UPTIME, -1).takeIf { it >= 0 }?.let { it to metadata.getLong(CLOCK_ELAPSED, it) }
                        } else {
                            null
                        }
                        val records = WakeStartInfo.parse(it, elapsed, uptime, System.currentTimeMillis(), protected, previous)
                        batches += WakeBatch(WAKE_START_INFO_SOURCE, records)
                        metadata.edit { putString(CLOCK_BOOT, boot).putLong(CLOCK_UPTIME, uptime).putLong(CLOCK_ELAPSED, elapsed) }
                    }
                } else {
                    batches += WakeBatch(WakeCommand.START_INFO.name, emptyList(), listOf("requires Android 15"))
                }
                read(WakeCommand.USERS) { profiles += WakeSystemEvidence.users(it) }
                for (profile in profiles) {
                    read(WakeCommand.PACKAGES, profile) { packages += WakeSystemEvidence.packages(it) }
                }
                val now = SystemClock.elapsedRealtimeNanos()
                fun snapshot(cmd: WakeCommand, profile: Int = 0) = read(cmd, profile) {
                    batches += WakeBatch(cmd.source(profile), WakeSystemEvidence.snapshot(cmd, it, now, profile))
                }
                for (cmd in listOf(WakeCommand.SERVICES, WakeCommand.PROVIDERS, WakeCommand.PROCESSES)) snapshot(cmd)
                if (diagnostic) {
                    for (cmd in listOf(WakeCommand.INTENTS, WakeCommand.COMPANIONS, WakeCommand.WALLPAPER, WakeCommand.ROLES, WakeCommand.WIDGETS, WakeCommand.JOBS, WakeCommand.ALARMS, WakeCommand.SYNCS, WakeCommand.BATTERY)) snapshot(cmd)
                    var idle: Map<String, Set<String>>? = null
                    var buckets: Map<String, Int>? = null
                    var restricted: Set<String>? = null
                    read(WakeCommand.IDLE_WHITELIST) { idle = WakeSystemEvidence.idleWhitelist(it) }
                    read(WakeCommand.STANDBY_BUCKETS) { buckets = WakeSystemEvidence.standbyBuckets(it) }
                    read(WakeCommand.BACKGROUND_RESTRICTED) { restricted = WakeSystemEvidence.queriedPackages(it) }
                    exemptions = WakeExemptionSnapshot(idle, buckets, restricted)
                }
                for (profile in profiles) {
                    for (cmd in listOf(WakeCommand.INPUT, WakeCommand.ACCESSIBILITY, WakeCommand.LISTENERS, WakeCommand.VPN)) snapshot(cmd, profile)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            batches += WakeBatch("authorization", emptyList(), listOf(e.message ?: "unavailable"))
        }
        return WakeCapture(boot, SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), packages, profiles, batches, binderFallback, periods, exemptions)
    }

    private fun readEvents(cursor: WakeResume, read: (WakeCommand, Int, Long, (String) -> Unit) -> Boolean): WakeBatch? {
        val bootWall = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        // The first run of a boot reads from boot start. Later runs resume at the newest imported event.
        val since = cursor.eventsSinceMillis?.coerceAtLeast(bootWall) ?: bootWall
        var batch: WakeBatch? = null
        if (!read(WakeCommand.EVENTS, 0, since) { output ->
                batch = WakeBatch(SYSTEM_WAKE_EVENTS, WakeSystemEvidence.logcat(output, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos()))
            }
        ) {
            return null
        }
        // Read the head afterwards: if the buffer still reaches the resume point now, it did during the read.
        var head: String? = null
        read(WakeCommand.EVENTS_HEAD, 0, 0) { head = it }
        // logd starts after the kernel, so the first event of a boot is never at the boot instant.
        val gap = WakeSystemEvidence.eventLogGap(since, head, if (cursor.eventsSinceMillis == null) BOOT_LOG_DELAY_MILLIS else 0)
        return batch?.let { events -> gap?.let { events.copy(gaps = listOf(it)) } ?: events }
    }

    private fun committedPeriods(previous: String, read: (WakeCommand, Int, Long, (String) -> Unit) -> Boolean): List<WakeProcStatsPeriod> {
        var count = 0
        if (!read(WakeCommand.PROCSTATS_COUNT, 0, 0) { count = WakeProcStats.committedCount(it) }) return emptyList()
        return WakeProcStats.committed(previous, count) { index ->
            var period: WakeProcStatsPeriod? = null
            read(WakeCommand.PROCSTATS_LAST, 0, index.toLong()) { period = WakeProcStats.parse(it, android.os.Build.VERSION.SDK_INT, ZoneId.systemDefault()) }
            period
        }
    }

    private fun readStatsReport(session: WakeSession, boot: String): WakeBatch {
        check(session.supportsRecorder) { "The system recorder requires the Shizuku user service" }
        check(android.os.Build.VERSION.SDK_INT >= 28) { "statsd metrics unavailable on this Android release" }
        // A statsd configuration is owned by the UID that installed it.
        val legacyOwner = metadata.getInt("uid", -1) == session.uid
        val generationKey = "generation/${session.uid}"
        val installedKey = "key/${session.uid}"
        val priorBoot = metadata.getInt(generationKey, if (legacyOwner) metadata.getInt("generation", session.generation) else session.generation)
        if (priorBoot != session.generation) session.read(WakeCommand.REMOVE, boot = priorBoot)
        val id = WakeStatsCodec.configId(android.os.Process.myUid(), session.generation)
        if (android.os.Build.VERSION.SDK_INT == 28) {
            // Pie ignores metric activations. Retire any old, unbounded config.
            session.read(WakeCommand.REMOVE)
            error("bounded statsd recording requires Android 10")
        }
        val configKey = "$boot/${session.uid}/${WakeStatsCodec.config(id, session.uid).contentHashCode()}"

        // The recorder generation must survive process death before installing the config.
        @SuppressLint("UseKtx")
        fun install() {
            check(metadata.edit().putInt(generationKey, session.generation).commit())
            session.read(WakeCommand.INSTALL)
            check(metadata.edit().putString(installedKey, configKey).commit())
        }
        if (metadata.getString(installedKey, if (legacyOwner) metadata.getString("key", null) else null) != configKey) install()
        fun report(): WakeBatch {
            session.read(WakeCommand.RENEW)
            val bytes = session.read(WakeCommand.REPORT)
            val elapsed = SystemClock.elapsedRealtimeNanos()
            val origin = System.currentTimeMillis() * 1_000_000 - elapsed
            return WakeStatsCodec.decode(bytes, origin, elapsed, session.uid, id)
        }
        return try {
            report()
        } catch (e: IllegalArgumentException) {
            install()
            report().let { it.copy(gaps = it.gaps + "configuration restored") }
        }
    }

    /** Called after the non-destructive copy has committed to Room. Import the returned tail too. */
    suspend fun drain(boot: String, access: WakeAccess): WakeBatch = reader.withSession(access) { session ->
        check(session.generation.toString() == boot && session.supportsRecorder)
        val id = WakeStatsCodec.configId(android.os.Process.myUid(), session.generation)
        val bytes = session.read(WakeCommand.DRAIN)
        val elapsed = SystemClock.elapsedRealtimeNanos()
        WakeStatsCodec.decode(bytes, System.currentTimeMillis() * 1_000_000 - elapsed, elapsed, session.uid, id)
    }
}
