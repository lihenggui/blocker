// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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

internal fun wakeSceneTransitions(density: Density): Map<String, Any> {
    val distance = with(density) { 30.dp.roundToPx() }
    return mapOf(WAKE_DESTINATION to true) +
        NavDisplay.transitionSpec { wakeTransition(distance) } +
        NavDisplay.popTransitionSpec { wakeTransition(-distance) } +
        NavDisplay.predictivePopTransitionSpec { _ -> wakeTransition(-distance) }
}

private fun wakeTransition(distance: Int): ContentTransform {
    val slide = tween<IntOffset>(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    val enter = fadeIn(tween(210, delayMillis = 90, easing = CubicBezierEasing(0f, 0f, 0f, 1f))) +
        slideInHorizontally(slide) { distance }
    val exit = fadeOut(tween(90, easing = CubicBezierEasing(0.3f, 0f, 1f, 1f))) +
        slideOutHorizontally(slide) { -distance }
    return enter togetherWith exit
}
