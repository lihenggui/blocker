/*
 * Copyright 2025 Blocker
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

@file:Suppress("ktlint:compose:multiple-emitters-check")

package com.merxury.blocker.core.ui.ifwruleeditor

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerOutlinedButton
import com.merxury.blocker.core.designsystem.component.BlockerOutlinedCard
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.R
import com.merxury.core.ifw.editor.IfwEditorGroupMode
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.editor.IfwEditorPortMode

@Composable
internal fun GroupEditorCard(
    group: IfwEditorNode.Group,
    depth: Int,
    isRoot: Boolean,
    onUpdate: (IfwEditorNode) -> Unit,
    onDelete: (String) -> Unit,
    onAddGroup: (String) -> Unit,
    onAddCondition: (String) -> Unit,
    modifier: Modifier = Modifier,
    validationMessage: String? = null,
) {
    var expanded by rememberSaveable(group.id) {
        mutableStateOf(isRoot || group.children.isEmpty())
    }
    val canCollapse = !isRoot && group.children.isNotEmpty()
    val isExpanded = isRoot || group.children.isEmpty() || expanded

    GroupEditorContainer(
        isRoot = isRoot,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (depth * 8).dp, top = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
        ) {
            GroupHeader(
                group = group,
                isRoot = isRoot,
                canCollapse = canCollapse,
                expanded = isExpanded,
                onToggleExpansion = { expanded = !expanded },
                onDelete = { onDelete(group.id) },
            )

            if (!isExpanded) {
                val collapsedSummary = collapsedGroupSummary(group)
                if (collapsedSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = collapsedSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                return@Column
            }

            Spacer(modifier = Modifier.height(12.dp))
            GroupModeSelector(
                selected = group.mode,
                onSelect = { mode -> onUpdate(group.copy(mode = mode)) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            SwitchRow(
                label = stringResource(R.string.core_ui_ifw_group_exclude),
                checked = group.excluded,
                onCheckedChange = { excluded -> onUpdate(group.copy(excluded = excluded)) },
            )
            validationMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (group.children.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                group.children.forEach { child ->
                    when (child) {
                        is IfwEditorNode.Condition -> ConditionEditorCard(
                            condition = child,
                            depth = depth + 1,
                            onUpdate = onUpdate,
                            onDelete = onDelete,
                        )
                        is IfwEditorNode.Group -> GroupEditorCard(
                            group = child,
                            depth = depth + 1,
                            isRoot = false,
                            validationMessage = null,
                            onUpdate = onUpdate,
                            onDelete = onDelete,
                            onAddGroup = onAddGroup,
                            onAddCondition = onAddCondition,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            GroupAddActions(
                isRoot = isRoot,
                onAddCondition = { onAddCondition(group.id) },
                onAddGroup = { onAddGroup(group.id) },
            )
        }
    }
}

@Composable
private fun GroupHeader(
    group: IfwEditorNode.Group,
    isRoot: Boolean,
    canCollapse: Boolean,
    expanded: Boolean,
    onToggleExpansion: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (canCollapse) {
                        Modifier.clickable(onClick = onToggleExpansion)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Text(
                text = stringResource(group.titleRes),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(group.summaryRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canCollapse) {
            IconButton(onClick = onToggleExpansion) {
                Icon(
                    imageVector = if (expanded) BlockerIcons.ExpandLess else BlockerIcons.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.core_ui_collapse_list else R.string.core_ui_expand_list,
                    ),
                )
            }
        }
        if (!isRoot) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = BlockerIcons.Close,
                    contentDescription = stringResource(R.string.core_ui_close),
                )
            }
        }
    }
}

@Composable
private fun GroupEditorContainer(
    isRoot: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (isRoot) {
        BlockerOutlinedCard(
            outerPadding = 0.dp,
            modifier = modifier,
        ) {
            content()
        }
    } else {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = MaterialTheme.shapes.large,
        ) {
            content()
        }
    }
}

@Composable
private fun GroupAddActions(
    isRoot: Boolean,
    onAddCondition: () -> Unit,
    onAddGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isRoot) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BlockerOutlinedButton(
                onClick = onAddCondition,
                text = { Text(stringResource(R.string.core_ui_ifw_add_condition)) },
                modifier = Modifier.weight(1f),
            )
            BlockerOutlinedButton(
                onClick = onAddGroup,
                text = { Text(stringResource(R.string.core_ui_ifw_add_group)) },
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            BlockerOutlinedButton(
                onClick = onAddCondition,
                text = { Text(stringResource(R.string.core_ui_ifw_add_condition)) },
                modifier = Modifier.fillMaxWidth(),
            )
            BlockerTextButton(
                onClick = onAddGroup,
                text = { Text(stringResource(R.string.core_ui_ifw_add_group)) },
            )
        }
    }
}

@Composable
private fun collapsedGroupSummary(group: IfwEditorNode.Group): String = group.children
    .take(2)
    .map { child -> collapsedNodeSummary(child) }
    .joinToString(", ")
    .let { prefix ->
        val remaining = group.children.size - 2
        if (remaining > 0 && prefix.isNotBlank()) {
            "$prefix +$remaining"
        } else {
            prefix
        }
    }

@Composable
private fun collapsedNodeSummary(node: IfwEditorNode): String = when (node) {
    is IfwEditorNode.Group -> stringResource(node.titleRes)
    is IfwEditorNode.Condition -> collapsedConditionSummary(node)
}

@Composable
private fun collapsedConditionSummary(condition: IfwEditorNode.Condition): String {
    val label = stringResource(condition.kind.labelRes)
    val detail = when (condition.kind) {
        com.merxury.core.ifw.editor.IfwEditorConditionKind.CALLER_TYPE -> {
            stringResource(condition.senderType.labelRes)
        }
        com.merxury.core.ifw.editor.IfwEditorConditionKind.PORT -> when (condition.portMode) {
            IfwEditorPortMode.EXACT -> condition.exactPort?.toString().orEmpty()
            IfwEditorPortMode.RANGE -> listOfNotNull(
                condition.minPort?.toString(),
                condition.maxPort?.toString(),
            ).joinToString("..")
        }
        else -> condition.value.trim()
    }
    return if (detail.isBlank()) label else "$label: $detail"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupModeSelector(
    selected: IfwEditorGroupMode,
    onSelect: (IfwEditorGroupMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.core_ui_ifw_group_logic),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GroupModeOption(
                selected = selected == IfwEditorGroupMode.ALL,
                label = stringResource(R.string.core_ui_ifw_group_all),
                onClick = { onSelect(IfwEditorGroupMode.ALL) },
            )
            GroupModeOption(
                selected = selected == IfwEditorGroupMode.ANY,
                label = stringResource(R.string.core_ui_ifw_group_any),
                onClick = { onSelect(IfwEditorGroupMode.ANY) },
            )
        }
    }
}

@Composable
internal fun GroupModeOption(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.selectable(
            selected = selected,
            onClick = onClick,
            role = Role.RadioButton,
        )
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(16.dp))
        BlockerSwitch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}
