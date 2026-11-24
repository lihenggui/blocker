/*
 * Copyright 2026 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.feature.search.impl.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.feature.search.api.R.string
import com.merxury.blocker.feature.search.impl.ComponentGroup
import com.merxury.blocker.feature.search.impl.IndexingProgress

/**
 * The app the components found belong to: its icon, or a check while it is selected, its name and
 * how many of each type were found. A tap opens the app or, in selection mode, selects it; a long
 * press starts selection mode with it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComponentGroupItem(
    group: ComponentGroup,
    selected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.background,
        animationSpec = tween(300, 0, LinearEasing),
        label = "color",
    )
    val cornerRadius by animateDpAsState(targetValue = if (selected) 12.dp else 0.dp, label = "shape")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .testTag("search:group:${group.app.packageName}")
            .background(color = background, shape = RoundedCornerShape(cornerRadius))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (!isSelectionMode) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Crossfade(selected, animationSpec = tween(500), label = "icon") { checked ->
            if (checked) {
                Icon(
                    imageVector = BlockerIcons.Check,
                    modifier = Modifier.size(48.dp),
                    contentDescription = stringResource(string.feature_search_api_check_icon),
                )
            } else {
                AppIcon(group.icon, Modifier.size(48.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            BlockerBodyLargeText(text = group.label)
            BlockerBodyMediumText(text = countsOf(group))
        }
    }
}

/** That the components of every app are still being read: a thin bar, filled as far as it got. */
@Composable
internal fun IndexingBar(progress: IndexingProgress, modifier: Modifier = Modifier) {
    val bar = modifier.fillMaxWidth().testTag("search:indexing")
    if (progress.total == 0) {
        LinearProgressIndicator(modifier = bar)
    } else {
        LinearProgressIndicator(progress = { progress.done.toFloat() / progress.total }, modifier = bar)
    }
}
