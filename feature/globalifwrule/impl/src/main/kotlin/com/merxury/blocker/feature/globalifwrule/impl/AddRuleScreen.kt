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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerButton
import com.merxury.blocker.core.designsystem.component.BlockerOutlinedButton
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.SenderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleScreen(
    onSave: (AddRuleData) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialData: AddRuleData? = null,
) {
    var packageName by remember { mutableStateOf(initialData?.packageName ?: "") }
    var componentType by remember { mutableStateOf(initialData?.componentType ?: IfwComponentType.BROADCAST) }
    var block by remember { mutableStateOf(initialData?.block ?: true) }
    var log by remember { mutableStateOf(initialData?.log ?: true) }
    var combineMode by remember { mutableStateOf(initialData?.combineMode ?: SimpleCombineMode.ALL_MATCH) }
    val conditions = remember {
        mutableStateListOf<SimpleCondition>().also {
            if (initialData != null) it.addAll(initialData.conditions)
        }
    }
    val isEditing = initialData != null

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
            CombineModeSelector(
                selected = combineMode,
                onSelect = { combineMode = it },
            )
            Spacer(modifier = Modifier.height(8.dp))

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
                                combineMode = combineMode,
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

@Composable
private fun CombineModeSelector(
    selected: SimpleCombineMode,
    onSelect: (SimpleCombineMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feature_globalifwrule_impl_combine_label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected == SimpleCombineMode.ALL_MATCH,
                onClick = { onSelect(SimpleCombineMode.ALL_MATCH) },
            )
            Text(
                text = stringResource(R.string.feature_globalifwrule_impl_combine_all),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected == SimpleCombineMode.ANY_MATCH,
                onClick = { onSelect(SimpleCombineMode.ANY_MATCH) },
            )
            Text(
                text = stringResource(R.string.feature_globalifwrule_impl_combine_any),
                style = MaterialTheme.typography.bodyMedium,
            )
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
        when {
            condition.filterType.supportsStringMatcher -> {
                GlobalMatchModeSelector(
                    selected = condition.matchMode,
                    onSelect = { onUpdate(condition.copy(matchMode = it)) },
                )
                if (!condition.matchMode.isNullMode) {
                    OutlinedTextField(
                        value = condition.value,
                        onValueChange = { onUpdate(condition.copy(value = it)) },
                        label = { Text(condition.filterType.valuePlaceholder) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            condition.filterType == SimpleFilterType.SENDER -> {
                SenderTypeDropdown(
                    selected = condition.senderType,
                    onSelect = { onUpdate(condition.copy(senderType = it)) },
                )
            }

            condition.filterType == SimpleFilterType.PORT -> {
                PortModeSelector(
                    selected = condition.portMode,
                    onSelect = { onUpdate(condition.copy(portMode = it)) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (condition.portMode == SimplePortMode.EXACT) {
                    OutlinedTextField(
                        value = condition.equals?.toString() ?: "",
                        onValueChange = { onUpdate(condition.copy(equals = it.toIntOrNull(), min = null, max = null)) },
                        label = { Text(stringResource(R.string.feature_globalifwrule_impl_port_exact_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = condition.min?.toString() ?: "",
                            onValueChange = { onUpdate(condition.copy(min = it.toIntOrNull(), equals = null)) },
                            label = { Text(stringResource(R.string.feature_globalifwrule_impl_port_min_label)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = condition.max?.toString() ?: "",
                            onValueChange = { onUpdate(condition.copy(max = it.toIntOrNull(), equals = null)) },
                            label = { Text(stringResource(R.string.feature_globalifwrule_impl_port_max_label)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }

            else -> {
                OutlinedTextField(
                    value = condition.value,
                    onValueChange = { onUpdate(condition.copy(value = it)) },
                    label = { Text(condition.filterType.valuePlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (condition.filterType.canBeNegated) {
            SwitchRow(
                label = stringResource(R.string.feature_globalifwrule_impl_negate),
                checked = condition.negated,
                onCheckedChange = { onUpdate(condition.copy(negated = it)) },
            )
        }
    }
}

@Composable
private fun GlobalMatchModeSelector(
    selected: SimpleMatchMode,
    onSelect: (SimpleMatchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = listOf(
        SimpleMatchMode.EXACT to R.string.feature_globalifwrule_impl_match_exact,
        SimpleMatchMode.STARTS_WITH to R.string.feature_globalifwrule_impl_match_starts_with,
        SimpleMatchMode.CONTAINS to R.string.feature_globalifwrule_impl_match_contains,
        SimpleMatchMode.PATTERN to R.string.feature_globalifwrule_impl_match_pattern,
        SimpleMatchMode.REGEX to R.string.feature_globalifwrule_impl_match_regex,
        SimpleMatchMode.IS_NULL to R.string.feature_globalifwrule_impl_match_is_null,
        SimpleMatchMode.IS_NOT_NULL to R.string.feature_globalifwrule_impl_match_is_not_null,
    )
    Column(modifier = modifier) {
        modes.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { (mode, labelRes) ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == mode,
                            onClick = { onSelect(mode) },
                        )
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SenderTypeDropdown(
    selected: SenderType,
    onSelect: (SenderType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            label = { Text(stringResource(R.string.feature_globalifwrule_impl_sender_type)) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SenderType.entries.forEach { senderType ->
                DropdownMenuItem(
                    text = { Text(senderType.displayName) },
                    onClick = {
                        onSelect(senderType)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PortModeSelector(
    selected: SimplePortMode,
    onSelect: (SimplePortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected == SimplePortMode.EXACT,
            onClick = { onSelect(SimplePortMode.EXACT) },
        )
        Text(
            text = stringResource(R.string.feature_globalifwrule_impl_port_exact),
            style = MaterialTheme.typography.bodyMedium,
        )
        RadioButton(
            selected = selected == SimplePortMode.RANGE,
            onClick = { onSelect(SimplePortMode.RANGE) },
        )
        Text(
            text = stringResource(R.string.feature_globalifwrule_impl_port_range),
            style = MaterialTheme.typography.bodyMedium,
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
    val combineMode: SimpleCombineMode = SimpleCombineMode.ALL_MATCH,
    val conditions: List<SimpleCondition>,
    val editingRuleIndex: Int? = null,
)

data class SimpleCondition(
    val filterType: SimpleFilterType = SimpleFilterType.COMPONENT_FILTER,
    val value: String = "",
    val matchMode: SimpleMatchMode = SimpleMatchMode.EXACT,
    val senderType: SenderType = SenderType.SYSTEM,
    val portMode: SimplePortMode = SimplePortMode.EXACT,
    val equals: Int? = null,
    val min: Int? = null,
    val max: Int? = null,
    val negated: Boolean = false,
)

enum class SimpleFilterType(
    val displayName: String,
    val valuePlaceholder: String,
    val supportsStringMatcher: Boolean = false,
    val canBeNegated: Boolean = true,
) {
    COMPONENT_FILTER("component-filter", "com.example/com.example.Receiver"),
    ACTION("action", "android.intent.action.BOOT_COMPLETED", supportsStringMatcher = true),
    CATEGORY("category", "android.intent.category.DEFAULT"),
    SENDER("sender", "", canBeNegated = true),
    SENDER_PACKAGE("sender-package", "com.example.caller"),
    SENDER_PERMISSION("sender-permission", "android.permission.INTERNET"),
    COMPONENT("component", "com.example/com.example.Activity", supportsStringMatcher = true),
    COMPONENT_NAME("component-name", "com.example.Activity", supportsStringMatcher = true),
    COMPONENT_PACKAGE("component-package", "com.example", supportsStringMatcher = true),
    HOST("host", "example.com", supportsStringMatcher = true),
    SCHEME("scheme", "https", supportsStringMatcher = true),
    SCHEME_SPECIFIC_PART("scheme-specific-part", "//example.com/path", supportsStringMatcher = true),
    PATH("path", "/api/endpoint", supportsStringMatcher = true),
    DATA("data", "content://com.example/data", supportsStringMatcher = true),
    MIME_TYPE("mime-type", "text/plain", supportsStringMatcher = true),
    PORT("port", "", canBeNegated = true),
}

enum class SimpleMatchMode {
    EXACT,
    STARTS_WITH,
    CONTAINS,
    PATTERN,
    REGEX,
    IS_NULL,
    IS_NOT_NULL,
    ;

    val isNullMode: Boolean
        get() = this == IS_NULL || this == IS_NOT_NULL
}

enum class SimplePortMode {
    EXACT,
    RANGE,
}

enum class SimpleCombineMode {
    ALL_MATCH,
    ANY_MATCH,
}

private val SenderType.displayName: String
    get() = when (this) {
        SenderType.SIGNATURE -> "signature"
        SenderType.SYSTEM -> "system"
        SenderType.SYSTEM_OR_SIGNATURE -> "system|signature"
        SenderType.USER_ID -> "userId"
    }
