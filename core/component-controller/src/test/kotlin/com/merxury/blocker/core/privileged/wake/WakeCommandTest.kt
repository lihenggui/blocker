// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.wake.proto.WakeStats
import org.junit.Assert.assertThrows
import org.junit.Test

class WakeCommandTest {
    @Test fun givenStealthSnapshotSources_whenBuildingCommands_thenOnlyFixedDumpArgumentsArePassed() {
        assertThat(WakeCommand.OVERLAYS.arguments(0, 10111, 7)).containsExactly("dumpsys", "-t", "5", "window", "windows").inOrder()
        assertThat(WakeCommand.AUDIO.arguments(0, 10111, 7)).containsExactly("dumpsys", "-t", "5", "audio").inOrder()
    }

    @Test fun givenLegacyAndModernSdk_whenListingPackages_thenVersionCodeIsRequestedOnlyWhenSupported() {
        val common = listOf("pm", "list", "packages", "-f", "-U")
        for (sdk in listOf(24, 25)) {
            assertThat(WakeCommand.PACKAGES.arguments(10, 10111, 7, sdk = sdk))
                .containsExactlyElementsIn(common + listOf("--user", "10")).inOrder()
        }
        for (sdk in listOf(26, 35)) {
            assertThat(WakeCommand.PACKAGES.arguments(10, 10111, 7, sdk = sdk))
                .containsExactlyElementsIn(common + listOf("--show-versioncode", "--user", "10")).inOrder()
        }
    }

    @Test fun givenDevicePolicySnapshot_whenBuildingCommand_thenTheDumpCoversAllUsers() {
        assertThat(WakeCommand.DEVICE_POLICY.arguments(10, 10111, 7)).containsExactly("dumpsys", "-t", "5", "device_policy").inOrder()
    }

    @Test fun givenResumePoint_whenBuildingEventCommand_thenTimeReplacesTheCountLimit() {
        val argv = WakeCommand.EVENTS.arguments(0, 10111, 7, 1_795_366_000_045L)
        assertThat(argv).containsAtLeast("-T", "1795366000.045").inOrder()
        assertThat(argv).doesNotContain("-t")
        assertThat(argv.last()).isEqualTo("*:S")
        assertThat(WakeCommand.EVENTS.arguments(0, 10111, 7, 5)).contains("0.005")
        assertThrows(IllegalArgumentException::class.java) { WakeCommand.EVENTS.arguments(0, 10111, 7, -1) }
    }

    @Test fun givenCommittedIndex_whenBuildingProcstatsCommand_thenOnlyAValidatedIndexIsPassed() {
        assertThat(WakeCommand.PROCSTATS_LAST.arguments(0, 10111, 7, 4)).containsExactly("dumpsys", "-t", "10", "procstats", "-c", "--last", "4").inOrder()
        assertThrows(IllegalArgumentException::class.java) { WakeCommand.PROCSTATS_LAST.arguments(0, 10111, 7, 0) }
        assertThrows(IllegalArgumentException::class.java) { WakeCommand.PROCSTATS_LAST.arguments(0, 10111, 7, PROCSTATS_COUNT_PROBE.toLong()) }
        assertThat(WakeCommand.PROCSTATS.arguments(0, 10111, 7)).containsAtLeast("-c", "--current").inOrder()
        assertThat(WakeCommand.PROCSTATS.arguments(0, 10111, 7)).doesNotContain("--checkin")
        assertThat(WakeCommand.BACKGROUND_RESTRICTED.arguments(10, 10111, 7)).containsAtLeast("--user", "10").inOrder()
        assertThat(WakeCommand.STANDBY_BUCKETS.arguments(0, 10111, 7).joinToString(" ")).isEqualTo("am get-standby-bucket --user 0")
        // These options commit, delete or rename procstats history.
        val procstats = WakeCommand.entries.map { it.arguments(0, 10111, 7, 1) }.filter { "procstats" in it }.flatten()
        assertThat(procstats).containsNoneOf("--checkin", "--clear", "--reset", "--commit")
    }

    @Test fun givenEventBufferHead_whenComparingWithResumePoint_thenAWrappedBufferIsAGap() {
        val head = "--------- beginning of events\n         1795365862.412  1000   581   621 I am_proc_start: [0,1,10236,a,b,{a/.B}]"
        assertThat(WakeSystemEvidence.eventLogHead(head)).isEqualTo(1_795_365_862_412L)
        assertThat(WakeSystemEvidence.eventLogGap(1_795_365_862_412L, head)).isNull()
        assertThat(WakeSystemEvidence.eventLogGap(1_795_365_900_000L, head)).isNull()
        assertThat(WakeSystemEvidence.eventLogGap(1_795_365_862_411L, head)).isEqualTo("event log wrapped after resume point")
        assertThat(WakeSystemEvidence.eventLogGap(1L, null)).isEqualTo("event log start unknown")
        // The first read of a boot starts at the boot instant, before logd wrote anything.
        assertThat(WakeSystemEvidence.eventLogGap(1_795_365_800_000L, head, 120_000)).isNull()
        assertThat(WakeSystemEvidence.eventLogGap(1_795_365_700_000L, head, 120_000)).isEqualTo("event log wrapped after resume point")
    }

    @Test fun givenWakeConfig_whenBuilt_thenHighVolumeDeliveriesAreLimitedToColdStarts() {
        val config = WakeStats.Config.parseFrom(WakeStatsCodec.config(WakeStatsCodec.configId(10111, 7), 2000))
        fun matchers(atom: Int) = config.atomMatcherList.single { it.simpleAtomMatcher.atomId == atom }.simpleAtomMatcher.fieldValueMatcherList
        assertThat(matchers(475).map { it.field to it.eqInt }).containsExactly(5 to 3L)
        assertThat(matchers(477).map { it.field to it.eqInt }).containsExactly(3 to 3L)
        // Reviewed paths also match warm service requests.
        assertThat(matchers(476)).isEmpty()
        assertThat(config.atomMatcherList.map { it.simpleAtomMatcher.atomId }).doesNotContain(3)
    }

    @Test fun givenExemptionDumps_whenParsing_thenEachSourceYieldsPackages() {
        val idle = "system-excidle,com.android.providers.calendar,10100\nsystem,com.android.providers.calendar,10100\nuser,example.app,10123\n"
        assertThat(WakeSystemEvidence.idleWhitelist(idle)).containsExactly(
            "com.android.providers.calendar",
            setOf("system-excidle", "system"),
            "example.app",
            setOf("user"),
        )
        assertThat(WakeSystemEvidence.standbyBuckets("com.google.android.youtube: 50\nli: x\n")).containsExactly("com.google.android.youtube", 50)
        assertThat(WakeSystemEvidence.queriedPackages("No operations.\n")).isEmpty()
        assertThat(WakeSystemEvidence.queriedPackages("example.app\nother.app\n")).containsExactly("example.app", "other.app")
    }
}
