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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
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

// The slide distance of Material's shared axis transition (MaterialSharedAxis).
private val SharedAxisSlideDistance = 30.dp

/** Transition metadata for the wake entries, from the theme motion, density and layout direction. */
class WakeTransitions internal constructor(internal val metadata: Map<String, Any>)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberWakeTransitions(): WakeTransitions {
    val motion = MaterialTheme.motionScheme
    val layoutDirection = LocalLayoutDirection.current
    val distance = with(LocalDensity.current) { SharedAxisSlideDistance.roundToPx() }
    return remember(motion, layoutDirection, distance) {
        WakeTransitions(wakeSceneTransitions(motion, layoutDirection, distance))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun wakeSceneTransitions(motion: MotionScheme, layoutDirection: LayoutDirection, distance: Int): Map<String, Any> {
    val forward = layoutDirection == LayoutDirection.Ltr
    return mapOf(WAKE_DESTINATION to true) +
        NavDisplay.transitionSpec { wakeTransition(motion, forward, distance) } +
        NavDisplay.popTransitionSpec { wakeTransition(motion, !forward, distance) } +
        NavDisplay.predictivePopTransitionSpec { _ -> wakeTransition(motion, !forward, distance) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun wakeTransition(motion: MotionScheme, towardLeft: Boolean, distance: Int): ContentTransform {
    val slide = motion.defaultSpatialSpec<IntOffset>()
    val offset: (Int) -> Int = { if (towardLeft) distance else -distance }
    val enter = fadeIn(motion.defaultEffectsSpec()) + slideInHorizontally(slide, initialOffsetX = offset)
    val exit = fadeOut(motion.fastEffectsSpec()) + slideOutHorizontally(slide) { -offset(it) }
    return enter togetherWith exit
}
