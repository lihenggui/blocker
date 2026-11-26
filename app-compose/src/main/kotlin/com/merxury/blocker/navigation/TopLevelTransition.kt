// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.core.graphics.PathParser
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene

// Material 3 moves between navigation bar destinations with the top level transition (fade through),
// and its transitions use the easing and duration tokens. The values below are those of
// MaterialFadeThrough in Material Components for Android, the implementation the guidelines link to.

/** md.sys.motion.duration.long1 */
private const val FADE_THROUGH_DURATION_MILLIS = 450

/** Share of the progress in which the exiting screen fades out, before the entering screen fades in. */
private const val FADE_THROUGH_THRESHOLD = 0.35f

/** Scale from which the entering screen grows. */
private const val FADE_THROUGH_START_SCALE = 0.92f

/** md.sys.motion.easing.emphasized on Android, built the way Material Components builds it. */
private val EmphasizedEasing = PathInterpolatorCompat.create(
    PathParser.createPathFromPathData("M 0,0 C 0.05, 0, 0.133333, 0.06, 0.166666, 0.4 C 0.208333, 0.82, 0.25, 1, 1, 1"),
).let { interpolator -> Easing(interpolator::getInterpolation) }

internal val FadeThroughOutEasing = Easing { fraction ->
    (EmphasizedEasing.transform(fraction) / FADE_THROUGH_THRESHOLD).coerceIn(0f, 1f)
}

internal val FadeThroughInEasing = Easing { fraction ->
    ((EmphasizedEasing.transform(fraction) - FADE_THROUGH_THRESHOLD) / (1f - FADE_THROUGH_THRESHOLD)).coerceIn(0f, 1f)
}

private fun fadeThrough(): ContentTransform {
    val enter = fadeIn(tween(FADE_THROUGH_DURATION_MILLIS, easing = FadeThroughInEasing)) +
        scaleIn(tween(FADE_THROUGH_DURATION_MILLIS, easing = EmphasizedEasing), initialScale = FADE_THROUGH_START_SCALE)
    return enter togetherWith fadeOut(tween(FADE_THROUGH_DURATION_MILLIS, easing = FadeThroughOutEasing))
}

/** The top-level destination whose stack holds the last entry of this scene, keyed by root content key. */
internal fun Scene<NavKey>.topLevelKey(roots: Map<Any, NavKey>): NavKey? = (previousEntries + entries).asReversed().firstNotNullOfOrNull { roots[it.contentKey] }

/** Fade through when the scene changes to another top-level destination, or null to keep the default. */
internal fun AnimatedContentTransitionScope<Scene<NavKey>>.topLevelTransition(roots: Map<Any, NavKey>): ContentTransform? = if (initialState.topLevelKey(roots) != targetState.topLevelKey(roots)) fadeThrough() else null
