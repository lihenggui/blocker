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

package com.merxury.blocker.feature.globalifwrule.impl

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.ifwruleeditor.IfwRuleTreeEditor
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.model.IfwComponentType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleScreen(
    onSave: (AddRuleData) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialData: AddRuleData? = null,
) {
    val editorKey = initialData?.let { "${it.packageName}:${it.editingRuleIndex}" } ?: "new"
    var packageName by remember(editorKey) { mutableStateOf(initialData?.packageName ?: "") }
    var componentType by remember(editorKey) {
        mutableStateOf(initialData?.componentType ?: IfwComponentType.BROADCAST)
    }
    var block by remember(editorKey) { mutableStateOf(initialData?.block ?: true) }
    var log by remember(editorKey) { mutableStateOf(initialData?.log ?: true) }
    var rootGroup by remember(editorKey) { mutableStateOf(initialData?.rootGroup ?: IfwEditorNode.Group()) }
    val isEditing = initialData != null
    var isDirty by remember(editorKey) { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        if (isDirty) {
            showUnsavedDialog = true
        } else {
            onBack()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        BlockerTopAppBar(
            title = stringResource(
                if (isEditing) {
                    R.string.feature_globalifwrule_impl_edit_rule
                } else {
                    R.string.feature_globalifwrule_impl_add_rule
                },
            ),
            hasNavigationIcon = true,
            onNavigationClick = handleBack,
            actions = {
                IconButton(
                    onClick = {
                        if (packageName.isNotBlank()) {
                            onSave(
                                AddRuleData(
                                    packageName = packageName,
                                    componentType = componentType,
                                    block = block,
                                    log = log,
                                    rootGroup = rootGroup,
                                    editingRuleIndex = initialData?.editingRuleIndex,
                                ),
                            )
                        }
                    },
                ) {
                    Icon(
                        imageVector = BlockerIcons.Check,
                        contentDescription = stringResource(R.string.feature_globalifwrule_impl_save),
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = packageName,
                onValueChange = {
                    isDirty = true
                    packageName = it
                },
                label = { Text(stringResource(R.string.feature_globalifwrule_impl_target_package)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))
            ComponentTypeDropdown(
                selected = componentType,
                onSelect = {
                    isDirty = true
                    componentType = it
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
            SwitchRow(
                label = stringResource(R.string.feature_globalifwrule_impl_block),
                checked = block,
                onCheckedChange = {
                    isDirty = true
                    block = it
                },
            )
            SwitchRow(
                label = stringResource(R.string.feature_globalifwrule_impl_log),
                checked = log,
                onCheckedChange = {
                    isDirty = true
                    log = it
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Text(
                text = stringResource(R.string.feature_globalifwrule_impl_conditions),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            IfwRuleTreeEditor(
                rootGroup = rootGroup,
                onChange = {
                    isDirty = true
                    rootGroup = it
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.feature_globalifwrule_impl_unsaved_title)) },
            text = { Text(stringResource(R.string.feature_globalifwrule_impl_unsaved_message)) },
            confirmButton = {
                BlockerTextButton(onClick = {
                    showUnsavedDialog = false
                    onBack()
                }) {
                    Text(stringResource(R.string.feature_globalifwrule_impl_discard))
                }
            },
            dismissButton = {
                BlockerTextButton(onClick = { showUnsavedDialog = false }) {
                    Text(stringResource(R.string.feature_globalifwrule_impl_cancel))
                }
            },
        )
    }
}

data class AddRuleData(
    val packageName: String,
    val componentType: IfwComponentType,
    val block: Boolean,
    val log: Boolean,
    val rootGroup: IfwEditorNode.Group = IfwEditorNode.Group(),
    val editingRuleIndex: Int? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentTypeDropdown(
    selected: IfwComponentType,
    onSelect: (IfwComponentType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = stringResource(selected.labelRes),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            label = { Text(stringResource(R.string.feature_globalifwrule_impl_rule_type)) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            IfwComponentType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.labelRes)) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

internal val IfwComponentType.labelRes: Int
    @StringRes get() = when (this) {
        IfwComponentType.ACTIVITY -> R.string.feature_globalifwrule_impl_rule_type_activity
        IfwComponentType.BROADCAST -> R.string.feature_globalifwrule_impl_rule_type_broadcast
        IfwComponentType.SERVICE -> R.string.feature_globalifwrule_impl_rule_type_service
    }

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        BlockerSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
