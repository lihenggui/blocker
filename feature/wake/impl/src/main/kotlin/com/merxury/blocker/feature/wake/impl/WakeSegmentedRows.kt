// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun WakeGroupHeader(@StringRes title: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(title),
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun animatedSegmentedShapes(index: Int, count: Int, expanded: Boolean = false): ListItemShapes {
    val shapes = ListItemDefaults.segmentedShapes(index, count)
    val target = if (expanded) RoundedCornerShape(16.dp) else shapes.shape as? CornerBasedShape ?: return shapes
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
