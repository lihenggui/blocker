// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay

private const val WAKE_DESTINATION = "com.merxury.blocker.feature.wake.destination"

// In a single pane, distinct scenes let NavDisplay seek the entry transitions on Back.
object CompactWakeSceneStrategy : SceneStrategy<NavKey> {
    private val singlePane = SinglePaneSceneStrategy<NavKey>()

    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        if (entries.lastOrNull()?.metadata?.get(WAKE_DESTINATION) != true) return null
        return with(singlePane) { calculateScene(entries) }
    }
}

// Use one eighth of the pane width for the shared-axis offset so it scales with the layout.
private const val SHARED_AXIS_WIDTH_DIVISOR = 8

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun wakeSceneTransitions(motion: MotionScheme, layoutDirection: LayoutDirection): Map<String, Any> {
    val forward = layoutDirection == LayoutDirection.Ltr
    return mapOf(WAKE_DESTINATION to true) +
        NavDisplay.transitionSpec { wakeTransition(motion, forward) } +
        NavDisplay.popTransitionSpec { wakeTransition(motion, !forward) } +
        NavDisplay.predictivePopTransitionSpec { _ -> wakeTransition(motion, !forward) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun wakeTransition(motion: MotionScheme, towardLeft: Boolean): ContentTransform {
    val slide = motion.defaultSpatialSpec<IntOffset>()
    val offset: (Int) -> Int = { width ->
        val distance = width / SHARED_AXIS_WIDTH_DIVISOR
        if (towardLeft) distance else -distance
    }
    val enter = fadeIn(motion.defaultEffectsSpec()) + slideInHorizontally(slide, initialOffsetX = offset)
    val exit = fadeOut(motion.fastEffectsSpec()) + slideOutHorizontally(slide) { -offset(it) }
    return enter togetherWith exit
}
