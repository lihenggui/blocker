// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string

// Compose keeps ListTokens.ItemLeadingAvatarSize internal.
internal val WakeListAvatarSize = 40.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.wakeAppGroup(
    key: String,
    @StringRes title: Int,
    apps: List<WakeAppRow>,
    onAppClick: (String) -> Unit,
) {
    if (apps.isEmpty()) return

    item(key = "$key-header", contentType = "group header") {
        WakeGroupHeader(title, Modifier.animateWakeItem(this))
    }
    itemsIndexed(apps, key = { _, app -> "app:${app.packageName}" }, contentType = { _, _ -> "app" }) { index, app ->
        SegmentedListItem(
            onClick = { onAppClick(app.packageName) },
            shapes = animatedSegmentedShapes(index, apps.size),
            colors = wakeSegmentedColors(),
            modifier = Modifier.animateWakeItem(this)
                .padding(top = if (index == 0) 0.dp else ListItemDefaults.SegmentedGap),
            leadingContent = { AppIcon(app.icon, Modifier.size(WakeListAvatarSize)) },
            supportingContent = app.headline?.let { headline ->
                { Text(headline.reasonText(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
            content = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
    item(key = "$key-spacing", contentType = "group spacing") {
        Spacer(Modifier.animateWakeItem(this).height(WakeContentSpacing))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.wakeOverviewTotals(state: WakeOverviewUiState.Ready) {
    val automatic = state.automaticBlocks
    if (automatic > 0) {
        item(key = "automaticBlocks") {
            WakeInformationalRow(
                text = pluralStringResource(plurals.feature_wake_api_automatic_blocks, automatic, automatic),
                icon = BlockerIcons.VerifiedUser,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                modifier = Modifier.animateWakeItem(this),
            )
        }
    }
    val other = state.otherApps
    if (other > 0 && state.dormantApps.isEmpty()) {
        item(key = "otherApps") {
            Text(
                text = pluralStringResource(plurals.feature_wake_api_other_apps, other, other),
                modifier = Modifier.animateWakeItem(this).padding(ListItemDefaults.ContentPadding),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun LazyListScope.wakeOverviewAppLists(
    state: WakeOverviewUiState.Ready,
    blockedExpanded: Boolean,
    dormantExpanded: Boolean,
    onToggleBlocked: () -> Unit,
    onToggleDormant: () -> Unit,
    onAppClick: (String) -> Unit,
) {
    if (state.blockedApps.isNotEmpty()) {
        val apps = state.blockedApps
        overviewDisclosure("blockedApps", plurals.feature_wake_api_section_blocked_apps, apps.size, BlockerIcons.LinkOff, blockedExpanded, onToggleBlocked)
        if (blockedExpanded) overviewAppItems("blocked", apps.map { WakeAppIdentity(it.packageName, it.label, it.icon) }, onAppClick)
    }
    if (state.dormantApps.isNotEmpty()) {
        overviewDisclosure("otherApps", plurals.feature_wake_api_other_apps, state.otherApps, BlockerIcons.Apps, dormantExpanded, onToggleDormant)
        if (dormantExpanded) overviewAppItems("dormant", state.dormantApps, onAppClick)
    }
}

internal fun LazyListScope.wakeCollectingApps(apps: List<WakeAppIdentity>, expanded: Boolean, onExpand: () -> Unit, onAppClick: (String) -> Unit) {
    if (apps.isEmpty()) return
    item(key = "installedApps") {
        WakeDisclosure(
            title = stringResource(string.feature_wake_api_browse_installed_apps),
            icon = BlockerIcons.Apps,
            expanded = expanded,
            onExpand = onExpand,
            modifier = Modifier.padding(top = WakeContentSpacing),
            supporting = stringResource(string.feature_wake_api_browse_installed_apps_description),
        )
    }
    if (expanded) overviewAppItems("installed", apps, onAppClick)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WakeOverviewSearchResults(results: List<WakeSearchRow>, onAppClick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (results.isEmpty()) {
        Box(modifier.fillMaxSize().padding(WakeContentSpacing), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(string.feature_wake_api_search_empty),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(WakeContentSpacing),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(results, key = { _, row -> row.app.packageName }) { index, (app, group) ->
            SegmentedListItem(
                onClick = { onAppClick(app.packageName) },
                shapes = animatedSegmentedShapes(index, results.size),
                colors = wakeSegmentedColors(),
                leadingContent = { AppIcon(app.icon, Modifier.size(WakeListAvatarSize)) },
                supportingContent = { Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = { WakeSearchBadge(group) },
            ) { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun WakeSearchBadge(group: WakeSearchGroup) {
    val colors = MaterialTheme.colorScheme
    val (text, container, content) = when (group) {
        WakeSearchGroup.KeepAlive -> Triple(string.feature_wake_api_search_keep_alive, colors.errorContainer, colors.onErrorContainer)
        WakeSearchGroup.Attention -> Triple(string.feature_wake_api_search_attention, colors.tertiaryContainer, colors.onTertiaryContainer)
        WakeSearchGroup.Blocked -> Triple(string.feature_wake_api_blocked, colors.primaryContainer, colors.onPrimaryContainer)
        WakeSearchGroup.Dormant -> Triple(string.feature_wake_api_search_dormant, colors.surfaceContainerHighest, colors.onSurfaceVariant)
    }
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = stringResource(text),
            modifier = Modifier.padding(horizontal = WakeGridUnit * 2, vertical = WakeGridUnit),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.overviewDisclosure(
    key: String,
    @PluralsRes title: Int,
    count: Int,
    icon: ImageVector,
    expanded: Boolean,
    onExpand: () -> Unit,
) {
    item(key = key) {
        WakeDisclosure(
            title = pluralStringResource(title, count, count),
            icon = icon,
            expanded = expanded,
            onExpand = onExpand,
            modifier = Modifier.padding(top = ListItemDefaults.SegmentedGap),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.overviewAppItems(key: String, apps: List<WakeAppIdentity>, onAppClick: (String) -> Unit) {
    itemsIndexed(apps, key = { _, app -> "$key:${app.packageName}" }, contentType = { _, _ -> "overview app" }) { index, app ->
        SegmentedListItem(
            onClick = { onAppClick(app.packageName) },
            shapes = ListItemDefaults.segmentedShapes(index, apps.size),
            colors = wakeSegmentedColors(),
            modifier = Modifier.padding(top = ListItemDefaults.SegmentedGap),
            leadingContent = { AppIcon(app.icon, Modifier.size(WakeListAvatarSize)) },
            content = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WakeFirstRunSection() {
    val rows = listOf(
        BlockerIcons.RestartAlt to string.feature_wake_api_looks_for_restart,
        BlockerIcons.SwapHoriz to string.feature_wake_api_looks_for_each_other,
        BlockerIcons.Bedtime to string.feature_wake_api_looks_for_screen_off,
    )
    Column {
        WakeGroupHeader(string.feature_wake_api_looks_for)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { index, (icon, text) ->
                WakeInformationalRow(stringResource(text), icon, ListItemDefaults.segmentedShapes(index, rows.size))
            }
        }
    }
}

@Composable
internal fun WakeGroupHeader(@StringRes title: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(title),
        modifier = modifier.padding(ListItemDefaults.ContentPadding).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeInformationalRow(
    text: String,
    icon: ImageVector,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        shapes = shapes,
        colors = wakeSegmentedColors(),
        modifier = modifier,
        leadingContent = {
            BlockerActionIcon(icon, contentDescription = null, tint = LocalContentColor.current)
        },
        content = { Text(text) },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Modifier.animateWakeItem(scope: LazyItemScope): Modifier = with(scope) {
    val motion = MaterialTheme.motionScheme
    animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec(),
        fadeOutSpec = motion.defaultEffectsSpec(),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun animatedSegmentedShapes(index: Int, count: Int): ListItemShapes {
    val inner = ListItemDefaults.shapes()
    val outer = ListItemDefaults.segmentedShapes(index = 0, count = 1, defaultShapes = inner)
    val innerShape = inner.shape as? CornerBasedShape ?: return inner
    val outerShape = outer.shape as? CornerBasedShape ?: return outer
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val top = animateFloatAsState(if (index == 0) 1f else 0f, spec, label = "Top corners")
    val bottom = animateFloatAsState(if (index == count - 1) 1f else 0f, spec, label = "Bottom corners")
    // Keep the shape stable while its corners animate so the list item retains its interaction state.
    return remember(inner, outer, top, bottom) {
        inner.copy(
            shape = innerShape.copy(
                topStart = AnimatedCorner(innerShape.topStart, outerShape.topStart, top),
                topEnd = AnimatedCorner(innerShape.topEnd, outerShape.topEnd, top),
                bottomEnd = AnimatedCorner(innerShape.bottomEnd, outerShape.bottomEnd, bottom),
                bottomStart = AnimatedCorner(innerShape.bottomStart, outerShape.bottomStart, bottom),
            ),
        )
    }
}

internal class AnimatedCorner(
    private val inner: CornerSize,
    private val outer: CornerSize,
    private val fraction: State<Float>,
) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float = lerp(
        inner.toPx(shapeSize, density),
        outer.toPx(shapeSize, density),
        fraction.value,
    ).coerceAtLeast(0f)
}

// The screen background is `surface`, so the segments take `surfaceContainer` to stay visible as a group.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun wakeSegmentedColors() = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
