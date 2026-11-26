// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.annotation.StringRes
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string

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
            modifier = Modifier.animateWakeItem(this)
                .padding(top = if (index == 0) 0.dp else ListItemDefaults.SegmentedGap)
                .heightIn(min = 72.dp),
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            leadingContent = { AppIcon(app.icon, Modifier.size(40.dp)) },
            supportingContent = {
                Text(app.headline.reasonText(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            content = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
    item(key = "$key-spacing", contentType = "group spacing") { Spacer(Modifier.animateWakeItem(this).height(20.dp)) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.wakeOverviewTotals(state: WakeOverviewUiState.Ready) {
    val automatic = state.automaticBlocks
    if (automatic > 0) {
        item(key = "automaticBlocks") {
            WakeInformationalRow(
                text = pluralStringResource(plurals.feature_wake_api_automatic_blocks, automatic, automatic),
                icon = BlockerIcons.VerifiedUser,
                shapes = ListItemDefaults.shapes(shape = RoundedCornerShape(16.dp)),
                modifier = Modifier.animateWakeItem(this),
            )
        }
    }
    val other = state.otherApps
    if (other > 0) {
        item(key = "otherApps") {
            Text(
                text = pluralStringResource(plurals.feature_wake_api_other_apps, other, other),
                modifier = Modifier.animateWakeItem(this).padding(start = 16.dp, end = 16.dp, top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
private fun WakeGroupHeader(@StringRes title: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(title),
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).semantics { heading() },
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
        modifier = modifier.heightIn(min = 56.dp),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = {
            BlockerActionIcon(icon, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        content = { Text(text) },
    )
}

private fun Modifier.animateWakeItem(scope: LazyItemScope): Modifier = with(scope) {
    animateItem(
        fadeInSpec = tween(210, easing = CubicBezierEasing(0f, 0f, 0f, 1f)),
        placementSpec = tween(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
        fadeOutSpec = tween(150, easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun animatedSegmentedShapes(index: Int, count: Int): ListItemShapes {
    val shapes = ListItemDefaults.segmentedShapes(index, count)
    val target = shapes.shape as? CornerBasedShape ?: return shapes
    val topStart by animateDpAsState(rowCorner(target.topStart), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "Top start")
    val topEnd by animateDpAsState(rowCorner(target.topEnd), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "Top end")
    val bottomEnd by animateDpAsState(rowCorner(target.bottomEnd), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "Bottom end")
    val bottomStart by animateDpAsState(rowCorner(target.bottomStart), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "Bottom start")
    return shapes.copy(shape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart))
}

@Composable
private fun rowCorner(corner: CornerSize): Dp = with(LocalDensity.current) {
    val rowHeight = 72.dp.toPx()
    corner.toPx(Size(rowHeight, rowHeight), this).toDp()
}
