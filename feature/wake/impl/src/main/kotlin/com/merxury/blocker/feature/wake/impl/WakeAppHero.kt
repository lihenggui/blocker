// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string

private val AttentionOutline = heroRadii(lobes = 9, amplitude = 0.05f)

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

    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.widthIn(max = 336.dp).fillMaxWidth().semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChainPeers(inbound, inbound = true, onPeerClick, Modifier.weight(1f))
            ChainConnector(inbound)
            WakeAppHalo(state)
            ChainConnector(outbound)
            ChainPeers(outbound, inbound = false, onPeerClick, Modifier.weight(1f))
        }
        Text(
            state.app.label,
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp),
            fontWeight = FontWeight.Normal,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(status),
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = statusColor,
            textAlign = TextAlign.Center,
        )
        Text(
            WakeText.Count(plurals.feature_wake_api_background_starts, state.backgroundStarts).resolve(),
            Modifier.padding(top = 2.dp),
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
        WakeHalo.Blocked -> CleanOutline to colors.primaryContainer
        WakeHalo.KeepAlive -> KeepAliveOutline to colors.errorContainer
        WakeHalo.Attention -> AttentionOutline to colors.tertiaryContainer
    }
    val color by animateColorAsState(haloColor, motion.slowEffectsSpec(), label = "Halo color")
    val rotation by animateFloatAsState(
        state.status.halo.rotation,
        motion.slowSpatialSpec(),
        label = "Halo rotation",
    )
    val badge = if (state.hasRestart) 1f else 0f
    val badgeAlpha by animateFloatAsState(badge, motion.fastEffectsSpec(), label = "Restart badge opacity")
    val badgeScale by animateFloatAsState(badge, motion.fastSpatialSpec(), label = "Restart badge scale")
    Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
        WakeHeroShape(outline, color, Modifier.matchParentSize().graphicsLayer { rotationZ = rotation })
        AppIcon(state.app.icon, Modifier.size(68.dp).clip(CircleShape).background(colors.surfaceContainerHighest))
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 10.dp).size(30.dp)
                .graphicsLayer {
                    alpha = badgeAlpha.coerceIn(0f, 1f)
                    scaleX = badgeScale
                    scaleY = badgeScale
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(28.dp).background(colors.surface, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(24.dp).background(colors.error, CircleShape), contentAlignment = Alignment.Center) {
                    BlockerActionIcon(BlockerIcons.RestartAltOutlined, null, Modifier.size(16.dp), tint = colors.onError)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChainConnector(side: WakeChainSide) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (side.cut) 0.3f else 1f, motion.defaultSpatialSpec(), label = "Connector length")
    val color by animateColorAsState(
        if (side.cut) colors.outlineVariant else colors.error,
        motion.defaultEffectsSpec(),
        label = "Connector color",
    )
    val arrow by animateFloatAsState(if (side.cut) 0f else 1f, motion.defaultEffectsSpec(), label = "Connector arrow")
    Canvas(Modifier.size(20.dp, 12.dp)) {
        if (side.peers.isEmpty()) return@Canvas
        val width = size.width / 2 * scale.coerceIn(0f, 1f)
        val radius = 1.dp.toPx()
        val lineSize = Size(width, radius * 2)
        val y = center.y - radius
        drawRoundRect(color, Offset(0f, y), lineSize, CornerRadius(radius))
        drawRoundRect(color, Offset(size.width - width, y), lineSize, CornerRadius(radius))
        val leftToRight = layoutDirection == LayoutDirection.Ltr
        val head = 4.dp.toPx()
        val tailX = if (leftToRight) head else -head
        val tip = Offset(if (leftToRight) size.width - radius else radius, center.y)
        val tint = color.copy(alpha = arrow.coerceIn(0f, 1f))
        drawLine(tint, tip - Offset(tailX, head), tip, radius * 2, StrokeCap.Square)
        drawLine(tint, tip - Offset(tailX, -head), tip, radius * 2, StrokeCap.Square)
    }
}

@Composable
private fun ChainPeers(side: WakeChainSide, inbound: Boolean, onPeerClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val peers = side.visiblePeers
    val slots = peers.size + if (side.more > 0) 1 else 0
    Box(modifier, contentAlignment = if (inbound) Alignment.CenterEnd else Alignment.CenterStart) {
        if (slots == 0) return@Box
        Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = if (inbound) Alignment.CenterEnd else Alignment.CenterStart) {
                Box(Modifier.requiredWidth((36 + 24 * (slots - 1)).dp)) {
                    peers.forEachIndexed { index, peer ->
                        ChainAvatar(
                            peer,
                            onClick = { onPeerClick(peer.app.packageName) },
                            Modifier.align(if (inbound) Alignment.CenterEnd else Alignment.CenterStart)
                                .offset(x = (24 * index * if (inbound) -1 else 1).dp),
                        )
                    }
                    if (side.more > 0) {
                        ChainMore(side.more, Modifier.align(if (inbound) Alignment.CenterStart else Alignment.CenterEnd))
                    }
                }
            }
            Text(
                text = side.peers.singleOrNull()?.app?.label
                    ?: pluralStringResource(plurals.feature_wake_api_chain_apps, side.peers.size, side.peers.size),
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChainAvatar(peer: WakeChainPeer, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val size by animateFloatAsState(if (peer.blocked) 0.88f else 1f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "Peer avatar scale")
    val opacity by animateFloatAsState(if (peer.blocked) 0.38f else 1f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "Peer avatar opacity")
    val label = stringResource(string.feature_wake_api_view_app, peer.app.label)
    Box(
        modifier.size(36.dp).graphicsLayer {
            scaleX = size
            scaleY = size
            alpha = opacity.coerceIn(0f, 1f)
        }.avatarRing().clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        AppIcon(peer.app.icon, Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape))
    }
}

@Composable
private fun ChainMore(count: Int, modifier: Modifier = Modifier) {
    val label = pluralStringResource(plurals.feature_wake_api_chain_more, count, count)
    Box(
        modifier.size(36.dp).avatarRing().background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text("+$count", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Modifier.avatarRing(): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    return drawRing(surface)
}

private fun Modifier.drawRing(color: Color) = drawBehind {
    drawCircle(color, radius = size.minDimension / 2 + 2.dp.toPx())
}
