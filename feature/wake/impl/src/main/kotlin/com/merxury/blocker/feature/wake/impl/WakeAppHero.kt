// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import android.icu.text.NumberFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WakeAppHero(state: WakeAppUiState, onPeerClick: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (status, targetColor) = when (state.status) {
        WakeAppStatus.KeepAlive -> string.feature_wake_api_status_keep_alive to colors.error
        WakeAppStatus.Attention -> string.feature_wake_api_status_attention to colors.tertiary
        WakeAppStatus.None -> string.feature_wake_api_none_found to colors.onSurfaceVariant
        WakeAppStatus.AllBlocked -> string.feature_wake_api_status_all_blocked to colors.primary
    }
    val statusColor by animateColorAsState(targetColor, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "Wake status")
    val inbound = state.inbound
    val outbound = state.outbound
    val description = stringResource(
        string.feature_wake_api_list_pair,
        pluralStringResource(plurals.feature_wake_api_chain_inbound, inbound.peers.size, inbound.peers.size, state.app.label),
        pluralStringResource(plurals.feature_wake_api_chain_outbound, outbound.peers.size, outbound.peers.size, state.app.label),
    )

    Column(
        Modifier.fillMaxWidth().padding(vertical = WakeContentSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WakeGridUnit),
    ) {
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChainPeers(inbound, Alignment.End, onPeerClick, Modifier.weight(1f))
            ChainConnector(inbound)
            WakeAppHalo(state)
            ChainConnector(outbound)
            ChainPeers(outbound, Alignment.Start, onPeerClick, Modifier.weight(1f))
        }
        Text(state.app.label, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(stringResource(status), style = MaterialTheme.typography.titleSmall, color = statusColor, textAlign = TextAlign.Center)
        Text(
            WakeText.Count(plurals.feature_wake_api_background_starts, state.backgroundStarts).resolve(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeAppHalo(state: WakeAppUiState) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val (outline, haloColor) = when (state.status.halo) {
        WakeHalo.Blocked -> MaterialShapes.Cookie6Sided to colors.primaryContainer
        WakeHalo.KeepAlive -> MaterialShapes.Cookie12Sided to colors.errorContainer
        WakeHalo.Attention -> MaterialShapes.Cookie9Sided to colors.tertiaryContainer
    }
    val rotation by animateFloatAsState(
        state.status.halo.rotation,
        motion.slowSpatialSpec(),
        label = "Halo rotation",
    )
    Box(Modifier.size(WakeHeroGraphicSize), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer { rotationZ = rotation }.wakeHeroShape(outline, haloColor))
        AppIcon(
            state.app.icon,
            Modifier.size(WakeListAvatarSize).clip(IconButtonDefaults.smallRoundShape).background(colors.surfaceContainerHighest),
        )
        AnimatedVisibility(
            visible = state.hasRestart,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec()),
            exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec()),
        ) {
            Badge {
                BlockerActionIcon(BlockerIcons.RestartAltOutlined, null, Modifier.size(IconButtonDefaults.extraSmallIconSize), tint = LocalContentColor.current)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChainConnector(side: WakeChainSide) {
    val motion = MaterialTheme.motionScheme
    val size by animateDpAsState(
        if (side.cut) IconButtonDefaults.extraSmallIconSize else IconButtonDefaults.smallIconSize,
        motion.defaultSpatialSpec(),
        label = "Wake connector size",
    )
    // Reserve the standard icon slot so both sides keep the halo centered.
    Box(Modifier.size(IconButtonDefaults.smallIconSize), contentAlignment = Alignment.Center) {
        if (side.peers.isNotEmpty()) {
            Crossfade(side.cut, animationSpec = motion.defaultEffectsSpec(), label = "Wake connector") { cut ->
                val colors = MaterialTheme.colorScheme
                BlockerActionIcon(
                    if (cut) BlockerIcons.LinkOff else BlockerIcons.Forward,
                    null,
                    Modifier.size(size),
                    tint = if (cut) colors.onSurfaceVariant else colors.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChainPeers(side: WakeChainSide, alignment: Alignment.Horizontal, onPeerClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(WakeGridUnit)) {
        if (side.peers.isEmpty()) return@Column
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap, alignment),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            side.visiblePeers.forEach { peer ->
                ChainAvatar(peer, onClick = { onPeerClick(peer.app.packageName) }, Modifier.weight(1f, fill = false))
            }
            if (side.more > 0) {
                ChainMore(side.more, Modifier.weight(1f, fill = false))
            }
        }
        Text(
            text = side.peers.singleOrNull()?.app?.label
                ?: pluralStringResource(plurals.feature_wake_api_chain_apps, side.peers.size, side.peers.size),
            modifier = Modifier.clearAndSetSemantics {},
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChainAvatar(peer: WakeChainPeer, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    val size by animateDpAsState(
        if (peer.blocked) FloatingActionButtonDefaults.LargeIconSize else WakeListAvatarSize,
        motion.defaultSpatialSpec(),
        label = "Peer avatar size",
    )
    val opacity by animateFloatAsState(
        if (peer.blocked) IconButtonDefaults.iconButtonColors().disabledContentColor.alpha else 1f,
        motion.defaultEffectsSpec(),
        label = "Peer avatar opacity",
    )
    val label = stringResource(string.feature_wake_api_view_app, peer.app.label)
    AppIcon(
        peer.app.icon,
        modifier.size(size).graphicsLayer { alpha = opacity }.clip(IconButtonDefaults.smallRoundShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun ChainMore(count: Int, modifier: Modifier = Modifier) {
    val label = pluralStringResource(plurals.feature_wake_api_chain_more, count, count)
    val number = NumberFormat.getIntegerInstance(LocalResources.current.configuration.locales[0]).format(count)
    Box(
        modifier.size(WakeListAvatarSize)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, IconButtonDefaults.smallRoundShape)
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text("+$number", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}
