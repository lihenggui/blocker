// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl.navigation

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategyScope
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.feature.wake.api.navigation.WakeAppNavKey
import com.merxury.blocker.feature.wake.api.navigation.WakeOverviewNavKey
import org.junit.Test

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class WakeSceneTransitionsTest {
    @Test
    fun givenWakeStack_whenUsingOnePane_thenEachEntryHasADistinctSceneForBack() {
        val overview = entry(WakeOverviewNavKey)
        val firstApp = entry(WakeAppNavKey("example.calendar"))
        val peer = entry(WakeAppNavKey("example.notes"))

        val listScene = checkNotNull(scene(listOf(overview)))
        val appScene = checkNotNull(scene(listOf(overview, firstApp)))
        val peerScene = checkNotNull(scene(listOf(overview, firstApp, peer)))

        assertThat(listOf(listScene.key, appScene.key, peerScene.key).distinct()).hasSize(3)
        assertThat(peerScene.entries).containsExactly(peer)
        assertThat(peerScene.previousEntries).containsExactly(overview, firstApp).inOrder()
        assertThat(appScene.previousEntries).containsExactly(overview)
        assertThat(peerScene.metadata).containsExactlyEntriesIn(peer.metadata)
    }

    @Test
    fun givenAnUnmarkedEntry_whenUsingOnePane_thenLeavesSceneSelectionToItsExistingStrategy() {
        val unmarked = NavEntry<NavKey>(WakeOverviewNavKey) {}
        assertThat(scene(listOf(entry(WakeOverviewNavKey), unmarked))).isNull()
    }

    private fun entry(key: NavKey) = NavEntry(key, metadata = wakeSceneTransitions(MotionScheme.standard(), LayoutDirection.Ltr)) {}

    private fun scene(entries: List<NavEntry<NavKey>>) = with(CompactWakeSceneStrategy) {
        SceneStrategyScope<NavKey>().calculateScene(entries)
    }
}
