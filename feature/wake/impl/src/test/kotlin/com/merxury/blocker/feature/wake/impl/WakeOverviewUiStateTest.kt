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
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeHeadline
import com.merxury.blocker.core.model.wake.WakeVerdict
import com.merxury.blocker.core.ui.coil.ApkIcon
import org.junit.Test
import kotlin.time.Instant

class WakeOverviewUiStateTest {
    private val searchable = WakeOverviewUiState.Ready(
        keepAlive = listOf(WakeAppRow("example.keep", "Sync Mail", null, null)),
        attention = listOf(WakeAppRow("example.attention", "Sync Tasks", null, null)),
        handledKeepAlive = 0,
        automaticBlocks = 0,
        otherApps = 1,
        checking = false,
        blockedApps = listOf(WakeAppRow("example.blocked", "Sync Calendar", null, null)),
        dormantApps = listOf(WakeAppIdentity("example.dormant", "Sync Notes")),
    )
    private val searchOrder = listOf(WakeSearchGroup.KeepAlive, WakeSearchGroup.Attention, WakeSearchGroup.Blocked, WakeSearchGroup.Dormant)

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
    fun givenUnsortedInstalledApps_whenCollecting_thenMapsIdentityAndSortsByLabel() {
        val apps = listOf(wakeTestApp("example.z").copy(label = "zebra"), wakeTestApp("example.a").copy(label = "Apple"))
        val state = wakeOverviewUiState(WakeAvailability.Collecting, checking = true, installedApps = apps) as WakeOverviewUiState.Collecting

        assertThat(state.checking).isTrue()
        assertThat(state.installedApps).containsExactly(
            WakeAppIdentity("example.a", "Apple", apps[1].wakeIcon()),
            WakeAppIdentity("example.z", "zebra", apps[0].wakeIcon()),
        ).inOrder()
    }

    @Test
    fun givenAllGroups_whenSearchingByLabelOrPackage_thenMatchingIsTrimmedAndCaseInsensitive() {
        assertThat(searchable.searchResults("  sYnC ").map { it.group }).containsExactlyElementsIn(searchOrder).inOrder()
        assertThat(searchable.searchResults("  EXAMPLE. ").map { it.group }).containsExactlyElementsIn(searchOrder).inOrder()
        assertThat(searchable.searchResults("TAsKS").single().app.label).isEqualTo("Sync Tasks")
        assertThat(searchable.searchResults("DormAnt").single()).isEqualTo(WakeSearchRow(searchable.dormantApps.single(), WakeSearchGroup.Dormant))
    }

    @Test
    fun givenAllGroups_whenQueryIsBlankOrDoesNotMatch_thenReturnsAllInOrderOrNoRows() {
        assertThat(searchable.searchResults("").map { it.group }).containsExactlyElementsIn(searchOrder).inOrder()
        assertThat(searchable.searchResults("  \t ")).isEqualTo(searchable.searchResults(""))
        assertThat(searchable.searchResults("other package")).isEmpty()
    }

    @Test
    fun givenOnlyOneNonEmptyGroup_whenCheckingSearchAvailability_thenAnyGroupCanEnableSearch() {
        val empty = WakeOverviewUiState.Ready(emptyList(), emptyList(), 0, 0, 0, false)
        assertThat(empty.hasSearchableApps).isFalse()
        assertThat(empty.copy(keepAlive = searchable.keepAlive).hasSearchableApps).isTrue()
        assertThat(empty.copy(attention = searchable.attention).hasSearchableApps).isTrue()
        assertThat(empty.copy(blockedApps = searchable.blockedApps).hasSearchableApps).isTrue()
        assertThat(empty.copy(dormantApps = searchable.dormantApps).hasSearchableApps).isTrue()
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
        assertThat(state.blockedApps.map { it.packageName }).containsExactly("keep-b", "watch-b").inOrder()
        assertThat(state.dormantApps.map { it.packageName }).containsExactly("no-summary", "normal").inOrder()
        assertThat(state.otherApps).isEqualTo(2)
        assertThat(state.checking).isTrue()
    }

    @Test
    fun givenActiveAndInactiveProtection_whenReady_thenOnlyActiveEntriesCount() {
        val blocks = listOf(block("active", active = true), block("pending", active = false), block("allowed", active = false, allowed = true))

        val state = wakeOverviewUiState(WakeAvailability.Ready(100), false, automaticBlocks = blocks)

        assertThat(state).isEqualTo(WakeOverviewUiState.Ready(emptyList(), emptyList(), 0, 1, 0, false))
    }

    @Test
    fun givenManualAndAutomaticBlocksAndDormantApps_whenReady_thenEveryInstalledAppIsReachable() {
        val apps = listOf(
            wakeTestApp("dormant-z").copy(label = "zebra"),
            wakeTestApp("dormant-a").copy(label = "Apple"),
            wakeTestApp("manual"),
            wakeTestApp("static-only"),
            wakeTestApp("caller"),
            wakeTestApp("target"),
        )
        val manual = listOf("manual", "static-only").map {
            WakeBlock("STATIC|$it", it, WakeBlockMethod.COMPONENT, null, null, "$it.Receiver", null, 42)
        }
        val state = wakeOverviewUiState(
            WakeAvailability.Ready(null),
            false,
            listOf(summary("manual", WakeVerdict.NORMAL)),
            listOf(block("active", active = true)),
            apps,
            manual,
        ) as WakeOverviewUiState.Ready

        assertThat(state.blockedApps.map { it.packageName }).containsExactly("caller", "manual", "static-only", "target")
        assertThat(state.dormantApps.map { it.packageName }).containsExactly("dormant-a", "dormant-z").inOrder()
        assertThat(state.otherApps).isEqualTo(2)
        assertThat(state.automaticBlocks).isEqualTo(1)
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
