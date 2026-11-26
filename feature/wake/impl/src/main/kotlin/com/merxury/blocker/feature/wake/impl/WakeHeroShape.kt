// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val HERO_POINT_COUNT = 120
private val HeroDirections = Array(HERO_POINT_COUNT) { index ->
    val angle = heroAngle(index)
    Offset(cos(angle).toFloat(), sin(angle).toFloat())
}
internal val KeepAliveOutline = heroRadii(lobes = 12, amplitude = 0.07f)
internal val CleanOutline = heroRadii(lobes = 6, amplitude = 0.035f)
internal val CircleOutline = heroRadii(lobes = 0, amplitude = 0f)
internal val CollectingOutlines = listOf(
    KeepAliveOutline,
    heroRadii(lobes = 9, amplitude = 0.07f),
    heroRadii(lobes = 5, amplitude = 0.08f),
    heroRadii(lobes = 2, amplitude = 0.12f),
    heroRadii(lobes = 8, amplitude = 0.05f),
    heroRadii(lobes = 4, amplitude = 0.09f),
    CircleOutline,
)

private fun heroAngle(index: Int): Double = index * (2 * PI / HERO_POINT_COUNT)

internal fun heroRadii(lobes: Int, amplitude: Float): FloatArray = FloatArray(HERO_POINT_COUNT) { index ->
    val wave = if (lobes == 0) 0.0 else amplitude * cos(lobes * heroAngle(index))
    ((1.0 + wave) / (1.0 + amplitude)).toFloat()
}

private class HeroMorph(val start: FloatArray, val end: FloatArray) {
    val progress = Animatable(if (start === end) 1f else 0f)

    fun radiusAt(index: Int, fraction: Float): Float = lerp(start[index], end[index], fraction)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WakeHeroShape(outline: FloatArray, color: Color, modifier: Modifier = Modifier) {
    var morph by remember { mutableStateOf(HeroMorph(outline, outline)) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(outline, spec) {
        val previous = morph
        if (outline !== previous.end) {
            // A retarget starts from the last drawn radii, including any spring overshoot.
            val fraction = previous.progress.value
            val current = FloatArray(HERO_POINT_COUNT) { previous.radiusAt(it, fraction) }
            morph = HeroMorph(current, outline)
        }
        if (morph.progress.value != 1f) {
            morph.progress.animateTo(1f, spec)
        }
    }

    val path = remember { Path() }
    Canvas(modifier) {
        val current = morph
        val fraction = current.progress.value
        path.rewind()
        HeroDirections.forEachIndexed { index, direction ->
            val point = direction * current.radiusAt(index, fraction)
            val x = center.x * (1 + point.x)
            val y = center.y * (1 + point.y)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        drawPath(path, color)
    }
}
