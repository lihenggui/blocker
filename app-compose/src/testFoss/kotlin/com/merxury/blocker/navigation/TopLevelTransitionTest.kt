// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.navigation

import android.app.Application
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.feature.appdetail.api.navigation.AppDetailNavKey
import com.merxury.blocker.feature.applist.api.navigation.AppListNavKey
import com.merxury.blocker.feature.generalrule.api.navigation.GeneralRuleNavKey
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TopLevelTransitionTest {
    private val apps = entry(AppListNavKey)
    private val rules = entry(GeneralRuleNavKey())
    private val roots = mapOf(apps.contentKey to AppListNavKey, rules.contentKey to GeneralRuleNavKey())

    @Test
    fun givenADetailInTheFirstDestination_whenFindingTheTopLevelKey_thenReturnsTheFirstDestination() {
        val scene = scene(apps, entry(AppDetailNavKey("com.example")))

        assertThat(scene.topLevelKey(roots)).isEqualTo(AppListNavKey)
    }

    @Test
    fun givenADetailInTheSecondDestination_whenFindingTheTopLevelKey_thenReturnsTheSecondDestination() {
        val scene = scene(apps, entry(AppDetailNavKey("com.example")), rules, entry(AppDetailNavKey("com.example.other")))

        assertThat(scene.topLevelKey(roots)).isEqualTo(GeneralRuleNavKey())
    }

    @Test
    fun whenFadingThrough_thenTheExitingScreenIsGoneBeforeTheEnteringScreenAppears() {
        for (step in 0..100) {
            val fraction = step / 100f
            val exitGone = FadeThroughOutEasing.transform(fraction) == 1f
            val enterShown = FadeThroughInEasing.transform(fraction) > 0f
            assertThat(enterShown && !exitGone).isFalse()
        }
        assertThat(FadeThroughOutEasing.transform(0f)).isEqualTo(0f)
        assertThat(FadeThroughInEasing.transform(1f)).isWithin(0.001f).of(1f)
    }

    @Test
    fun whenFadingThrough_thenTheExitingScreenFadesOutQuickly() {
        // The emphasized curve passes 0.4 at a sixth of the duration, past the 0.35 threshold.
        assertThat(FadeThroughOutEasing.transform(1f / 6)).isEqualTo(1f)
    }

    private fun entry(key: NavKey) = NavEntry(key) {}

    private fun scene(vararg entries: NavEntry<NavKey>) = with(SinglePaneSceneStrategy<NavKey>()) {
        checkNotNull(SceneStrategyScope<NavKey>().calculateScene(entries.toList()))
    }
}
