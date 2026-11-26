// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string
import com.merxury.blocker.core.ui.R.string as coreString

@Composable
internal fun WakeOverviewHero(
    state: WakeOverviewUiState,
    onGrantAccess: () -> Unit,
    onCheckNow: () -> Unit,
) {
    val variant = when (state) {
        WakeOverviewUiState.Loading -> return
        WakeOverviewUiState.NoAccess -> HeroVariant.NoAccess
        WakeOverviewUiState.NotOwnerProfile -> HeroVariant.NotOwner
        WakeOverviewUiState.ReadsRefused -> HeroVariant.ReadsRefused
        is WakeOverviewUiState.Collecting -> HeroVariant.Collecting
        is WakeOverviewUiState.Ready -> if (state.keepAlive.isEmpty()) HeroVariant.Clean else HeroVariant.KeepAlive
    }

    val ready = state as? WakeOverviewUiState.Ready
    Row(
        modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WakeHeroGraphic(variant, ready?.handledKeepAlive ?: 0)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = heroTitle(variant, ready?.keepAlive?.size ?: 0),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
            )
            when (state) {
                is WakeOverviewUiState.Ready -> HeroBody(
                    when (val count = state.attention.size) {
                        0 -> stringResource(string.feature_wake_api_all_managed)
                        else -> pluralStringResource(plurals.feature_wake_api_more_need_attention, count, count)
                    },
                )

                is WakeOverviewUiState.Collecting -> {
                    HeroBody(stringResource(string.feature_wake_api_collecting_body))
                    HeroButton(string.feature_wake_api_check_now, onCheckNow, enabled = !state.checking)
                }

                WakeOverviewUiState.NoAccess -> HeroButton(coreString.core_ui_grant_access, onGrantAccess)

                else -> Unit
            }
        }
    }
}

private enum class HeroVariant { KeepAlive, Clean, Collecting, NoAccess, NotOwner, ReadsRefused }

private data class HeroGraphic(val variant: HeroVariant, val handledKeepAlive: Int) {
    val collecting: Boolean get() = variant == HeroVariant.Collecting
}

private class HeroMorph(val start: RoundedPolygon, val end: RoundedPolygon) {
    val shape = Morph(start, end)
}

@Composable
private fun heroTitle(variant: HeroVariant, keepAlive: Int): String = when (variant) {
    HeroVariant.KeepAlive -> pluralStringResource(plurals.feature_wake_api_keep_alive_apps, keepAlive, keepAlive)
    HeroVariant.Clean -> stringResource(string.feature_wake_api_none_found)
    HeroVariant.Collecting -> stringResource(string.feature_wake_api_collecting_title)
    HeroVariant.NoAccess -> stringResource(string.feature_wake_api_needs_access)
    HeroVariant.NotOwner -> stringResource(string.feature_wake_api_owner_only)
    HeroVariant.ReadsRefused -> stringResource(string.feature_wake_api_update_shizuku)
}

@Composable
private fun HeroBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HeroButton(text: Int, onClick: () -> Unit, enabled: Boolean = true) {
    // The column already provides the first 4 dp of separation.
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.padding(top = 4.dp)) {
        Text(stringResource(text))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class)
@Composable
private fun WakeHeroGraphic(variant: HeroVariant, handledKeepAlive: Int) {
    val transition = updateTransition(HeroGraphic(variant, handledKeepAlive), label = "Wake hero loading")
    transition.Crossfade(
        modifier = Modifier.size(88.dp),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        contentKey = { it.collecting },
    ) { graphic ->
        if (graphic.collecting) {
            LoadingIndicator(
                modifier = Modifier.size(88.dp).clearAndSetSemantics {},
                color = MaterialTheme.colorScheme.primaryContainer,
            )
        } else {
            WakeHeroBadge(graphic.variant, graphic.handledKeepAlive)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeHeroBadge(variant: HeroVariant, handledKeepAlive: Int) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val polygon = when (variant) {
        HeroVariant.KeepAlive -> MaterialShapes.Cookie12Sided
        HeroVariant.Clean -> MaterialShapes.Cookie6Sided
        else -> MaterialShapes.Circle
    }
    val containerColor by animateColorAsState(
        when (variant) {
            HeroVariant.KeepAlive -> colors.errorContainer
            HeroVariant.Clean -> colors.primaryContainer
            else -> colors.surfaceContainerHighest
        },
        animationSpec = motion.slowEffectsSpec(),
        label = "Wake hero container",
    )
    val iconColor by animateColorAsState(
        when (variant) {
            HeroVariant.KeepAlive -> colors.onErrorContainer
            HeroVariant.Clean -> colors.onPrimaryContainer
            else -> colors.onSurfaceVariant
        },
        animationSpec = motion.slowEffectsSpec(),
        label = "Wake hero icon color",
    )
    val rotation by animateFloatAsState(
        when (variant) {
            HeroVariant.KeepAlive -> 30f * handledKeepAlive
            HeroVariant.Clean -> 60f
            else -> 0f
        },
        animationSpec = motion.slowSpatialSpec(),
        label = "Wake hero rotation",
    )
    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        WakeHeroShape(polygon, containerColor, Modifier.matchParentSize().graphicsLayer { rotationZ = rotation })
        Crossfade(
            targetState = variant,
            animationSpec = motion.defaultEffectsSpec(),
            label = "Wake hero icon",
        ) { badge ->
            val icon = when (badge) {
                HeroVariant.KeepAlive -> BlockerIcons.Autorenew
                HeroVariant.Clean -> BlockerIcons.Check
                HeroVariant.NoAccess -> BlockerIcons.Lock
                HeroVariant.NotOwner -> BlockerIcons.PersonOff
                HeroVariant.ReadsRefused -> BlockerIcons.Update
                HeroVariant.Collecting -> null
            }
            if (icon != null) {
                BlockerActionIcon(icon, contentDescription = null, Modifier.size(40.dp), tint = iconColor)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeHeroShape(polygon: RoundedPolygon, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    var settled by remember { mutableStateOf(polygon) }
    var morph by remember { mutableStateOf<HeroMorph?>(null) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(polygon, spec) {
        val active = morph
        when (polygon) {
            active?.start -> progress.animateTo(0f, spec)

            active?.end -> progress.animateTo(1f, spec)

            else -> {
                val start = when {
                    active == null -> settled
                    progress.value >= 0.5f -> active.end
                    else -> active.start
                }
                if (start != polygon) {
                    progress.snapTo(0f)
                    morph = HeroMorph(start, polygon)
                    progress.animateTo(1f, spec)
                }
            }
        }
        settled = polygon
        morph = null
    }

    val activeMorph = morph
    if (activeMorph == null) {
        Box(modifier.background(color, settled.toShape()))
    } else {
        val path = remember { Path() }
        Canvas(modifier) {
            activeMorph.shape.toPath(progress.value, path)
            path.translate(Offset(0.5f, 0.5f) - path.getBounds().center)
            scale(size.width, size.height, pivot = Offset.Zero) {
                drawPath(path, color)
            }
        }
    }
}
