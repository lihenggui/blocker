// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WakeStartInfoTest {
    private val dump = checkNotNull(javaClass.getResource("/wake/start-info.txt")).readText()

    // The fixture was captured 1562 s after boot on an emulator that had not slept.
    private val now = 1_562_445_000_000L
    private val wall = 1_795_336_795_445L

    private fun parse(text: String = dump, uptime: Long = now) = WakeStartInfo.parse(text, now, uptime, wall)

    @Test fun givenPersistedHistory_whenParsing_thenOnlyRecordsNamingTheirPackageAreFromThisBoot() {
        val records = parse()
        // Lists are sorted by timestamp across boots, so position says nothing about the boot. A record
        // reloaded from disk has no package name: uid 10139 lists 15450 s and 33.8 s from older boots
        // above the BOOT_COMPLETED broadcast of this boot at 23.1 s.
        assertThat(records.map { it.targetUid to it.attributes["reason"] }).containsExactly(
            10139 to "BROADCAST",
            10154 to "SERVICE",
            2010154 to "JOB",
            2010154 to "SERVICE",
            2010234 to "START ACTIVITY",
            2010234 to "START ACTIVITY",
            2010234 to "START ACTIVITY",
            2010234 to "START ACTIVITY",
        ).inOrder()
        assertThat(records.none { it.targetPackage == "com.android.providers.contacts" }).isTrue()
    }

    @Test fun givenRecordClocks_whenParsing_thenTheForkTimeOrTheServiceLaunchTimeAnchorsIt() {
        val records = parse(uptime = now - 20_000_000_000L)
        // No fork time: a broadcast launch is monotonic, a service launch is elapsed realtime.
        val boot = records.single { it.targetUid == 10139 }
        assertThat(boot.elapsedNanos).isEqualTo(23_130_230_033L)
        assertThat(boot.attributes[WAKE_START_INFO_CLOCK]).isEqualTo("uptime")
        assertThat(boot.attributes[WAKE_START_INFO_SLEEP]).isEqualTo("20000000000")
        // The latest wall time it can have: no sleep after it.
        assertThat(boot.eventWallNanos).isEqualTo(wall * 1_000_000 - (now - 20_000_000_000L - 23_130_230_033L))
        val service = records.single { it.targetUid == 10154 }
        assertThat(service.attributes[WAKE_START_INFO_CLOCK]).isEqualTo("elapsed")
        assertThat(service.elapsedNanos).isEqualTo(16_900_599_141L)
        // With a fork time, every reason is monotonic.
        val job = records.first { it.targetUid == 2010154 }
        assertThat(job.elapsedNanos).isEqualTo(1_536_865_992_162L)
        assertThat(job.attributes[WAKE_START_INFO_CLOCK]).isEqualTo("uptime")
        // After the previous read of this boot, the deep sleep before that read is known to precede the record.
        val previous = 1_000_000_000_000L to 1_015_000_000_000L
        val narrowed = WakeStartInfo.parse(dump, now, now - 20_000_000_000L, wall, previous = previous).first { it.targetUid == 2010154 }
        assertThat(narrowed.elapsedNanos).isEqualTo(1_536_865_992_162L + 15_000_000_000L)
        assertThat(narrowed.attributes[WAKE_START_INFO_SLEEP]).isEqualTo("5000000000")
        // A record before the previous read keeps the whole span.
        val early = WakeStartInfo.parse(dump, now, now - 20_000_000_000L, wall, previous = previous).single { it.targetUid == 10139 }
        assertThat(early.elapsedNanos).isEqualTo(23_130_230_033L)
        // A monotonic time beyond this boot's uptime cannot be from it.
        assertThat(parse(uptime = 1_000_000_000_000L).map { it.targetUid }).doesNotContain(2010234)
    }

    @Test fun givenJobWithoutForkTime_whenTheDeviceSlept_thenItsLaunchIsElapsedRealtime() {
        val text = dump.replace("timestamps: 0=1536851780187 1=1536865992162 2=1536916773210 3=1536890025909", "timestamps: 0=1536851780187")
        // The launch is later than the uptime of a device that slept, and still a start of this boot.
        val job = parse(text, uptime = 1_000_000_000_000L).single { it.attributes["reason"] == "JOB" }
        assertThat(job.elapsedNanos).isEqualTo(1_536_851_780_187L)
        assertThat(job.attributes[WAKE_START_INFO_CLOCK]).isEqualTo("elapsed")
    }

    @Test fun givenIsolatedProcess_whenParsing_thenItIsNotAStartOfItsApp() {
        val text = dump.replace("pid=0 realUid=10154 packageUid=10154", "pid=0 realUid=99003 packageUid=10154")
        assertThat(parse(text).map { it.targetUid }).doesNotContain(10154)
        assertThat(parse().map { it.targetUid }).contains(10154)
    }

    @Test fun givenRecords_whenParsing_thenReasonsIntentsAndStartTypesAreDecoded() {
        val records = parse()
        val boot = records.single { it.targetUid == 10139 }
        assertThat(boot.kind).isEqualTo(WAKE_START_INFO)
        assertThat(boot.targetPackage).isEqualTo("com.google.android.apps.restore")
        assertThat(boot.action).isEqualTo("android.intent.action.BOOT_COMPLETED")
        assertThat(boot.component).isEqualTo("com.google.android.apps.restore/androidx.work.impl.background.systemalarm.RescheduleReceiver")
        assertThat(boot.startType).isEqualTo(3)
        assertThat(boot.pid).isNull()
        assertThat(WakeStartInfo.parse(dump, now, now, wall, setOf("android.intent.action.BOOT_COMPLETED")).single { it.targetUid == 10139 }.attributes["protected_broadcast"])
            .isEqualTo("true")

        val activity = records.first { it.targetPackage == "com.example.check" }
        assertThat(activity.attributes).containsAtLeast("reason", "START ACTIVITY", "was_force_stopped", "true")
        assertThat(activity.processName).isEqualTo("com.example.check")
    }

    @Test fun givenLaterReadOfTheSameStart_whenFirstFrameArrives_thenTheSignatureIsStable() {
        val first = parse().first { it.targetPackage == "com.example.check" }
        val updated = WakeStartInfo.parse(dump.replaceFirst("startupState=2 reason=START ACTIVITY", "startupState=0 reason=START ACTIVITY"), now + 1, now + 1, wall + 1)
            .first { it.targetPackage == "com.example.check" }
        assertThat(updated.attributes["signature"]).isEqualTo(first.attributes["signature"])
    }
}
