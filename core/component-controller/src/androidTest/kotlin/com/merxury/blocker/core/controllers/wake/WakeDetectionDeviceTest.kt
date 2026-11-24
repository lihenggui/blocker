// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.ZoneId

/** Runs the read-only detection commands as the shell, as Shizuku started through adb does, and parses the real output. */
class WakeDetectionDeviceTest {
    private fun shell(command: WakeCommand, profile: Int = 0, argument: Long = 0): String {
        // UiAutomation splits on spaces without a shell, so every argument arrives unchanged.
        val argv = command.arguments(profile, android.os.Process.myUid(), 0, argument).joinToString(" ")
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(argv)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }

    @Test fun givenShellAccess_whenReadingProcstats_thenCurrentAndNewestCommittedPeriodsParse() {
        val current = WakeProcStats.parse(shell(WakeCommand.PROCSTATS), Build.VERSION.SDK_INT, ZoneId.systemDefault())
        assertTrue(current.id.isNotEmpty())
        assertTrue(current.endWallMillis >= current.startWallMillis)
        // A device that has not committed a period yet reports no count.
        val count = runCatching { WakeProcStats.committedCount(shell(WakeCommand.PROCSTATS_COUNT)) }.getOrDefault(0)
        assumeTrue("Needs at least two committed procstats periods", count >= 2)
        val committed = WakeProcStats.parse(shell(WakeCommand.PROCSTATS_LAST, argument = count - 1L), Build.VERSION.SDK_INT, ZoneId.systemDefault())
        assertTrue("${committed.id} must precede ${current.id}", committed.id < current.id)
    }

    @Test fun givenShellAccess_whenReadingTheEventLogFromBoot_thenItsStartAndEventsParse() {
        val bootWall = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val output = shell(WakeCommand.EVENTS, argument = bootWall)
        val events = WakeSystemEvidence.logcat(output, System.currentTimeMillis(), SystemClock.elapsedRealtimeNanos())
        assertTrue(events.all { it.eventWallNanos != null && it.eventWallNanos!! >= bootWall * 1_000_000 - 1_000_000_000 })
        assertNotNull(WakeSystemEvidence.eventLogHead(shell(WakeCommand.EVENTS_HEAD)))
    }

    @Test fun givenAndroid15_whenReadingStartInfo_thenRecordsOfThisBootLieInThePast() {
        assumeTrue(Build.VERSION.SDK_INT >= 35)
        val now = SystemClock.elapsedRealtimeNanos()
        val records = WakeStartInfo.parse(shell(WakeCommand.START_INFO), now, SystemClock.uptimeNanos(), System.currentTimeMillis())
        assertTrue(records.all { it.elapsedNanos in 0..now })
        assertTrue(records.all { !it.attributes["signature"].isNullOrEmpty() && !it.attributes["reason"].isNullOrEmpty() })
    }

    @Test fun givenShellAccess_whenReadingExemptions_thenEachSnapshotParses() {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        assertTrue(WakeSystemEvidence.idleWhitelist(shell(WakeCommand.IDLE_WHITELIST)).isNotEmpty())
        assertTrue(WakeSystemEvidence.standbyBuckets(shell(WakeCommand.STANDBY_BUCKETS)).isNotEmpty())
        val restricted = shell(WakeCommand.BACKGROUND_RESTRICTED)
        assertTrue(restricted, !restricted.startsWith("Error"))
        WakeSystemEvidence.queriedPackages(restricted)
    }
}
