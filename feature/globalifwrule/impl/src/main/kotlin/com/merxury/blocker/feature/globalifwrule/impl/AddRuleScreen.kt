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

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerButton
import com.merxury.blocker.core.designsystem.component.BlockerOutlinedButton
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.core.ifw.model.IfwComponentType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleScreen(
    onSave: (AddRuleData) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var packageName by remember { mutableStateOf("") }
    var componentType by remember { mutableStateOf(IfwComponentType.BROADCAST) }
    var block by remember { mutableStateOf(true) }
    var log by remember { mutableStateOf(true) }
    val conditions = remember { mutableStateListOf<SimpleCondition>() }

    Column(modifier = modifier.fillMaxSize()) {
        BlockerTopAppBar(
            title = stringResource(R.string.feature_globalifwrule_impl_add_rule),
            hasNavigationIcon = true,
            onNavigationClick = onBack,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Target package name
            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it },
                label = { Text(stringResource(R.string.feature_globalifwrule_impl_target_package)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Component type dropdown
            ComponentTypeDropdown(
                selected = componentType,
                onSelect = { componentType = it },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Block and Log toggles
            SwitchRow(
                label = stringResource(R.string.feature_globalifwrule_impl_block),
                checked = block,
                onCheckedChange = { block = it },
            )
            SwitchRow(
                label = stringResource(R.string.feature_globalifwrule_impl_log),
                checked = log,
                onCheckedChange = { log = it },
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()

            // Conditions section
            Text(
                text = stringResource(R.string.feature_globalifwrule_impl_conditions),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )

            conditions.forEachIndexed { index, condition ->
                ConditionInput(
                    condition = condition,
                    onUpdate = { conditions[index] = it },
                    onRemove = { conditions.removeAt(index) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            BlockerOutlinedButton(
                onClick = {
                    conditions.add(
                        SimpleCondition(
                            filterType = SimpleFilterType.COMPONENT_FILTER,
                        ),
                    )
                },
                text = { Text(stringResource(R.string.feature_globalifwrule_impl_add_condition)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Save button
            Spacer(modifier = Modifier.height(24.dp))
            BlockerButton(
                onClick = {
                    if (packageName.isNotBlank()) {
                        onSave(
                            AddRuleData(
                                packageName = packageName,
                                componentType = componentType,
                                block = block,
                                log = log,
                                conditions = conditions.toList(),
                            ),
                        )
                    }
                },
                text = { Text(stringResource(R.string.feature_globalifwrule_impl_save)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
        }
    }
}

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
            value = selected.xmlTag.replaceFirstChar { it.uppercase() },
            onValueChange = { },
            readOnly = true,
            label = { Text(stringResource(R.string.feature_globalifwrule_impl_rule_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            IfwComponentType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.xmlTag.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionInput(
    condition: SimpleCondition,
    onUpdate: (SimpleCondition) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filterTypeExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExposedDropdownMenuBox(
                expanded = filterTypeExpanded,
                onExpandedChange = { filterTypeExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = condition.filterType.displayName,
                    onValueChange = { },
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterTypeExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                ExposedDropdownMenu(
                    expanded = filterTypeExpanded,
                    onDismissRequest = { filterTypeExpanded = false },
                ) {
                    SimpleFilterType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                onUpdate(condition.copy(filterType = type))
                                filterTypeExpanded = false
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                )
            }
        }
        OutlinedTextField(
            value = condition.value,
            onValueChange = { onUpdate(condition.copy(value = it)) },
            label = { Text(condition.filterType.valuePlaceholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
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
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        BlockerSwitch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

data class AddRuleData(
    val packageName: String,
    val componentType: IfwComponentType,
    val block: Boolean,
    val log: Boolean,
    val conditions: List<SimpleCondition>,
)

data class SimpleCondition(
    val filterType: SimpleFilterType = SimpleFilterType.COMPONENT_FILTER,
    val value: String = "",
)

enum class SimpleFilterType(
    val displayName: String,
    val valuePlaceholder: String,
) {
    COMPONENT_FILTER("component-filter", "com.example/com.example.Receiver"),
    ACTION("action", "android.intent.action.BOOT_COMPLETED"),
    CATEGORY("category", "android.intent.category.DEFAULT"),
    SENDER_PACKAGE("sender-package", "com.example.caller"),
    COMPONENT("component", "com.example/com.example.Activity"),
    COMPONENT_NAME("component-name", "com.example.Activity"),
    COMPONENT_PACKAGE("component-package", "com.example"),
    HOST("host", "example.com"),
    SCHEME("scheme", "https"),
    PATH("path", "/api/endpoint"),
    DATA("data", "content://com.example/data"),
    MIME_TYPE("mime-type", "text/plain"),
}
