// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons

// Material Switch does not expose the thumb travel or separate spatial and effects specs.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WakeSwitch(
    allowed: Boolean,
    enabled: Boolean,
    label: String,
    onChange: (Boolean) -> Unit,
    dimmed: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val track by animateColorAsState(
        if (allowed) colors.primary else colors.surfaceContainerHighest,
        motion.fastEffectsSpec(),
        label = "Switch track",
    )
    val border by animateColorAsState(
        if (allowed) colors.outline.copy(alpha = 0f) else colors.outline,
        motion.fastEffectsSpec(),
        label = "Switch outline",
    )
    val thumb by animateColorAsState(
        if (allowed) colors.onPrimary else colors.outline,
        motion.fastEffectsSpec(),
        label = "Switch thumb",
    )
    val mark by animateColorAsState(
        if (allowed) colors.onPrimaryContainer else colors.surfaceContainerHighest,
        motion.fastEffectsSpec(),
        label = "Switch mark",
    )
    val position by animateDpAsState(if (allowed) 24.dp else 4.dp, motion.fastSpatialSpec(), label = "Switch position")
    Box(
        Modifier.semantics { contentDescription = label }
            .toggleable(allowed, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(52.dp, 32.dp).graphicsLayer { alpha = if (dimmed) 0.38f else 1f }
                .background(track, CircleShape).border(2.dp, border, CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.offset(x = position).size(24.dp).background(thumb, CircleShape), contentAlignment = Alignment.Center) {
                BlockerActionIcon(if (allowed) BlockerIcons.CheckSmall else BlockerIcons.Close, null, Modifier.size(16.dp), tint = mark)
            }
        }
    }
}
