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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.model.system.DeepBackgroundRestriction
import com.merxury.blocker.core.model.wake.JustifiedStarts
import com.merxury.blocker.core.model.wake.OwnWakeKind
import com.merxury.blocker.core.model.wake.WakeAppEnvironment
import com.merxury.blocker.core.model.wake.WakeSpecialAccess
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.wakeDetailGroup(
    title: Int,
    rows: List<WakeDetailRow>,
    expanded: String?,
    background: BackgroundUsage?,
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
            switchEnabled = !busy && row.canToggle(background),
            onExpand = { onExpand(row.key) },
            onToggle = { onToggle(row.key, it) },
            onPeerClick = onPeerClick,
            modifier = Modifier.padding(bottom = if (index == rows.lastIndex) WakeContentSpacing else ListItemDefaults.SegmentedGap),
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
    switchEnabled: Boolean,
    onExpand: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onPeerClick: (String) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val shapes = wakeDetailShapes(ListItemDefaults.segmentedShapes(index, count), expanded)
    val defaultColors = wakeSegmentedColors()
    val container by animateColorAsState(
        if (expanded) colors.surfaceContainerHigh else defaultColors.containerColor,
        motion.defaultEffectsSpec(),
        label = "Wake row container",
    )
    val metaColor by animateColorAsState(
        when {
            row.reEnabledByApp -> colors.error
            row.blocked -> colors.primary
            else -> defaultColors.supportingContentColor
        },
        motion.defaultEffectsSpec(),
        label = "Wake row supporting text",
    )
    val title = row.title.resolve()
    Column(modifier.clip(shapes.shape).background(container)) {
        SegmentedListItem(
            onClick = onExpand,
            shapes = shapes,
            modifier = Modifier.expandable(expanded, onExpand),
            colors = defaultColors.copy(containerColor = container, supportingContentColor = metaColor),
            leadingContent = { WakeRowIcon(row) },
            supportingContent = row.meta?.let { meta -> { Text(meta.resolve()) } },
            trailingContent = row.takeIf { it.hasSwitch }?.let {
                {
                    WakeAllowedSwitch(
                        allowed = !row.blocked,
                        enabled = switchEnabled,
                        label = stringResource(string.feature_wake_api_allow_path, row.actionTitle.resolve()),
                        onToggle = onToggle,
                    )
                }
            },
        ) { Text(title) }
        WakeExpanded(expanded) { WakeRowFacts(row, onPeerClick) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun wakeDetailShapes(shapes: ListItemShapes, expanded: Boolean): ListItemShapes {
    val base = shapes.shape as? CornerBasedShape ?: return shapes
    val open = MaterialTheme.shapes.large
    val progress = animateFloatAsState(
        if (expanded) 1f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "Wake row corners",
    )
    return remember(shapes, base, open, progress) {
        shapes.copy(
            shape = base.copy(
                topStart = AnimatedCorner(base.topStart, open.topStart, progress),
                topEnd = AnimatedCorner(base.topEnd, open.topEnd, progress),
                bottomEnd = AnimatedCorner(base.bottomEnd, open.bottomEnd, progress),
                bottomStart = AnimatedCorner(base.bottomStart, open.bottomStart, progress),
            ),
        )
    }
}

@Composable
private fun WakeRowIcon(row: WakeDetailRow) {
    val peer = row.peer
    val modifier = Modifier.size(WakeListAvatarSize).clip(IconButtonDefaults.smallRoundShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    if (peer != null) {
        AppIcon(peer.icon, modifier)
    } else {
        val icon = when (row.kind) {
            OwnWakeKind.RESTART_AFTER_CLOSE -> BlockerIcons.RestartAltOutlined
            OwnWakeKind.SELF_START -> BlockerIcons.SyncAlt
            OwnWakeKind.SYSTEM_EVENT -> if (row.boot) BlockerIcons.PowerSettingsNew else BlockerIcons.Campaign
            OwnWakeKind.TIMED -> BlockerIcons.Alarm
            OwnWakeKind.SCREEN_OFF_RUN -> BlockerIcons.Bedtime
            null -> BlockerIcons.VerifiedUser
        }
        Box(modifier, contentAlignment = Alignment.Center) {
            BlockerActionIcon(icon, null, tint = LocalContentColor.current)
        }
    }
}

@SuppressLint("DesignSystem") // The wake action uses the primary content role.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeRowFacts(row: WakeDetailRow, onPeerClick: (String) -> Unit) {
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    Column {
        row.facts.forEach { fact ->
            ListItem(colors = colors, supportingContent = { Text(fact.value.resolve()) }) { Text(stringResource(fact.label)) }
        }
        row.peer?.let { peer ->
            TextButton(onClick = { onPeerClick(peer.packageName) }, modifier = Modifier.padding(ListItemDefaults.ContentPadding)) {
                Text(stringResource(string.feature_wake_api_view_app, peer.label))
            }
        }
    }
}

// A disclosure chevron flips by a half turn between collapsed and expanded.
private const val EXPANDED_CHEVRON_ROTATION = 180f

@SuppressLint("DesignSystem") // The wake action uses the primary content role.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NormalWakeStarts(
    normal: JustifiedStarts,
    expanded: Boolean,
    onExpand: () -> Unit,
    onComponents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parts = normal.parts()
    val total = parts.sumOf { it.amount }
    if (total == 0L) return
    WakeDisclosure(
        title = WakeText.Count(plurals.feature_wake_api_normal_starts, total).resolve(),
        icon = BlockerIcons.CheckCircle,
        expanded = expanded,
        onExpand = onExpand,
        modifier = modifier,
    ) {
        Column {
            val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
            parts.forEach { ListItem(colors = itemColors) { Text(it.resolve()) } }
            TextButton(onClick = onComponents, modifier = Modifier.padding(ListItemDefaults.ContentPadding)) {
                Text(stringResource(string.feature_wake_api_view_components))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PotentialWakeEntries(
    rows: List<WakeDetailRow>,
    expanded: Boolean,
    expandedRow: String?,
    busy: Boolean,
    onExpand: () -> Unit,
    onExpandRow: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    WakeDisclosure(
        title = WakeText.Count(plurals.feature_wake_api_potential_entries, rows.size.toLong()).resolve(),
        icon = BlockerIcons.Widgets,
        expanded = expanded,
        onExpand = onExpand,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { index, row ->
                key(row.key) {
                    WakeDetailItem(
                        row = row,
                        index = index,
                        count = rows.size,
                        expanded = expandedRow == row.key,
                        switchEnabled = !busy,
                        onExpand = { onExpandRow(row.key) },
                        onToggle = { onToggle(row.key, it) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WakeDisclosure(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val rotation by animateFloatAsState(
        if (expanded) EXPANDED_CHEVRON_ROTATION else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "Wake disclosure",
    )
    val shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1)
    val colors = wakeSegmentedColors()
    Column(modifier.clip(shapes.shape).background(colors.containerColor)) {
        SegmentedListItem(
            onClick = onExpand,
            shapes = shapes,
            modifier = Modifier.expandable(expanded, onExpand),
            colors = colors,
            leadingContent = { BlockerActionIcon(icon, null, tint = LocalContentColor.current) },
            trailingContent = {
                BlockerActionIcon(BlockerIcons.ExpandMore, null, Modifier.graphicsLayer { rotationZ = rotation }, tint = LocalContentColor.current)
            },
        ) { Text(title) }
        WakeExpanded(expanded, content)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WakeBackgroundCard(usage: BackgroundUsage, onToggle: (Boolean) -> Unit, busy: Boolean) {
    val label = stringResource(string.feature_wake_api_allow_background)
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors = wakeSegmentedColors(),
        trailingContent = { WakeAllowedSwitch(usage.allowed, usage.changeable && !busy, label, onToggle) },
    ) { Text(label) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WakeDeepRestrictionCard(
    restriction: DeepBackgroundRestriction,
    onToggle: (Boolean) -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(string.feature_wake_api_deep_restriction)
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors = wakeSegmentedColors(),
        modifier = modifier,
        supportingContent = { Text(stringResource(string.feature_wake_api_deep_restriction_desc)) },
        trailingContent = {
            BlockerSwitch(
                checked = restriction.anyActive,
                onCheckedChange = onToggle,
                enabled = restriction.changeable && !busy,
                modifier = Modifier.semantics { contentDescription = label },
            )
        },
    ) { Text(label) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WakeEnvironmentCard(environment: WakeAppEnvironment, modifier: Modifier = Modifier) {
    val labels = buildList {
        if (environment.userIdleAllowlisted || environment.systemIdleAllowlisted) {
            add(stringResource(string.feature_wake_api_battery_allowlist))
        }
        WakeSpecialAccess.entries.filter { it in environment.specialAccess }.forEach {
            add(stringResource(it.label))
        }
    }
    if (labels.isEmpty()) return
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors = wakeSegmentedColors(),
        modifier = modifier,
        leadingContent = { BlockerActionIcon(BlockerIcons.Info, null, tint = LocalContentColor.current) },
        supportingContent = { Text(labels.joinToString(" · ")) },
    ) { Text(stringResource(string.feature_wake_api_environment)) }
}

private val WakeSpecialAccess.label get() = when (this) {
    WakeSpecialAccess.ACCESSIBILITY -> string.feature_wake_api_accessibility
    WakeSpecialAccess.NOTIFICATION_LISTENER -> string.feature_wake_api_notification_listener
    WakeSpecialAccess.DEVICE_ADMIN -> string.feature_wake_api_device_admin
    WakeSpecialAccess.ALWAYS_ON_VPN -> string.feature_wake_api_always_on_vpn
    WakeSpecialAccess.COMPANION_DEVICE -> string.feature_wake_api_companion_device
    WakeSpecialAccess.INPUT_METHOD -> string.feature_wake_api_input_method
    WakeSpecialAccess.LIVE_WALLPAPER -> string.feature_wake_api_live_wallpaper
    WakeSpecialAccess.WIDGET -> string.feature_wake_api_home_widget
    WakeSpecialAccess.SYNC_ADAPTER -> string.feature_wake_api_account_sync
}

@Composable
private fun WakeAllowedSwitch(allowed: Boolean, enabled: Boolean, label: String, onToggle: (Boolean) -> Unit) {
    BlockerSwitch(
        checked = allowed,
        onCheckedChange = onToggle,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
    )
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
