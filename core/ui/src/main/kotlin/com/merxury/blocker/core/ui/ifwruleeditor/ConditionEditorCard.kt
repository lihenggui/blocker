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

package com.merxury.blocker.core.ui.ifwruleeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.R
import com.merxury.core.ifw.editor.IfwEditorConditionKind
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.editor.IfwEditorPortMode
import com.merxury.core.ifw.editor.IfwEditorStringMatcherMode
import com.merxury.core.ifw.model.SenderType

@Composable
internal fun ConditionEditorCard(
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
internal fun <T> DropdownSelector(
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
