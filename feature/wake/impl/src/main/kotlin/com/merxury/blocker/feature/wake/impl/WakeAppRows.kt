// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.model.wake.JustifiedStarts
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.wakeDetailGroup(
    title: Int,
    rows: List<WakeDetailRow>,
    expanded: String?,
    busy: Boolean,
    onExpand: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onPeerClick: (String) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = title) { WakeGroupHeader(title) }
    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
        WakeDetailItem(
            row = row,
            index = index,
            count = rows.size,
            expanded = expanded == row.key,
            busy = busy,
            onExpand = { onExpand(row.key) },
            onToggle = { onToggle(row.key, it) },
            onPeerClick = onPeerClick,
            modifier = Modifier.padding(bottom = if (index == rows.lastIndex) 20.dp else ListItemDefaults.SegmentedGap),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeDetailItem(
    row: WakeDetailRow,
    index: Int,
    count: Int,
    expanded: Boolean,
    busy: Boolean,
    onExpand: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onPeerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val shapes = animatedSegmentedShapes(index, count, expanded)
    val container by animateColorAsState(
        if (expanded) colors.surfaceContainerHigh else colors.surfaceContainer,
        motion.defaultEffectsSpec(),
        label = "Wake row container",
    )
    val metaColor by animateColorAsState(
        if (row.blocked) colors.primary else colors.onSurfaceVariant,
        motion.defaultEffectsSpec(),
        label = "Wake row supporting text",
    )
    val title = row.title.resolve()
    Column(modifier.fillMaxWidth().clip(shapes.shape).background(container)) {
        SegmentedListItem(
            onClick = onExpand,
            shapes = shapes,
            modifier = Modifier.heightIn(min = 72.dp).expandable(expanded, onExpand),
            colors = ListItemDefaults.segmentedColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            leadingContent = { WakeRowIcon(row) },
            supportingContent = row.meta?.let { meta -> { Text(meta.resolve(), color = metaColor) } },
            trailingContent = if (row.canToggle) {
                {
                    WakeSwitch(
                        allowed = !row.blocked,
                        enabled = !busy,
                        label = stringResource(string.feature_wake_api_allow_path, title),
                        onChange = onToggle,
                    )
                }
            } else {
                null
            },
        ) { Text(title) }
        WakeExpanded(expanded) { WakeRowFacts(row, onPeerClick) }
    }
}

@Composable
private fun WakeRowIcon(row: WakeDetailRow) {
    val peer = row.peer
    val colors = MaterialTheme.colorScheme
    if (peer != null) {
        AppIcon(peer.icon, Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceContainerHighest))
    } else {
        val icon = when (row.kind) {
            OwnWakeKind.RESTART_AFTER_CLOSE -> BlockerIcons.RestartAltOutlined
            OwnWakeKind.SELF_START -> BlockerIcons.SyncAlt
            OwnWakeKind.SYSTEM_EVENT -> if (row.boot) BlockerIcons.PowerSettingsNew else BlockerIcons.Campaign
            OwnWakeKind.TIMED -> BlockerIcons.Alarm
            OwnWakeKind.SCREEN_OFF_RUN -> BlockerIcons.Bedtime
            null -> BlockerIcons.VerifiedUser
        }
        Box(Modifier.size(40.dp).background(colors.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
            BlockerActionIcon(icon, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
        }
    }
}

@SuppressLint("DesignSystem") // The wake action uses the primary content role.
@Composable
private fun WakeRowFacts(row: WakeDetailRow, onPeerClick: (String) -> Unit) {
    val style = MaterialTheme.typography.bodyMedium
    val labels = row.facts.map { stringResource(it.label) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.padding(start = 68.dp, end = 16.dp, bottom = 12.dp)) {
        val labelWidth = with(density) {
            val widest = labels.maxOfOrNull { measurer.measure(it, style, maxLines = 1).size.width } ?: 0
            maxOf(64.dp, widest.toDp()).coerceAtMost(maxWidth * 0.45f)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            row.facts.forEachIndexed { index, fact ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        labels[index],
                        Modifier.width(labelWidth),
                        style = style,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(fact.value.resolve(), Modifier.weight(1f), style = style, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            row.peer?.let { peer ->
                TextButton(
                    onClick = { onPeerClick(peer.packageName) },
                    contentPadding = PaddingValues(end = 12.dp),
                ) {
                    Text(stringResource(string.feature_wake_api_view_app, peer.label))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NormalWakeStarts(normal: JustifiedStarts, expanded: Boolean, onExpand: () -> Unit) {
    val parts = normal.parts()
    val total = parts.sumOf { it.amount }
    if (total == 0L) return
    val colors = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "Normal starts disclosure",
    )
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.surfaceContainer)) {
        SegmentedListItem(
            onClick = onExpand,
            shapes = ListItemDefaults.shapes(shape = shape),
            modifier = Modifier.heightIn(min = 56.dp).expandable(expanded, onExpand),
            colors = ListItemDefaults.segmentedColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(horizontal = 16.dp),
            leadingContent = { BlockerActionIcon(BlockerIcons.CheckCircle, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant) },
            trailingContent = {
                BlockerActionIcon(
                    BlockerIcons.ExpandMore,
                    null,
                    Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
                    tint = colors.onSurfaceVariant,
                )
            },
        ) {
            Text(WakeText.Count(plurals.feature_wake_api_normal_starts, total).resolve())
        }
        WakeExpanded(expanded) {
            Column(Modifier.padding(start = 48.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                parts.forEach { Text(it.resolve(), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WakeBackgroundCard(usage: BackgroundUsage, onToggle: (Boolean) -> Unit, busy: Boolean) {
    val label = stringResource(string.feature_wake_api_allow_background)
    SegmentedListItem(
        shapes = ListItemDefaults.shapes(shape = RoundedCornerShape(16.dp)),
        modifier = Modifier.heightIn(min = 72.dp),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        trailingContent = { WakeSwitch(usage.allowed, usage.changeable && !busy, label, onToggle, dimmed = !usage.changeable) },
    ) { Text(label) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeExpanded(expanded: Boolean, content: @Composable () -> Unit) {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(spec, expandFrom = Alignment.Top),
        exit = shrinkVertically(spec, shrinkTowards = Alignment.Top),
    ) { content() }
}

private fun Modifier.expandable(expanded: Boolean, onExpand: () -> Unit): Modifier = semantics {
    val action = {
        onExpand()
        true
    }
    if (expanded) collapse(action = action) else expand(action = action)
}
