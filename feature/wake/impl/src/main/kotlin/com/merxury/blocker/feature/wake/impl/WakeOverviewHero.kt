// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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

    Row(
        modifier = Modifier.padding(bottom = WakeContentSpacing),
        horizontalArrangement = Arrangement.spacedBy(WakeContentSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WakeHeroGraphic(variant)
        Column(verticalArrangement = Arrangement.spacedBy(WakeGridUnit)) {
            Text(
                text = heroTitle(variant, state as? WakeOverviewUiState.Ready),
                style = MaterialTheme.typography.headlineSmall,
            )
            when (state) {
                is WakeOverviewUiState.Ready -> HeroBody(
                    if (state.attention.isEmpty()) {
                        stringResource(string.feature_wake_api_all_managed)
                    } else {
                        val count = state.attention.size
                        pluralStringResource(plurals.feature_wake_api_more_need_attention, count, count)
                    },
                )

                is WakeOverviewUiState.Collecting -> {
                    HeroBody(stringResource(string.feature_wake_api_collecting_body))
                    FilledTonalButton(onClick = onCheckNow, enabled = !state.checking) {
                        Text(stringResource(string.feature_wake_api_check_now))
                    }
                }

                WakeOverviewUiState.NoAccess -> FilledTonalButton(onClick = onGrantAccess) {
                    Text(stringResource(coreString.core_ui_grant_access))
                }

                else -> Unit
            }
        }
    }
}

private enum class HeroVariant { KeepAlive, Clean, Collecting, NoAccess, NotOwner, ReadsRefused }

private data class HeroShape(val outline: RoundedPolygon, val icon: ImageVector, val container: Color, val tint: Color)

@Composable
private fun heroTitle(variant: HeroVariant, ready: WakeOverviewUiState.Ready?): String = when (variant) {
    HeroVariant.KeepAlive -> {
        val count = checkNotNull(ready).keepAlive.size
        pluralStringResource(plurals.feature_wake_api_keep_alive_apps, count, count)
    }

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

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeHeroGraphic(variant: HeroVariant) {
    updateTransition(variant, label = "Wake hero graphic").Crossfade(
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        contentKey = { it == HeroVariant.Collecting },
    ) { graphic ->
        Box(Modifier.size(WakeHeroGraphicSize), contentAlignment = Alignment.Center) {
            if (graphic == HeroVariant.Collecting) {
                LoadingIndicator()
            } else {
                WakeHeroShape(graphic, Modifier.matchParentSize())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeHeroShape(variant: HeroVariant, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val (outline, icon, container, tint) = when (variant) {
        HeroVariant.KeepAlive -> HeroShape(
            MaterialShapes.Cookie12Sided,
            BlockerIcons.Autorenew,
            colors.errorContainer,
            colors.onErrorContainer,
        )

        HeroVariant.Clean -> HeroShape(
            MaterialShapes.Cookie6Sided,
            BlockerIcons.Check,
            colors.primaryContainer,
            colors.onPrimaryContainer,
        )

        HeroVariant.NoAccess -> HeroShape(
            MaterialShapes.Circle,
            BlockerIcons.Lock,
            colors.surfaceContainerHighest,
            colors.onSurfaceVariant,
        )

        HeroVariant.NotOwner -> HeroShape(
            MaterialShapes.Circle,
            BlockerIcons.PersonOff,
            colors.surfaceContainerHighest,
            colors.onSurfaceVariant,
        )

        HeroVariant.ReadsRefused -> HeroShape(
            MaterialShapes.Circle,
            BlockerIcons.Update,
            colors.surfaceContainerHighest,
            colors.onSurfaceVariant,
        )

        HeroVariant.Collecting -> error("Collecting uses a loading indicator")
    }
    val iconColor by animateColorAsState(tint, motion.slowEffectsSpec(), label = "Wake hero icon")
    Box(
        modifier.wakeHeroShape(outline, container),
        contentAlignment = Alignment.Center,
    ) {
        BlockerActionIcon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
            tint = iconColor,
        )
    }
}
