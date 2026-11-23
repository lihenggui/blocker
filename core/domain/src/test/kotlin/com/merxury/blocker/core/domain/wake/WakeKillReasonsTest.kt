// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WakeKillReasonsTest {
    @Test fun givenAmKillReasons_whenClassifying_thenOnlyKillsThatWantedTheAppGoneQualify() {
        val qualifying = listOf(
            "stop com.example due to from pid 23833",
            "stop com.example due to from pid 4321 (com.android.settings)",
            "fully stop com.example/0 by user request",
            "remove task",
            "kill background",
            "kill all background",
            "kill all background except",
            "empty",
            "empty #17",
            "empty for 1800s",
            "cached #33",
            "swap low and too many cached",
            "excessive cpu 12000 during 300000 dur=600000 limit=2",
            "excessive binder traffic during cached",
            "cached idle & background restricted",
            "Too many outgoing broadcasts in cached state",
        )
        // Observed on an API 35 emulator or taken from the Android 15 kill sites.
        val housekeeping = listOf(
            "stop user 34 due to finish user",
            "stop com.example due to deletePackageX",
            "stop com.example due to installPackageLI",
            "stop com.example due to clear data",
            "stop com.example due to pkg removed",
            "change com.example",
            "isolated not needed",
            "sandbox not needed",
            "depends on provider com.google.android.gms/.core.providersettings.GoogleSettingsProvider in dying proc com.google.android.gms (adj -10000)",
            "crash",
            "anr",
            "bg anr",
            "start timeout",
            "Unable to freeze binder interface",
            "Can't deliver broadcast",
            "kill",
        )
        assertThat(qualifying.filterNot(WakeKillReasons::qualifies)).isEmpty()
        assertThat(housekeeping.filter(WakeKillReasons::qualifies)).isEmpty()
    }

    @Test fun givenExitReasonCodes_whenClassifying_thenMemoryResourceAndUserRemovalQualify() {
        assertThat(WakeKillReasons.qualifies(3, 30)).isTrue() // LOW_MEMORY, OOM_KILL
        assertThat(WakeKillReasons.qualifies(9, 7)).isTrue() // EXCESSIVE_RESOURCE_USAGE, EXCESSIVE_CPU
        assertThat(WakeKillReasons.qualifies(10, 22)).isTrue() // USER_REQUESTED, REMOVE_TASK
        assertThat(WakeKillReasons.qualifies(10, 24)).isTrue() // USER_REQUESTED, KILL_BACKGROUND
        assertThat(WakeKillReasons.qualifies(13, 3)).isTrue() // OTHER, TOO_MANY_EMPTY
        assertThat(WakeKillReasons.qualifies(13, 18)).isTrue() // OTHER, CACHED_IDLE_FORCED_APP_STANDBY
        // forceStopPackageLocked also tags clear data and package removal as USER_REQUESTED/FORCE_STOP.
        assertThat(WakeKillReasons.qualifies(10, 21)).isFalse()
        assertThat(WakeKillReasons.qualifies(11, 0)).isFalse() // USER_STOPPED
        assertThat(WakeKillReasons.qualifies(13, 17)).isFalse() // OTHER, ISOLATED_NOT_NEEDED
        assertThat(WakeKillReasons.qualifies(15, 0)).isFalse() // PACKAGE_STATE_CHANGE
        assertThat(WakeKillReasons.qualifies(16, 25)).isFalse() // PACKAGE_UPDATED
        assertThat(WakeKillReasons.qualifies(4, 0)).isFalse() // CRASH
        assertThat(WakeKillReasons.qualifies(null, null)).isFalse()
        assertThat(WakeKillReasons.name(13, 3)).isEqualTo("REASON_OTHER:SUBREASON_TOO_MANY_EMPTY")
        assertThat(WakeKillReasons.name(3, 0)).isEqualTo("REASON_LOW_MEMORY")
    }
}
