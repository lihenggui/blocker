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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.BlockerLabelSmallText
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.condensedRegular
import com.merxury.blocker.core.model.component.BlockReason
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.core.ui.control.label
import com.merxury.blocker.feature.search.api.R.string
import com.merxury.blocker.feature.search.impl.ComponentGroup
import com.merxury.blocker.feature.search.impl.FoundComponent
import com.merxury.blocker.feature.search.impl.FoundState
import com.merxury.blocker.feature.search.impl.IndexingProgress
import com.merxury.blocker.core.ui.R.string as uiString

/**
 * The header of the components found in one app: its icon, or a check when every one of them is
 * selected, its name and how many of each type were found. A tap opens the app or, in selection
 * mode, selects them all; a long press starts selection mode with them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComponentGroupHeader(
    group: ComponentGroup,
    expanded: Boolean,
    allSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val background by animateColorAsState(
        targetValue = if (allSelected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.background,
        animationSpec = tween(300, 0, LinearEasing),
        label = "selection",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .testTag("search:group:${group.app.packageName}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (!isSelectionMode) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                },
            )
            .background(background)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
    ) {
        Crossfade(allSelected, animationSpec = tween(500), label = "icon") { selected ->
            if (selected) {
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
        Column(modifier = Modifier.weight(1f)) {
            BlockerBodyLargeText(text = group.label)
            BlockerBodyMediumText(text = countsOf(group))
        }
        IconButton(onClick = onExpandClick) {
            Icon(
                imageVector = if (expanded) BlockerIcons.ExpandLess else BlockerIcons.ExpandMore,
                contentDescription = stringResource(if (expanded) uiString.core_ui_collapse_list else uiString.core_ui_expand_list),
            )
        }
    }
}

/**
 * One component found: its names, why it is blocked when it is, and its state as the cache holds
 * it. A tap opens it in its app or, in selection mode, selects it; a long press starts selection
 * mode with it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FoundComponentItem(
    component: FoundComponent,
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
        label = "selection",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .testTag("search:component:${component.id.className}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (!isSelectionMode) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                },
            )
            .background(background)
            .padding(start = 80.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BlockerBodyLargeText(text = component.id.simpleName)
            BlockerBodyMediumText(text = component.id.className, style = MaterialTheme.typography.bodyMedium.condensedRegular())
            StateTags(component)
        }
        Spacer(modifier = Modifier.width(8.dp))
        StateIcon(component)
    }
}

@Composable
private fun StateTags(component: FoundComponent) {
    val reasons = REASON_ORDER.filter { it in component.blockedBy }
    if (reasons.isEmpty() && !component.conditional && component.state != FoundState.UNKNOWN) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        reasons.forEach { reason -> Tag(stringResource(reason.label())) }
        if (component.conditional) Tag(stringResource(uiString.core_ui_conditional_rule))
        if (component.state == FoundState.UNKNOWN) Tag(stringResource(uiString.core_ui_unknown))
    }
}

/** Progress while a batch changes the component, otherwise whether it can run; the tags tell why not. */
@Composable
private fun StateIcon(component: FoundComponent) {
    when {
        component.inFlight -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        component.state == FoundState.RUNNABLE -> Icon(BlockerIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        component.state == FoundState.BLOCKED -> Icon(BlockerIcons.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        else -> Icon(BlockerIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Tag(text: String, container: Color = MaterialTheme.colorScheme.secondaryContainer, content: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    val shape = RoundedCornerShape(4.dp)
    BlockerLabelSmallText(
        text = text,
        color = content,
        modifier = Modifier
            .clip(shape)
            .background(container, shape)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/** How far the reading of every app's components got: a line of text over a thin bar. */
@Composable
internal fun IndexingLine(progress: IndexingProgress, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("search:indexing")) {
        val text = if (progress.total == 0) {
            stringResource(string.feature_search_api_indexing)
        } else {
            stringResource(string.feature_search_api_indexing_progress, progress.done, progress.total)
        }
        BlockerBodyMediumText(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        if (progress.total == 0) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { progress.done.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val REASON_ORDER = listOf(BlockReason.PM_SETTING, BlockReason.IFW_RULE, BlockReason.MANIFEST_DEFAULT)
