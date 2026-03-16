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

package com.merxury.blocker.feature.ifwrule.impl.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.merxury.blocker.feature.ifwrule.impl.R
import com.merxury.blocker.feature.ifwrule.impl.model.ComponentPatternType
import com.merxury.blocker.feature.ifwrule.impl.model.ConditionUiState
import com.merxury.blocker.feature.ifwrule.impl.model.MatchMode
import com.merxury.blocker.feature.ifwrule.impl.model.PermissionMode
import com.merxury.blocker.feature.ifwrule.impl.model.PortMode
import com.merxury.blocker.feature.ifwrule.impl.model.SourceOption

@Composable
fun ConditionCard(
    condition: ConditionUiState,
    onUpdate: (ConditionUiState) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = conditionTitle(condition),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onDelete(condition.id) }) {
                    Icon(
                        imageVector = BlockerIcons.Close,
                        contentDescription = null,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            when (condition) {
                is ConditionUiState.ActionFilter -> ActionFilterContent(condition, onUpdate)
                is ConditionUiState.SourceControl -> SourceControlContent(condition, onUpdate)
                is ConditionUiState.CallerApp -> CallerAppContent(condition, onUpdate)
                is ConditionUiState.CallerPermission -> CallerPermissionContent(condition, onUpdate)
                is ConditionUiState.CategoryFilter -> CategoryFilterContent(condition, onUpdate)
                is ConditionUiState.LinkFilter -> LinkFilterContent(condition, onUpdate)
                is ConditionUiState.DataFilter -> DataFilterContent(condition, onUpdate)
                is ConditionUiState.MimeTypeFilter -> MimeTypeFilterContent(condition, onUpdate)
                is ConditionUiState.PortFilter -> PortFilterContent(condition, onUpdate)
                is ConditionUiState.ComponentPattern -> ComponentPatternContent(condition, onUpdate)
            }
        }
    }
}

@Composable
private fun conditionTitle(condition: ConditionUiState): String = when (condition) {
    is ConditionUiState.ActionFilter -> stringResource(R.string.feature_ifwrule_impl_action_label)
    is ConditionUiState.SourceControl -> stringResource(R.string.feature_ifwrule_impl_source_label)
    is ConditionUiState.CallerApp -> stringResource(R.string.feature_ifwrule_impl_caller_app_label)
    is ConditionUiState.CallerPermission -> stringResource(R.string.feature_ifwrule_impl_caller_perm_label)
    is ConditionUiState.CategoryFilter -> stringResource(R.string.feature_ifwrule_impl_category_label)
    is ConditionUiState.LinkFilter -> stringResource(R.string.feature_ifwrule_impl_condition_link)
    is ConditionUiState.DataFilter -> stringResource(R.string.feature_ifwrule_impl_data_label)
    is ConditionUiState.MimeTypeFilter -> stringResource(R.string.feature_ifwrule_impl_mime_label)
    is ConditionUiState.PortFilter -> stringResource(R.string.feature_ifwrule_impl_condition_port)
    is ConditionUiState.ComponentPattern -> stringResource(R.string.feature_ifwrule_impl_condition_component_pattern)
}

@Composable
private fun ActionFilterContent(
    condition: ConditionUiState.ActionFilter,
    onUpdate: (ConditionUiState) -> Unit,
) {
    MatchModeSelector(
        selected = condition.matchMode,
        onSelect = { onUpdate(condition.copy(matchMode = it)) },
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = condition.value,
        onValueChange = { onUpdate(condition.copy(value = it)) },
        label = { Text(stringResource(R.string.feature_ifwrule_impl_action_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun SourceControlContent(
    condition: ConditionUiState.SourceControl,
    onUpdate: (ConditionUiState) -> Unit,
) {
    val options = listOf(
        SourceOption.ALLOW_SYSTEM_ONLY to R.string.feature_ifwrule_impl_source_allow_system,
        SourceOption.ALLOW_SIGNATURE_ONLY to R.string.feature_ifwrule_impl_source_allow_signature,
        SourceOption.ALLOW_SYSTEM_OR_SIGNATURE to R.string.feature_ifwrule_impl_source_allow_system_or_sig,
        SourceOption.BLOCK_SYSTEM to R.string.feature_ifwrule_impl_source_block_system,
    )
    options.forEach { (option, labelRes) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            RadioButton(
                selected = condition.option == option,
                onClick = { onUpdate(condition.copy(option = option)) },
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CallerAppContent(
    condition: ConditionUiState.CallerApp,
    onUpdate: (ConditionUiState) -> Unit,
) {
    var newPackage by remember { mutableStateOf("") }
    condition.packageNames.forEachIndexed { index, pkg ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pkg,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    onUpdate(
                        condition.copy(
                            packageNames = condition.packageNames.toMutableList().also {
                                it.removeAt(index)
                            },
                        ),
                    )
                },
            ) {
                Icon(
                    imageVector = BlockerIcons.Close,
                    contentDescription = null,
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = newPackage,
            onValueChange = { newPackage = it },
            label = { Text(stringResource(R.string.feature_ifwrule_impl_condition_caller_app)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = {
                if (newPackage.isNotBlank()) {
                    onUpdate(
                        condition.copy(
                            packageNames = condition.packageNames + newPackage.trim(),
                        ),
                    )
                    newPackage = ""
                }
            },
        ) {
            Icon(
                imageVector = BlockerIcons.CheckSmall,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun CallerPermissionContent(
    condition: ConditionUiState.CallerPermission,
    onUpdate: (ConditionUiState) -> Unit,
) {
    OutlinedTextField(
        value = condition.permission,
        onValueChange = { onUpdate(condition.copy(permission = it)) },
        label = { Text(stringResource(R.string.feature_ifwrule_impl_caller_perm_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RadioButton(
            selected = condition.mode == PermissionMode.REQUIRE,
            onClick = { onUpdate(condition.copy(mode = PermissionMode.REQUIRE)) },
        )
        Text(
            text = stringResource(R.string.feature_ifwrule_impl_perm_require),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RadioButton(
            selected = condition.mode == PermissionMode.BLOCK_WITH,
            onClick = { onUpdate(condition.copy(mode = PermissionMode.BLOCK_WITH)) },
        )
        Text(
            text = stringResource(R.string.feature_ifwrule_impl_perm_block),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CategoryFilterContent(
    condition: ConditionUiState.CategoryFilter,
    onUpdate: (ConditionUiState) -> Unit,
) {
    OutlinedTextField(
        value = condition.name,
        onValueChange = { onUpdate(condition.copy(name = it)) },
        label = { Text(stringResource(R.string.feature_ifwrule_impl_category_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun LinkFilterContent(
    condition: ConditionUiState.LinkFilter,
    onUpdate: (ConditionUiState) -> Unit,
) {
    OutlinedTextField(
        value = condition.scheme,
        onValueChange = { onUpdate(condition.copy(scheme = it)) },
        label = { Text(stringResource(R.string.feature_ifwrule_impl_link_scheme_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = condition.host,
        onValueChange = { onUpdate(condition.copy(host = it)) },
        label = { Text(stringResource(R.string.feature_ifwrule_impl_link_host_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(8.dp))
    MatchModeSelector(
        selected = condition.pathMatchMode,
        onSelect = { onUpdate(condition.copy(pathMatchMode = it)) },
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = condition.path,
        onValueChange = { onUpdate(condition.copy(path = it)) },
        label = { Text(stringResource(R.string.feature_ifwrule_impl_link_path_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun DataFilterContent(
    condition: ConditionUiState.DataFilter,
    onUpdate: (ConditionUiState) -> Unit,
) {
    MatchModeSelector(
        selected = condition.matchMode,
        onSelect = { onUpdate(condition.copy(matchMode = it)) },
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = condition.value,
        onValueChange = { onUpdate(condition.copy(value = it)) },
        label = { Text(stringResource(R.string.feature_ifwrule_impl_data_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun MimeTypeFilterContent(
    condition: ConditionUiState.MimeTypeFilter,
    onUpdate: (ConditionUiState) -> Unit,
) {
    MatchModeSelector(
        selected = condition.matchMode,
        onSelect = { onUpdate(condition.copy(matchMode = it)) },
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = condition.value,
        onValueChange = { onUpdate(condition.copy(value = it)) },
        label = { Text(stringResource(R.string.feature_ifwrule_impl_mime_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun PortFilterContent(
    condition: ConditionUiState.PortFilter,
    onUpdate: (ConditionUiState) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = condition.portMode == PortMode.EXACT,
            onClick = { onUpdate(condition.copy(portMode = PortMode.EXACT)) },
        )
        Text(
            text = stringResource(R.string.feature_ifwrule_impl_port_exact),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.width(16.dp))
        RadioButton(
            selected = condition.portMode == PortMode.RANGE,
            onClick = { onUpdate(condition.copy(portMode = PortMode.RANGE)) },
        )
        Text(
            text = stringResource(R.string.feature_ifwrule_impl_port_range),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (condition.portMode == PortMode.EXACT) {
        OutlinedTextField(
            value = condition.equals?.toString() ?: "",
            onValueChange = { onUpdate(condition.copy(equals = it.toIntOrNull())) },
            label = { Text(stringResource(R.string.feature_ifwrule_impl_port_exact_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    } else {
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = condition.min?.toString() ?: "",
                onValueChange = { onUpdate(condition.copy(min = it.toIntOrNull())) },
                label = { Text(stringResource(R.string.feature_ifwrule_impl_port_min_label)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = condition.max?.toString() ?: "",
                onValueChange = { onUpdate(condition.copy(max = it.toIntOrNull())) },
                label = { Text(stringResource(R.string.feature_ifwrule_impl_port_max_label)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}

@Composable
private fun ComponentPatternContent(
    condition: ConditionUiState.ComponentPattern,
    onUpdate: (ConditionUiState) -> Unit,
) {
    val patternTypes = listOf(
        ComponentPatternType.COMPONENT to R.string.feature_ifwrule_impl_pattern_component,
        ComponentPatternType.NAME to R.string.feature_ifwrule_impl_pattern_name,
        ComponentPatternType.PACKAGE to R.string.feature_ifwrule_impl_pattern_package,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        patternTypes.forEach { (type, labelRes) ->
            RadioButton(
                selected = condition.patternType == type,
                onClick = { onUpdate(condition.copy(patternType = type)) },
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    MatchModeSelector(
        selected = condition.matchMode,
        onSelect = { onUpdate(condition.copy(matchMode = it)) },
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = condition.value,
        onValueChange = { onUpdate(condition.copy(value = it)) },
        label = { Text(stringResource(R.string.feature_ifwrule_impl_condition_component_pattern)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun MatchModeSelector(
    selected: MatchMode,
    onSelect: (MatchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = listOf(
        MatchMode.EXACT to R.string.feature_ifwrule_impl_match_exact,
        MatchMode.STARTS_WITH to R.string.feature_ifwrule_impl_match_starts_with,
        MatchMode.CONTAINS to R.string.feature_ifwrule_impl_match_contains,
        MatchMode.PATTERN to R.string.feature_ifwrule_impl_match_pattern,
        MatchMode.REGEX to R.string.feature_ifwrule_impl_match_regex,
        MatchMode.IS_NULL to R.string.feature_ifwrule_impl_match_is_null,
    )
    Column(modifier = modifier) {
        modes.chunked(3).forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { (mode, labelRes) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
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
