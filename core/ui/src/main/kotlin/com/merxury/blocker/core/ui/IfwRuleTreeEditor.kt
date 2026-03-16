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

package com.merxury.blocker.core.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerOutlinedButton
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.core.ifw.editor.IfwEditorConditionKind
import com.merxury.core.ifw.editor.IfwEditorGroupMode
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.editor.IfwEditorPortMode
import com.merxury.core.ifw.editor.IfwEditorStringMatcherMode
import com.merxury.core.ifw.editor.addNode
import com.merxury.core.ifw.editor.newIfwEditorId
import com.merxury.core.ifw.editor.removeNode
import com.merxury.core.ifw.editor.updateNode
import com.merxury.core.ifw.model.SenderType

@Composable
fun IfwRuleTreeEditor(
    rootGroup: IfwEditorNode.Group,
    onChange: (IfwEditorNode.Group) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerTargetGroupId by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        GroupEditorCard(
            group = rootGroup,
            depth = 0,
            isRoot = true,
            onUpdate = { updated -> onChange(rootGroup.updateNode(updated)) },
            onDelete = { nodeId -> onChange(rootGroup.removeNode(nodeId)) },
            onAddGroup = { groupId ->
                onChange(
                    rootGroup.addNode(
                        targetGroupId = groupId,
                        node = IfwEditorNode.Group(id = newIfwEditorId()),
                    ),
                )
            },
            onAddCondition = { groupId -> pickerTargetGroupId = groupId },
        )
    }

    if (pickerTargetGroupId != null) {
        ConditionPickerSheet(
            onDismiss = { pickerTargetGroupId = null },
            onSelect = { kind ->
                val targetId = pickerTargetGroupId ?: return@ConditionPickerSheet
                onChange(
                    rootGroup.addNode(
                        targetGroupId = targetId,
                        node = IfwEditorNode.Condition(
                            id = newIfwEditorId(),
                            kind = kind,
                        ),
                    ),
                )
                pickerTargetGroupId = null
            },
        )
    }
}

@Composable
private fun GroupEditorCard(
    group: IfwEditorNode.Group,
    depth: Int,
    isRoot: Boolean,
    onUpdate: (IfwEditorNode) -> Unit,
    onDelete: (String) -> Unit,
    onAddGroup: (String) -> Unit,
    onAddCondition: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
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

@Composable
private fun GroupModeSelector(
    selected: IfwEditorGroupMode,
    onSelect: (IfwEditorGroupMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.core_ui_ifw_group_logic),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
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

@Composable
private fun GroupModeOption(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        BlockerSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ConditionEditorCard(
    condition: IfwEditorNode.Condition,
    depth: Int,
    onUpdate: (IfwEditorNode) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (depth * 12).dp, top = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                ConditionKindDropdown(
                    selected = condition.kind,
                    onSelect = { kind -> onUpdate(condition.copy(kind = kind)) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onDelete(condition.id) }) {
                    Icon(
                        imageVector = BlockerIcons.Close,
                        contentDescription = stringResource(R.string.core_ui_close),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SwitchRow(
                label = stringResource(R.string.core_ui_ifw_condition_exclude),
                checked = condition.excluded,
                onCheckedChange = { excluded -> onUpdate(condition.copy(excluded = excluded)) },
            )
            Spacer(modifier = Modifier.height(8.dp))

            when (condition.kind) {
                IfwEditorConditionKind.CALLER_TYPE -> {
                    SenderTypeDropdown(
                        selected = condition.senderType,
                        onSelect = { senderType -> onUpdate(condition.copy(senderType = senderType)) },
                    )
                }

                IfwEditorConditionKind.PORT -> {
                    PortModeSelector(
                        selected = condition.portMode,
                        onSelect = { portMode ->
                            onUpdate(
                                condition.copy(
                                    portMode = portMode,
                                    exactPort = if (portMode == IfwEditorPortMode.EXACT) condition.exactPort else null,
                                    minPort = if (portMode == IfwEditorPortMode.RANGE) condition.minPort else null,
                                    maxPort = if (portMode == IfwEditorPortMode.RANGE) condition.maxPort else null,
                                ),
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (condition.portMode == IfwEditorPortMode.EXACT) {
                        OutlinedTextField(
                            value = condition.exactPort?.toString() ?: "",
                            onValueChange = { text ->
                                onUpdate(condition.copy(exactPort = text.toIntOrNull()))
                            },
                            label = { Text(stringResource(R.string.core_ui_ifw_port_exact_value)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = condition.minPort?.toString() ?: "",
                                onValueChange = { text ->
                                    onUpdate(condition.copy(minPort = text.toIntOrNull()))
                                },
                                label = { Text(stringResource(R.string.core_ui_ifw_port_min_value)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = condition.maxPort?.toString() ?: "",
                                onValueChange = { text ->
                                    onUpdate(condition.copy(maxPort = text.toIntOrNull()))
                                },
                                label = { Text(stringResource(R.string.core_ui_ifw_port_max_value)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                    }
                }

                else -> {
                    if (condition.kind.supportsStringMatcher) {
                        MatcherModeDropdown(
                            selected = condition.matcherMode,
                            onSelect = { matcherMode ->
                                onUpdate(condition.copy(matcherMode = matcherMode))
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (!condition.matcherMode.isNullMode || !condition.kind.supportsStringMatcher) {
                        OutlinedTextField(
                            value = condition.value,
                            onValueChange = { value -> onUpdate(condition.copy(value = value)) },
                            label = { Text(stringResource(condition.kind.valueHintRes)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionKindDropdown(
    selected: IfwEditorConditionKind,
    onSelect: (IfwEditorConditionKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownSelector(
        modifier = modifier,
        label = stringResource(R.string.core_ui_ifw_condition_type),
        value = stringResource(selected.labelRes),
        options = IfwEditorConditionKind.entries,
        optionLabel = { kind -> stringResource(kind.labelRes) },
        onSelect = onSelect,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatcherModeDropdown(
    selected: IfwEditorStringMatcherMode,
    onSelect: (IfwEditorStringMatcherMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownSelector(
        modifier = modifier,
        label = stringResource(R.string.core_ui_ifw_match_type),
        value = stringResource(selected.labelRes),
        options = IfwEditorStringMatcherMode.entries,
        optionLabel = { mode -> stringResource(mode.labelRes) },
        onSelect = onSelect,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SenderTypeDropdown(
    selected: SenderType,
    onSelect: (SenderType) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownSelector(
        modifier = modifier,
        label = stringResource(R.string.core_ui_ifw_sender_type),
        value = stringResource(selected.labelRes),
        options = SenderType.entries,
        optionLabel = { senderType -> stringResource(senderType.labelRes) },
        onSelect = onSelect,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownSelector(
    value: String,
    label: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            label = { Text(label) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PortModeSelector(
    selected: IfwEditorPortMode,
    onSelect: (IfwEditorPortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupModeOption(
            selected = selected == IfwEditorPortMode.EXACT,
            label = stringResource(R.string.core_ui_ifw_port_exact),
            onClick = { onSelect(IfwEditorPortMode.EXACT) },
        )
        GroupModeOption(
            selected = selected == IfwEditorPortMode.RANGE,
            label = stringResource(R.string.core_ui_ifw_port_range),
            onClick = { onSelect(IfwEditorPortMode.RANGE) },
        )
    }
}

private data class ConditionPickerSection(
    @StringRes val titleRes: Int,
    val kinds: List<IfwEditorConditionKind>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionPickerSheet(
    onDismiss: () -> Unit,
    onSelect: (IfwEditorConditionKind) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val sections = remember {
        listOf(
            ConditionPickerSection(
                titleRes = R.string.core_ui_ifw_picker_section_intent,
                kinds = listOf(
                    IfwEditorConditionKind.ACTION,
                    IfwEditorConditionKind.CATEGORY,
                ),
            ),
            ConditionPickerSection(
                titleRes = R.string.core_ui_ifw_picker_section_caller,
                kinds = listOf(
                    IfwEditorConditionKind.CALLER_TYPE,
                    IfwEditorConditionKind.CALLER_PACKAGE,
                    IfwEditorConditionKind.CALLER_PERMISSION,
                ),
            ),
            ConditionPickerSection(
                titleRes = R.string.core_ui_ifw_picker_section_link,
                kinds = listOf(
                    IfwEditorConditionKind.SCHEME,
                    IfwEditorConditionKind.HOST,
                    IfwEditorConditionKind.PATH,
                    IfwEditorConditionKind.SCHEME_SPECIFIC_PART,
                    IfwEditorConditionKind.DATA,
                    IfwEditorConditionKind.MIME_TYPE,
                    IfwEditorConditionKind.PORT,
                ),
            ),
            ConditionPickerSection(
                titleRes = R.string.core_ui_ifw_picker_section_component,
                kinds = listOf(
                    IfwEditorConditionKind.COMPONENT_FILTER,
                    IfwEditorConditionKind.COMPONENT,
                    IfwEditorConditionKind.COMPONENT_NAME,
                    IfwEditorConditionKind.COMPONENT_PACKAGE,
                ),
            ),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.core_ui_ifw_add_condition),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            sections.forEach { section ->
                Text(
                    text = stringResource(section.titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                section.kinds.forEach { kind ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(kind)
                                onDismiss()
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = BlockerIcons.Add,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(kind.labelRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(kind.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val IfwEditorNode.Group.titleRes: Int
    @StringRes get() = when {
        excluded -> R.string.core_ui_ifw_group_excluded_title
        mode == IfwEditorGroupMode.ALL -> R.string.core_ui_ifw_group_all_title
        else -> R.string.core_ui_ifw_group_any_title
    }

private val IfwEditorNode.Group.summaryRes: Int
    @StringRes get() = when {
        excluded -> R.string.core_ui_ifw_group_excluded_summary
        mode == IfwEditorGroupMode.ALL -> R.string.core_ui_ifw_group_all_summary
        else -> R.string.core_ui_ifw_group_any_summary
    }

private val IfwEditorConditionKind.labelRes: Int
    @StringRes get() = when (this) {
        IfwEditorConditionKind.ACTION -> R.string.core_ui_ifw_condition_action
        IfwEditorConditionKind.CATEGORY -> R.string.core_ui_ifw_condition_category
        IfwEditorConditionKind.CALLER_TYPE -> R.string.core_ui_ifw_condition_caller_type
        IfwEditorConditionKind.CALLER_PACKAGE -> R.string.core_ui_ifw_condition_caller_package
        IfwEditorConditionKind.CALLER_PERMISSION -> R.string.core_ui_ifw_condition_caller_permission
        IfwEditorConditionKind.COMPONENT -> R.string.core_ui_ifw_condition_component
        IfwEditorConditionKind.COMPONENT_NAME -> R.string.core_ui_ifw_condition_component_name
        IfwEditorConditionKind.COMPONENT_PACKAGE -> R.string.core_ui_ifw_condition_component_package
        IfwEditorConditionKind.COMPONENT_FILTER -> R.string.core_ui_ifw_condition_component_filter
        IfwEditorConditionKind.HOST -> R.string.core_ui_ifw_condition_host
        IfwEditorConditionKind.SCHEME -> R.string.core_ui_ifw_condition_scheme
        IfwEditorConditionKind.SCHEME_SPECIFIC_PART -> R.string.core_ui_ifw_condition_scheme_specific_part
        IfwEditorConditionKind.PATH -> R.string.core_ui_ifw_condition_path
        IfwEditorConditionKind.DATA -> R.string.core_ui_ifw_condition_data
        IfwEditorConditionKind.MIME_TYPE -> R.string.core_ui_ifw_condition_mime_type
        IfwEditorConditionKind.PORT -> R.string.core_ui_ifw_condition_port
    }

private val IfwEditorConditionKind.descriptionRes: Int
    @StringRes get() = when (this) {
        IfwEditorConditionKind.ACTION -> R.string.core_ui_ifw_condition_action_desc
        IfwEditorConditionKind.CATEGORY -> R.string.core_ui_ifw_condition_category_desc
        IfwEditorConditionKind.CALLER_TYPE -> R.string.core_ui_ifw_condition_caller_type_desc
        IfwEditorConditionKind.CALLER_PACKAGE -> R.string.core_ui_ifw_condition_caller_package_desc
        IfwEditorConditionKind.CALLER_PERMISSION -> R.string.core_ui_ifw_condition_caller_permission_desc
        IfwEditorConditionKind.COMPONENT -> R.string.core_ui_ifw_condition_component_desc
        IfwEditorConditionKind.COMPONENT_NAME -> R.string.core_ui_ifw_condition_component_name_desc
        IfwEditorConditionKind.COMPONENT_PACKAGE -> R.string.core_ui_ifw_condition_component_package_desc
        IfwEditorConditionKind.COMPONENT_FILTER -> R.string.core_ui_ifw_condition_component_filter_desc
        IfwEditorConditionKind.HOST -> R.string.core_ui_ifw_condition_host_desc
        IfwEditorConditionKind.SCHEME -> R.string.core_ui_ifw_condition_scheme_desc
        IfwEditorConditionKind.SCHEME_SPECIFIC_PART -> R.string.core_ui_ifw_condition_scheme_specific_part_desc
        IfwEditorConditionKind.PATH -> R.string.core_ui_ifw_condition_path_desc
        IfwEditorConditionKind.DATA -> R.string.core_ui_ifw_condition_data_desc
        IfwEditorConditionKind.MIME_TYPE -> R.string.core_ui_ifw_condition_mime_type_desc
        IfwEditorConditionKind.PORT -> R.string.core_ui_ifw_condition_port_desc
    }

private val IfwEditorConditionKind.valueHintRes: Int
    @StringRes get() = when (this) {
        IfwEditorConditionKind.ACTION -> R.string.core_ui_ifw_value_hint_action
        IfwEditorConditionKind.CATEGORY -> R.string.core_ui_ifw_value_hint_category
        IfwEditorConditionKind.CALLER_TYPE -> R.string.core_ui_ifw_sender_type
        IfwEditorConditionKind.CALLER_PACKAGE -> R.string.core_ui_ifw_value_hint_package_name
        IfwEditorConditionKind.CALLER_PERMISSION -> R.string.core_ui_ifw_value_hint_permission
        IfwEditorConditionKind.COMPONENT -> R.string.core_ui_ifw_value_hint_component
        IfwEditorConditionKind.COMPONENT_NAME -> R.string.core_ui_ifw_value_hint_component_name
        IfwEditorConditionKind.COMPONENT_PACKAGE -> R.string.core_ui_ifw_value_hint_package_name
        IfwEditorConditionKind.COMPONENT_FILTER -> R.string.core_ui_ifw_value_hint_component_filter
        IfwEditorConditionKind.HOST -> R.string.core_ui_ifw_value_hint_host
        IfwEditorConditionKind.SCHEME -> R.string.core_ui_ifw_value_hint_scheme
        IfwEditorConditionKind.SCHEME_SPECIFIC_PART -> R.string.core_ui_ifw_value_hint_scheme_specific_part
        IfwEditorConditionKind.PATH -> R.string.core_ui_ifw_value_hint_path
        IfwEditorConditionKind.DATA -> R.string.core_ui_ifw_value_hint_data
        IfwEditorConditionKind.MIME_TYPE -> R.string.core_ui_ifw_value_hint_mime_type
        IfwEditorConditionKind.PORT -> R.string.core_ui_ifw_port_exact_value
    }

private val IfwEditorStringMatcherMode.labelRes: Int
    @StringRes get() = when (this) {
        IfwEditorStringMatcherMode.EXACT -> R.string.core_ui_ifw_match_exact
        IfwEditorStringMatcherMode.STARTS_WITH -> R.string.core_ui_ifw_match_starts_with
        IfwEditorStringMatcherMode.CONTAINS -> R.string.core_ui_ifw_match_contains
        IfwEditorStringMatcherMode.PATTERN -> R.string.core_ui_ifw_match_pattern
        IfwEditorStringMatcherMode.REGEX -> R.string.core_ui_ifw_match_regex
        IfwEditorStringMatcherMode.IS_NULL -> R.string.core_ui_ifw_match_is_empty
        IfwEditorStringMatcherMode.IS_NOT_NULL -> R.string.core_ui_ifw_match_is_not_empty
    }

private val SenderType.labelRes: Int
    @StringRes get() = when (this) {
        SenderType.SIGNATURE -> R.string.core_ui_ifw_sender_signature
        SenderType.SYSTEM -> R.string.core_ui_ifw_sender_system
        SenderType.SYSTEM_OR_SIGNATURE -> R.string.core_ui_ifw_sender_system_or_signature
        SenderType.USER_ID -> R.string.core_ui_ifw_sender_user_id
    }
