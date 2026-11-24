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

package com.merxury.blocker.feature.appdetail.impl.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.BlockerLabelSmallText
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.condensedRegular
import com.merxury.blocker.core.model.component.BlockReason
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.ui.component.ComponentItemMenu
import com.merxury.blocker.core.ui.control.label
import com.merxury.blocker.feature.appdetail.api.R.string
import com.merxury.blocker.feature.appdetail.impl.ComponentRow
import com.merxury.blocker.feature.appdetail.impl.SwitchState
import com.merxury.blocker.core.ui.R.string as uiString

/** What a component row can ask for. */
class ComponentRowActions(
    /** The switch was turned; true means on. */
    val onSwitch: (ComponentRow, Boolean) -> Unit = { _, _ -> },
    /** The user chose to block or enable a component whose state is unknown. */
    val onControl: (ComponentRow, ControlGoal) -> Unit = { _, _ -> },
    /** The row was tapped outside selection mode. */
    val onClick: (ComponentRow) -> Unit = {},
    val onToggleSelected: (ComponentRow) -> Unit = {},
    val onStopService: (ComponentRow) -> Unit = {},
    val onLaunchActivity: (ComponentRow) -> Unit = {},
    val onCopyName: (ComponentRow) -> Unit = {},
    val onCopyFullName: (ComponentRow) -> Unit = {},
    /** Opens the rule editor of the component, also from the conditional rule tag. */
    val onEditIfwRule: (ComponentRow) -> Unit = {},
)

/**
 * One component: its names and description, why it is blocked when it is, and a switch that shows
 * the state the repository holds. While its change is in flight the switch shows progress and
 * ignores taps. When the state cannot be read, the switch is replaced by an unknown label that
 * explains why and offers to block or enable it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComponentRowItem(
    row: ComponentRow,
    inFlight: Boolean,
    actions: ComponentRowActions,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var explainUnknown by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val background by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.background,
        animationSpec = tween(300, 0, LinearEasing),
        label = "selection",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("component:${row.id.className}")
            .combinedClickable(
                onClick = { if (isSelectionMode) actions.onToggleSelected(row) else actions.onClick(row) },
                onLongClick = {
                    menuExpanded = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            )
            .background(color = background)
            .padding(start = 16.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ComponentTexts(row = row, onConditionalClick = { actions.onEditIfwRule(row) }, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Box {
            ComponentSwitch(row = row, inFlight = inFlight, onSwitch = { actions.onSwitch(row, it) }, onExplainUnknown = { explainUnknown = true })
            ComponentItemMenu(
                expanded = menuExpanded,
                type = row.type,
                isServiceRunning = row.isRunning,
                onStopServiceClick = { actions.onStopService(row) },
                onLaunchActivityClick = { actions.onLaunchActivity(row) },
                onCopyNameClick = { actions.onCopyName(row) },
                onCopyPackageNameClick = { actions.onCopyFullName(row) },
                onEditIfwRuleClick = { actions.onEditIfwRule(row) },
                onDismissRequest = { menuExpanded = false },
            )
        }
    }
    val cause = row.unknownCause
    if (explainUnknown && cause != null) {
        UnknownStateDialog(
            cause = cause,
            onControl = { goal -> actions.onControl(row, goal) },
            onDismissRequest = { explainUnknown = false },
        )
    }
}

@Composable
private fun ComponentTexts(row: ComponentRow, onConditionalClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlockerBodyLargeText(modifier = Modifier.weight(1f, fill = false), text = row.id.simpleName)
            if (row.isRunning) {
                Spacer(modifier = Modifier.width(8.dp))
                Tag(
                    text = stringResource(uiString.core_ui_running),
                    container = MaterialTheme.colorScheme.tertiary,
                    content = MaterialTheme.colorScheme.onTertiary,
                )
            }
        }
        BlockerBodyMediumText(text = row.id.className, style = MaterialTheme.typography.bodyMedium.condensedRegular())
        row.description?.let {
            Spacer(modifier = Modifier.height(4.dp))
            BlockerBodyMediumText(text = it, style = MaterialTheme.typography.bodyMedium.condensedRegular())
        }
        if (row.blockedBy.isNotEmpty() || row.conditional) {
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                REASON_ORDER.filter { it in row.blockedBy }.forEach { reason ->
                    Tag(
                        text = stringResource(reason.label()),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (row.conditional) {
                    Tag(
                        text = stringResource(uiString.core_ui_conditional_rule),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = onConditionalClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComponentSwitch(row: ComponentRow, inFlight: Boolean, onSwitch: (Boolean) -> Unit, onExplainUnknown: () -> Unit) {
    when {
        inFlight -> {
            val changing = stringResource(string.feature_appdetail_api_changing)
            BlockerSwitch(
                checked = row.switch == SwitchState.ON,
                onCheckedChange = null,
                enabled = false,
                thumbContent = { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) },
                modifier = Modifier.semantics { contentDescription = changing },
            )
        }

        row.switch == SwitchState.UNKNOWN -> {
            val why = stringResource(string.feature_appdetail_api_unknown_state_title)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onExplainUnknown)
                    .semantics { contentDescription = why }
                    .padding(start = 8.dp),
            ) {
                BlockerLabelSmallText(text = stringResource(uiString.core_ui_unknown))
                IconButton(onClick = onExplainUnknown) {
                    Icon(imageVector = BlockerIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        else -> BlockerSwitch(checked = row.switch == SwitchState.ON, onCheckedChange = onSwitch)
    }
}

@Composable
private fun Tag(text: String, container: Color, content: Color, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(4.dp)
    BlockerLabelSmallText(
        text = text,
        color = content,
        modifier = Modifier
            .clip(shape)
            .background(container, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

private val REASON_ORDER = listOf(BlockReason.PM_SETTING, BlockReason.IFW_RULE, BlockReason.MANIFEST_DEFAULT)
