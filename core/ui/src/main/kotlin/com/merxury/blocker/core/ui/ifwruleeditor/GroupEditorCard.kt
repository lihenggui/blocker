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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerOutlinedButton
import com.merxury.blocker.core.designsystem.component.BlockerOutlinedCard
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.R
import com.merxury.core.ifw.editor.IfwEditorGroupMode
import com.merxury.core.ifw.editor.IfwEditorNode

@Composable
internal fun GroupEditorCard(
    group: IfwEditorNode.Group,
    depth: Int,
    isRoot: Boolean,
    validationMessage: String? = null,
    onUpdate: (IfwEditorNode) -> Unit,
    onDelete: (String) -> Unit,
    onAddGroup: (String) -> Unit,
    onAddCondition: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BlockerOutlinedCard(
        outerPadding = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (depth * 12).dp, top = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                if (!isRoot) {
                    IconButton(onClick = { onDelete(group.id) }) {
                        Icon(
                            imageVector = BlockerIcons.Close,
                            contentDescription = stringResource(R.string.core_ui_close),
                        )
                    }
                }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BlockerOutlinedButton(
                    onClick = { onAddCondition(group.id) },
                    text = { Text(stringResource(R.string.core_ui_ifw_add_condition)) },
                    modifier = Modifier.weight(1f),
                )
                BlockerOutlinedButton(
                    onClick = { onAddGroup(group.id) },
                    text = { Text(stringResource(R.string.core_ui_ifw_add_group)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
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
