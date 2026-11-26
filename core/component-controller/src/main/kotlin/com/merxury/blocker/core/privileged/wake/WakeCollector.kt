// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.wake

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import com.merxury.blocker.core.model.wake.WakeBatch
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.privileged.uid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.time.ZoneId
import javax.inject.Inject

const val WAKE_START_INFO_SOURCE = "START_INFO"

private const val CLOCK_BOOT = "start-info clock/boot"
private const val CLOCK_UPTIME = "start-info clock/uptime"
private const val CLOCK_ELAPSED = "start-info clock/elapsed"

// How long after boot the event log may begin. logd starts in early init; unmeasured, chosen generously.
private const val BOOT_LOG_DELAY_MILLIS = 120_000L

private val SNAPSHOTS = listOf(
    WakeCommand.SERVICES,
    WakeCommand.PROVIDERS,
    WakeCommand.PROCESSES,
    WakeCommand.NOTIFICATIONS,
    WakeCommand.OVERLAYS,
    WakeCommand.AUDIO,
)
private val DIAGNOSTIC_SNAPSHOTS = listOf(
    WakeCommand.INTENTS, WakeCommand.COMPANIONS, WakeCommand.WALLPAPER, WakeCommand.ROLES, WakeCommand.WIDGETS,
    WakeCommand.JOBS, WakeCommand.ALARMS, WakeCommand.SYNCS, WakeCommand.BATTERY,
)
private val PROFILE_SETTINGS = listOf(WakeCommand.INPUT, WakeCommand.ACCESSIBILITY, WakeCommand.LISTENERS, WakeCommand.VPN)

/**
 * One collection.
 *
 * @param channelUnavailable whether the privileged channel could not run some read, because it
 * went away or its privileged process could not be reached; those sources record why
 * @param everyReadRefused whether the channel refused every read as not permitted, as Shizuku
 * before 13 does
 */
data class WakeCapture(
    val boot: String,
    val nowElapsed: Long,
    val wallMillis: Long,
    val packages: List<WakePackageUid>,
    val profiles: List<Int>,
    val batches: List<WakeBatch>,
    val channelUnavailable: Boolean,
    val procstats: List<WakeProcStatsPeriod> = emptyList(),
    val exemptions: WakeExemptionSnapshot? = null,
    val everyReadRefused: Boolean = false,
)

/** Where the previous successful import of this boot ended. */
data class WakeResume(val eventsSinceMillis: Long?, val procstatsPeriod: String?)

/** Each part is null when its command failed. */
data class WakeExemptionSnapshot(
    val idleWhitelist: Map<String, Set<String>>?,
    val standbyBuckets: Map<String, Int>?,
    val backgroundRestricted: Set<String>?,
)

/**
 * Reads the wake feature's evidence through the privileged channel the user chose, only while it
 * is ready, so it never shows a prompt. Each source that cannot be read records why in its batch
 * and the collection goes on with the others.
 */
class WakeCollector @Inject internal constructor(
    @param:ApplicationContext private val context: Context,
    private val executor: PrivilegedExecutor,
) {
    private val metadata get() = context.getSharedPreferences("wake_stats_owner", Context.MODE_PRIVATE)

    private fun generation() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    /**
     * Reads every source as [identity], the identity the channel is ready as: the shell may read
     * all of them. The daily [diagnostic] set adds the snapshots detection does not need every run.
     */
    suspend fun capture(diagnostic: Boolean, identity: PrivilegedIdentity, resume: suspend (boot: String) -> WakeResume = { WakeResume(null, null) }): WakeCapture {
        val started = SystemClock.elapsedRealtime()
        val boot = Settings.Global.getString(context.contentResolver, Settings.Global.BOOT_COUNT) ?: "unavailable"
        val run = CaptureRun(WakeReads(executor, identity.uid, generation()))
        val cursor = if (boot != "unavailable") resume(boot) else WakeResume(null, null)
        if (boot != "unavailable") {
            run.readRecorder(boot)
            readEvents(run, cursor)?.let { run.batches += it }
        } else {
            run.batches += WakeBatch("clock", emptyList(), listOf("boot identity unavailable"))
        }
        readProcstats(run, cursor)
        readStartInfo(run, boot)
        run.read(WakeCommand.USERS) { run.profiles += WakeSystemEvidence.users(it) }
        for (profile in run.profiles) {
            run.read(WakeCommand.PACKAGES, profile) { run.packages += WakeSystemEvidence.packages(it) }
        }
        val now = SystemClock.elapsedRealtimeNanos()
        for (command in SNAPSHOTS) run.snapshot(command, now)
        if (diagnostic) {
            for (command in DIAGNOSTIC_SNAPSHOTS) run.snapshot(command, now)
            readExemptions(run)
        }
        for (profile in run.profiles) {
            for (command in PROFILE_SETTINGS) run.snapshot(command, now, profile)
        }
        // What the channel let this collection read, in words.
        run.batches += WakeBatch("authorization", emptyList(), listOfNotNull(run.reads.accessGap()))
        Timber.i("Wake capture as $identity: ${run.batches.size} sources in ${SystemClock.elapsedRealtime() - started} ms, channel unavailable: ${run.reads.channelUnavailable}")
        return WakeCapture(
            boot, SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), run.packages, run.profiles, run.batches,
            run.reads.channelUnavailable, run.periods, run.exemptions, run.reads.everyReadRefused,
        )
    }

    /** What one capture gathers so far. */
    private inner class CaptureRun(val reads: WakeReads) {
        val packages = mutableListOf<WakePackageUid>()
        val profiles = mutableListOf<Int>()
        val batches = mutableListOf<WakeBatch>()
        val periods = mutableListOf<WakeProcStatsPeriod>()
        var exemptions: WakeExemptionSnapshot? = null

        /**
         * Reads [command] for [profile] and hands its output to [handle]. Records the source as
         * read, or with why it could not be read or parsed. Returns whether it was read.
         */
        suspend fun read(command: WakeCommand, profile: Int = 0, argument: Long = 0, handle: (String) -> Unit): Boolean {
            val source = command.source(profile)
            try {
                handle(reads.text(command, profile, argument))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                batches += WakeBatch(source, emptyList(), listOf(e.message ?: "unavailable"))
                return false
            }
            if (batches.none { it.source == source }) batches += WakeBatch(source, emptyList())
            return true
        }

        suspend fun snapshot(command: WakeCommand, now: Long, profile: Int = 0) = read(command, profile) {
            batches += WakeBatch(command.source(profile), WakeSystemEvidence.snapshot(command, it, now, profile))
        }

        suspend fun readRecorder(boot: String) {
            batches += try {
                readStatsReport(reads, boot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WakeBatch("statsd", emptyList(), listOf(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun WakeCommand.source(profile: Int) = if (profile == 0) name else "$name/$profile"

    private suspend fun readEvents(run: CaptureRun, cursor: WakeResume): WakeBatch? {
        val bootWall = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        // The first run of a boot reads from boot start. Later runs resume at the newest imported event.
        val since = cursor.eventsSinceMillis?.coerceAtLeast(bootWall) ?: bootWall
        var batch: WakeBatch? = null
        val read = run.read(WakeCommand.EVENTS, argument = since) { output ->
            batch = WakeBatch(SYSTEM_WAKE_EVENTS, WakeSystemEvidence.logcat(output, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos()))
        }
        if (!read) return null
        // Read the head afterwards: if the buffer still reaches the resume point now, it did during the read.
        var head: String? = null
        run.read(WakeCommand.EVENTS_HEAD) { head = it }
        // logd starts after the kernel, so the first event of a boot is never at the boot instant.
        val gap = WakeSystemEvidence.eventLogGap(since, head, if (cursor.eventsSinceMillis == null) BOOT_LOG_DELAY_MILLIS else 0)
        return batch?.let { events -> gap?.let { events.copy(gaps = listOf(it)) } ?: events }
    }

    private suspend fun readProcstats(run: CaptureRun, cursor: WakeResume) {
        var current: WakeProcStatsPeriod? = null
        run.read(WakeCommand.PROCSTATS) { current = WakeProcStats.parse(it, Build.VERSION.SDK_INT, ZoneId.systemDefault()) }
        val period = current ?: return
        val previous = cursor.procstatsPeriod
        // Store the final numbers of the period that committed since the last run.
        if (previous != null && previous != period.id) {
            val committed = committedPeriods(run, previous)
            run.periods += committed
            if (committed.none { it.id == previous }) {
                run.batches += WakeBatch(WakeCommand.PROCSTATS_LAST.name, emptyList(), listOf("committed period $previous unavailable"))
            }
        }
        run.periods += period
    }

    private suspend fun committedPeriods(run: CaptureRun, previous: String): List<WakeProcStatsPeriod> {
        var count = 0
        if (!run.read(WakeCommand.PROCSTATS_COUNT) { count = WakeProcStats.committedCount(it) }) return emptyList()
        return WakeProcStats.committed(previous, count) { index ->
            var period: WakeProcStatsPeriod? = null
            run.read(WakeCommand.PROCSTATS_LAST, argument = index.toLong()) { period = WakeProcStats.parse(it, Build.VERSION.SDK_INT, ZoneId.systemDefault()) }
            period
        }
    }

    private suspend fun readStartInfo(run: CaptureRun, boot: String) {
        if (Build.VERSION.SDK_INT < 35) {
            run.batches += WakeBatch(WakeCommand.START_INFO.name, emptyList(), listOf("requires Android 15"))
            return
        }
        var protected = emptySet<String>()
        run.read(WakeCommand.PROTECTED_BROADCASTS) { protected = WakeSystemEvidence.protectedBroadcasts(it) }
        run.read(WakeCommand.START_INFO) {
            val elapsed = SystemClock.elapsedRealtimeNanos()
            val uptime = SystemClock.uptimeNanos()
            // The clocks of the previous read narrow when a later monotonic start happened.
            val previous = if (metadata.getString(CLOCK_BOOT, null) == boot) {
                metadata.getLong(CLOCK_UPTIME, -1).takeIf { it >= 0 }?.let { it to metadata.getLong(CLOCK_ELAPSED, it) }
            } else {
                null
            }
            val records = WakeStartInfo.parse(it, elapsed, uptime, System.currentTimeMillis(), protected, previous)
            run.batches += WakeBatch(WAKE_START_INFO_SOURCE, records)
            metadata.edit { putString(CLOCK_BOOT, boot).putLong(CLOCK_UPTIME, uptime).putLong(CLOCK_ELAPSED, elapsed) }
        }
    }

    private suspend fun readExemptions(run: CaptureRun) {
        var idle: Map<String, Set<String>>? = null
        var buckets: Map<String, Int>? = null
        var restricted: Set<String>? = null
        run.read(WakeCommand.IDLE_WHITELIST) { idle = WakeSystemEvidence.idleWhitelist(it) }
        run.read(WakeCommand.STANDBY_BUCKETS) { buckets = WakeSystemEvidence.standbyBuckets(it) }
        run.read(WakeCommand.BACKGROUND_RESTRICTED) { restricted = WakeSystemEvidence.queriedPackages(it) }
        run.exemptions = WakeExemptionSnapshot(idle, buckets, restricted)
    }

    private suspend fun readStatsReport(reads: WakeReads, boot: String): WakeBatch {
        check(Build.VERSION.SDK_INT >= 28) { STATSD_UNAVAILABLE_ON_RELEASE }
        // A statsd configuration is owned by the UID that installed it: the privileged process's.
        val owner = reads.owner
        val generation = reads.generation
        val legacyOwner = metadata.getInt("uid", -1) == owner
        val generationKey = "generation/$owner"
        val installedKey = "key/$owner"
        val lastRenewedBootKey = "lastRenewedBoot/$owner"
        val lastRenewedElapsedKey = "lastRenewedElapsedNanos/$owner"
        val priorBoot = metadata.getInt(generationKey, if (legacyOwner) metadata.getInt("generation", generation) else generation)
        if (priorBoot != generation) reads.bytes(WakeCommand.REMOVE, boot = priorBoot)
        val id = WakeStatsCodec.configId(Process.myUid(), generation)
        if (Build.VERSION.SDK_INT == 28) {
            // Pie ignores metric activations. Retire any old, unbounded config.
            reads.bytes(WakeCommand.REMOVE)
            error("bounded statsd recording requires Android 10")
        }
        val configKey = "$boot/$owner/${WakeStatsCodec.config(id, owner).contentHashCode()}"

        suspend fun renew() {
            reads.bytes(WakeCommand.RENEW)
            val elapsed = SystemClock.elapsedRealtimeNanos()
            metadata.edit { putString(lastRenewedBootKey, boot).putLong(lastRenewedElapsedKey, elapsed) }
        }

        // The recorder generation must survive process death before installing the config.
        @SuppressLint("UseKtx")
        suspend fun install() {
            check(metadata.edit().putInt(generationKey, generation).commit())
            reads.bytes(WakeCommand.INSTALL)
            renew()
            check(metadata.edit().putString(installedKey, configKey).commit())
        }
        if (metadata.getString(installedKey, if (legacyOwner) metadata.getString("key", null) else null) != configKey) install()
        suspend fun report(): WakeBatch {
            val bytes = reads.bytes(WakeCommand.REPORT)
            val elapsed = SystemClock.elapsedRealtimeNanos()
            val origin = System.currentTimeMillis() * 1_000_000 - elapsed
            val batch = WakeStatsCodec.decode(bytes, origin, elapsed, owner, id)
            val lastRenewed = metadata.getLong(lastRenewedElapsedKey, -1L)
            val expired = metadata.getString(lastRenewedBootKey, null) == boot && lastRenewed >= 0 &&
                elapsed - lastRenewed > WakeStatsCodec.LEASE_SECONDS * 1_000_000_000L
            renew()
            return if (expired) batch.copy(gaps = batch.gaps + "statsd: lease expired") else batch
        }
        return try {
            report()
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "statsd has no report of the wake config; installing it again")
            install()
            report().let { it.copy(gaps = it.gaps + "configuration restored") }
        }
    }

    /**
     * Called after the non-destructive copy has committed to Room. Import the returned tail too.
     * statsd deletes what it returns, so the channel never sends this read twice, and a failure
     * leaves the rest for the next drain.
     *
     * @throws java.io.IOException when the tail cannot be read, with the status to record
     */
    suspend fun drain(boot: String, identity: PrivilegedIdentity): WakeBatch {
        val reads = WakeReads(executor, identity.uid, generation())
        check(reads.generation.toString() == boot) { "The boot changed since the report" }
        val id = WakeStatsCodec.configId(Process.myUid(), reads.generation)
        val bytes = reads.bytes(WakeCommand.DRAIN)
        val elapsed = SystemClock.elapsedRealtimeNanos()
        return WakeStatsCodec.decode(bytes, System.currentTimeMillis() * 1_000_000 - elapsed, elapsed, reads.owner, id)
    }
}
