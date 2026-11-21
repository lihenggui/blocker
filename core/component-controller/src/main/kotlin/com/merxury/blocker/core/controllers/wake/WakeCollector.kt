// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.merxury.blocker.core.model.wake.SYSTEM_WAKE_EVENTS
import com.merxury.blocker.core.model.wake.WakeBatch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

data class WakeCapture(
    val boot: String,
    val nowElapsed: Long,
    val wallMillis: Long,
    val packages: List<WakePackageUid>,
    val profiles: List<Int>,
    val batches: List<WakeBatch>,
)

class WakeCollector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val reader: WakeSystemReader,
) {
    private val metadata get() = context.getSharedPreferences("wake_stats_owner", Context.MODE_PRIVATE)

    suspend fun access() = reader.access()

    suspend fun capture(diagnostic: Boolean, access: WakeAccess): WakeCapture {
        val boot = Settings.Global.getString(context.contentResolver, Settings.Global.BOOT_COUNT) ?: "unavailable"
        val packages = mutableListOf<WakePackageUid>()
        val profiles = mutableListOf<Int>()
        val batches = mutableListOf<WakeBatch>()
        try {
            reader.withSession(access) { session ->
                batches += WakeBatch("authorization", emptyList())
                fun read(command: WakeCommand, profile: Int = 0, handle: (String) -> Unit) {
                    val source = if (profile == 0) command.name else "${command.name}/$profile"
                    try {
                        val output = session.read(command, profile).decodeToString()
                        check(!output.startsWith("Permission Denial") && !output.startsWith("Can't find service") && !output.contains("DUMP TIMEOUT")) { "Service unavailable" }
                        handle(output)
                        if (batches.none { it.source == source }) batches += WakeBatch(source, emptyList())
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        batches += WakeBatch(source, emptyList(), listOf(e.message ?: "unavailable"))
                    }
                }
                if (boot != "unavailable") {
                    try {
                        check(session.supportsRecorder) { "The system recorder requires the Shizuku user service" }
                        check(android.os.Build.VERSION.SDK_INT >= 28) { "statsd metrics unavailable on this Android release" }
                        // A statsd configuration is owned by the UID that installed it.
                        val legacyOwner = metadata.getInt("uid", -1) == session.uid
                        val generationKey = "generation/${session.uid}"
                        val installedKey = "key/${session.uid}"
                        val priorBoot = metadata.getInt(generationKey, if (legacyOwner) metadata.getInt("generation", session.generation) else session.generation)
                        if (priorBoot != session.generation) session.read(WakeCommand.REMOVE, boot = priorBoot)
                        val id = WakeStatsCodec.configId(android.os.Process.myUid(), session.generation)
                        val configKey = "$boot/${session.uid}/${WakeStatsCodec.config(id).contentHashCode()}"
                        fun install() {
                            check(metadata.edit().putInt(generationKey, session.generation).commit())
                            session.read(WakeCommand.INSTALL)
                            check(metadata.edit().putString(installedKey, configKey).commit())
                        }
                        if (metadata.getString(installedKey, if (legacyOwner) metadata.getString("key", null) else null) != configKey) install()
                        fun report(): WakeBatch {
                            val bytes = session.read(WakeCommand.REPORT)
                            val elapsed = SystemClock.elapsedRealtimeNanos()
                            val origin = System.currentTimeMillis() * 1_000_000 - elapsed
                            return WakeStatsCodec.decode(bytes, origin, elapsed, session.uid, id)
                        }
                        batches += try {
                            report()
                        } catch (e: IllegalArgumentException) {
                            install()
                            report().let { it.copy(gaps = it.gaps + "configuration restored") }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        batches += WakeBatch("statsd", emptyList(), listOf(e.message ?: "unavailable"))
                    }
                    read(WakeCommand.EVENTS) { output ->
                        batches += WakeBatch(SYSTEM_WAKE_EVENTS, WakeSystemEvidence.logcat(output), listOf("bounded event log"))
                    }
                } else {
                    batches += WakeBatch("clock", emptyList(), listOf("boot identity unavailable"))
                }
                read(WakeCommand.USERS) { profiles += WakeSystemEvidence.users(it) }
                for (profile in profiles) {
                    read(WakeCommand.PACKAGES, profile) { packages += WakeSystemEvidence.packages(it) }
                }
                val now = SystemClock.elapsedRealtimeNanos()
                fun snapshot(cmd: WakeCommand, profile: Int = 0) = read(cmd, profile) {
                    val name = if (profile == 0) cmd.name else "${cmd.name}/$profile"
                    batches += WakeBatch(name, WakeSystemEvidence.snapshot(cmd, it, now, profile))
                }
                for (cmd in listOf(WakeCommand.SERVICES, WakeCommand.PROVIDERS, WakeCommand.PROCESSES)) snapshot(cmd)
                if (diagnostic) {
                    for (cmd in listOf(WakeCommand.INTENTS, WakeCommand.COMPANIONS, WakeCommand.WALLPAPER, WakeCommand.ROLES, WakeCommand.WIDGETS, WakeCommand.JOBS, WakeCommand.ALARMS, WakeCommand.SYNCS, WakeCommand.BATTERY)) snapshot(cmd)
                }
                for (profile in profiles) {
                    for (cmd in listOf(WakeCommand.INPUT, WakeCommand.ACCESSIBILITY, WakeCommand.LISTENERS, WakeCommand.VPN)) snapshot(cmd, profile)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            batches += WakeBatch("authorization", emptyList(), listOf(e.message ?: "unavailable"))
        }
        return WakeCapture(boot, SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), packages, profiles, batches)
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
