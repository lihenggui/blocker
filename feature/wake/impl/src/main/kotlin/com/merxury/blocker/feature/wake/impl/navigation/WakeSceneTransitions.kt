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
import androidx.navigation3.ui.NavDisplay

internal fun wakeSceneTransitions(density: Density): Map<String, Any> {
    val distance = with(density) { 30.dp.roundToPx() }
    return NavDisplay.transitionSpec { wakeTransition(distance) } +
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
