// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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
    // The 48 dp touch target extends 4 dp below the visible button.
    val bottomPadding = if (variant == HeroVariant.Collecting || variant == HeroVariant.NoAccess) 24.dp else 28.dp
    Row(
        modifier = Modifier.padding(top = 8.dp, bottom = bottomPadding),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroButton(text: Int, onClick: () -> Unit, enabled: Boolean = true) {
    // The column and minimum touch target already provide 8 dp above the visible button.
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.padding(top = 4.dp),
        contentPadding = ButtonDefaults.SmallContentPadding,
    ) {
        Text(stringResource(text))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeHeroGraphic(variant: HeroVariant, handledKeepAlive: Int) {
    val collecting = variant == HeroVariant.Collecting
    var step by remember(collecting) { mutableIntStateOf(0) }
    if (collecting) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(650)
                step++
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val outline = when (variant) {
        HeroVariant.KeepAlive -> KeepAliveOutline
        HeroVariant.Clean -> CleanOutline
        HeroVariant.Collecting -> CollectingOutlines[step % CollectingOutlines.size]
        else -> CircleOutline
    }
    val containerColor by animateColorAsState(
        when (variant) {
            HeroVariant.KeepAlive -> colors.errorContainer
            HeroVariant.Clean, HeroVariant.Collecting -> colors.primaryContainer
            else -> colors.surfaceContainerHighest
        },
        animationSpec = motion.slowEffectsSpec(),
        label = "Wake hero container",
    )
    val iconColor by animateColorAsState(
        when (variant) {
            HeroVariant.KeepAlive -> colors.onErrorContainer
            HeroVariant.Clean -> colors.onPrimaryContainer
            HeroVariant.Collecting -> Color.Transparent
            else -> colors.onSurfaceVariant
        },
        animationSpec = motion.slowEffectsSpec(),
        label = "Wake hero icon color",
    )
    val rotation by animateFloatAsState(
        when (variant) {
            HeroVariant.KeepAlive -> 30f * handledKeepAlive
            HeroVariant.Clean -> 60f
            HeroVariant.Collecting -> 60f * step
            else -> 0f
        },
        animationSpec = motion.slowSpatialSpec(),
        label = "Wake hero rotation",
    )
    val icon = when (variant) {
        HeroVariant.KeepAlive -> BlockerIcons.Autorenew
        HeroVariant.Clean -> BlockerIcons.Check
        HeroVariant.NoAccess -> BlockerIcons.Lock
        HeroVariant.NotOwner -> BlockerIcons.PersonOff
        HeroVariant.ReadsRefused -> BlockerIcons.Update
        HeroVariant.Collecting -> null
    }
    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        WakeHeroShape(outline, containerColor, Modifier.matchParentSize().graphicsLayer { rotationZ = rotation })
        if (icon != null) {
            BlockerActionIcon(icon, contentDescription = null, Modifier.size(40.dp), tint = iconColor)
        }
    }
}

private const val HERO_POINT_COUNT = 120
private val HeroDirections = Array(HERO_POINT_COUNT) { index ->
    val angle = heroAngle(index)
    Offset(cos(angle).toFloat(), sin(angle).toFloat())
}
private val KeepAliveOutline = heroRadii(lobes = 12, amplitude = 0.07f)
private val CleanOutline = heroRadii(lobes = 6, amplitude = 0.035f)
private val CircleOutline = heroRadii(lobes = 0, amplitude = 0f)
private val CollectingOutlines = listOf(
    KeepAliveOutline,
    heroRadii(lobes = 9, amplitude = 0.07f),
    heroRadii(lobes = 5, amplitude = 0.08f),
    heroRadii(lobes = 2, amplitude = 0.12f),
    heroRadii(lobes = 8, amplitude = 0.05f),
    heroRadii(lobes = 4, amplitude = 0.09f),
    CircleOutline,
)

private fun heroAngle(index: Int): Double = index * (2 * PI / HERO_POINT_COUNT)

private fun heroRadii(lobes: Int, amplitude: Float): FloatArray = FloatArray(HERO_POINT_COUNT) { index ->
    val wave = if (lobes == 0) 0.0 else amplitude * cos(lobes * heroAngle(index))
    ((1.0 + wave) / (1.0 + amplitude)).toFloat()
}

private class HeroMorph(val start: FloatArray, val end: FloatArray) {
    val progress = Animatable(if (start === end) 1f else 0f)

    fun radiusAt(index: Int, fraction: Float): Float = lerp(start[index], end[index], fraction)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeHeroShape(outline: FloatArray, color: Color, modifier: Modifier = Modifier) {
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
