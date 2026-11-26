// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

// Compose keeps FabLargeTokens.ContainerWidth and ContainerHeight internal.
internal val WakeHeroGraphicSize = 96.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Modifier.wakeHeroShape(outline: RoundedPolygon, container: Color): Modifier {
    val motion = MaterialTheme.motionScheme
    val containerColor by animateColorAsState(container, motion.slowEffectsSpec(), label = "Wake hero container")
    val target by rememberUpdatedState(outline)
    val spec by rememberUpdatedState(motion.slowSpatialSpec<Float>())
    val progress = remember { Animatable(MORPH_END) }
    var morph by remember { mutableStateOf(Morph(outline, outline)) }
    LaunchedEffect(Unit) {
        var current = outline
        // Finish each morph at a Material polygon before taking the latest target.
        snapshotFlow { target }.collect { next ->
            if (current != next) {
                val nextMorph = Morph(current, next)
                progress.snapTo(MORPH_START)
                morph = nextMorph
                progress.animateTo(MORPH_END, spec)
                current = next
            }
        }
    }

    return drawWithCache {
        val path = Path()
        val scale = Matrix().apply { scale(size.width, size.height) }
        onDrawBehind {
            morph.toPath(progress.value, path).transform(scale)
            drawPath(path, containerColor)
        }
    }
}

// Graphics Shapes defines morph progress from the source polygon to the target polygon.
private const val MORPH_START = 0f
private const val MORPH_END = 1f
