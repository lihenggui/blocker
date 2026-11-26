// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.wake.AppWakeSummary
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.WakeAvailability
import com.merxury.blocker.core.model.wake.WakeHeadline
import com.merxury.blocker.core.model.wake.WakeVerdict
import com.merxury.blocker.core.ui.coil.ApkIcon
import org.junit.Test
import kotlin.time.Instant

class WakeOverviewUiStateTest {

    @Test
    fun givenNoAvailability_whenMapping_thenLoading() {
        assertThat(wakeOverviewUiState(null, checking = false)).isEqualTo(WakeOverviewUiState.Loading)
    }

    @Test
    fun givenUnavailableStates_whenMapping_thenMatchingStateWinsOverChecking() {
        assertThat(wakeOverviewUiState(WakeAvailability.NoAccess, checking = true)).isEqualTo(WakeOverviewUiState.NoAccess)
        assertThat(wakeOverviewUiState(WakeAvailability.NotOwnerProfile, checking = true)).isEqualTo(WakeOverviewUiState.NotOwnerProfile)
        assertThat(wakeOverviewUiState(WakeAvailability.ReadsRefused, checking = true)).isEqualTo(WakeOverviewUiState.ReadsRefused)
    }

    @Test
    fun givenCollecting_whenRunningFlagChanges_thenCheckingFollowsIt() {
        assertThat(wakeOverviewUiState(WakeAvailability.Collecting, checking = false)).isEqualTo(WakeOverviewUiState.Collecting(false))
        assertThat(wakeOverviewUiState(WakeAvailability.Collecting, checking = true)).isEqualTo(WakeOverviewUiState.Collecting(true))
    }

    @Test
    fun givenInstalledAndMissingSummaries_whenReady_thenVisibleRowsUseInstalledMetadataAndPreserveOrder() {
        val summaries = listOf(
            summary("watch-z", WakeVerdict.SUSPICIOUS),
            summary("keep-z", WakeVerdict.KEEP_ALIVE),
            summary("removed", WakeVerdict.KEEP_ALIVE),
            summary("keep-b", WakeVerdict.KEEP_ALIVE, blocked = true),
            summary("keep-a", WakeVerdict.KEEP_ALIVE),
            summary("watch-b", WakeVerdict.SUSPICIOUS, blocked = true),
            summary("watch-a", WakeVerdict.SUSPICIOUS),
            summary("removed-blocked", WakeVerdict.KEEP_ALIVE, blocked = true),
            summary("removed-watch", WakeVerdict.SUSPICIOUS),
            summary("normal", WakeVerdict.NORMAL),
        )
        val apps = listOf("keep-a", "keep-b", "keep-z", "watch-a", "watch-b", "watch-z", "normal", "no-summary").map { wakeTestApp(it) }

        val state = wakeOverviewUiState(WakeAvailability.Ready(null), true, summaries, installedApps = apps) as WakeOverviewUiState.Ready

        assertThat(state.keepAlive.map { it.packageName }).containsExactly("keep-z", "keep-a").inOrder()
        assertThat(state.attention.map { it.packageName }).containsExactly("watch-z", "watch-a").inOrder()
        assertThat(state.keepAlive.first()).isEqualTo(
            WakeAppRow("keep-z", "Label for keep-z", ApkIcon("keep-z", "/apk/keep-z", 3, 12345), WakeHeadline.BackgroundStarts(4)),
        )
        assertThat(state.handledKeepAlive).isEqualTo(1)
        assertThat(state.otherApps).isEqualTo(4)
        assertThat(state.checking).isTrue()
    }

    @Test
    fun givenActiveAndInactiveProtection_whenReady_thenOnlyActiveEntriesCount() {
        val blocks = listOf(block("active", active = true), block("pending", active = false), block("allowed", active = false, allowed = true))

        val state = wakeOverviewUiState(WakeAvailability.Ready(100), false, automaticBlocks = blocks)

        assertThat(state).isEqualTo(WakeOverviewUiState.Ready(emptyList(), emptyList(), 0, 1, 0, false))
    }

    private fun summary(name: String, verdict: WakeVerdict, blocked: Boolean = false) = AppWakeSummary(name, verdict, keepAliveDays = 0, suspiciousDays = 0, WakeHeadline.BackgroundStarts(4), allBlocked = blocked)

    private fun block(id: String, active: Boolean, allowed: Boolean = false) = AutomaticWakeBlock(id, "caller", "target", "target.Service", "", active = active, allowed = allowed)
}

internal fun wakeTestApp(name: String, user: UserId = UserId.SYSTEM) = InstalledApp(
    key = AppKey(name, user),
    uid = 12345,
    label = "Label for $name",
    versionName = "1.0",
    versionCode = 3,
    isSystem = false,
    enabled = true,
    enabledSetting = AppEnabledSetting.DEFAULT,
    apkPath = "/apk/$name",
    dataDir = null,
    firstInstallTime = Instant.fromEpochMilliseconds(0),
    lastUpdateTime = Instant.fromEpochMilliseconds(0),
    minSdk = 24,
    targetSdk = 37,
)
