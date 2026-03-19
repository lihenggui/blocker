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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerOutlinedButton
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
import com.merxury.core.ifw.model.IfwComponentType

@Composable
fun SimpleGlobalIfwRuleScreen(
    draft: SimpleGlobalIfwRuleDraft,
    isDirty: Boolean,
    selectedPackageLabel: String?,
    componentQuery: String,
    visibleComponents: List<SimpleRuleComponentUiState>,
    isComponentLoading: Boolean,
    componentLoadError: String?,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onPackageNameChange: (String) -> Unit,
    onComponentTypeChange: (IfwComponentType) -> Unit,
    onTargetModeChange: (SimpleTargetMode) -> Unit,
    onBlockChange: (Boolean) -> Unit,
    onLogChange: (Boolean) -> Unit,
    onActionChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onCallerPackageChange: (String) -> Unit,
    onComponentQueryChange: (String) -> Unit,
    onSelectSingleTarget: (String) -> Unit,
    onToggleMultiTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEditing = draft.editingRuleIndex != null
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showOptionalConditions by rememberSaveable {
        mutableStateOf(
            draft.action.isNotBlank() ||
                draft.category.isNotBlank() ||
                draft.callerPackage.isNotBlank(),
        )
    }

    val handleBack: () -> Unit = {
        if (isDirty) {
            showUnsavedDialog = true
        } else {
            onBack()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        RuleEditorTopBar(
            title = stringResource(
                if (isEditing) {
                    R.string.feature_globalifwrule_impl_edit_rule
                } else {
                    R.string.feature_globalifwrule_impl_add_rule
                },
            ),
            canSave = draft.canSave,
            onSave = onSave,
            onBack = handleBack,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle(title = stringResource(R.string.feature_globalifwrule_impl_target_section))
            ComponentTypeDropdown(
                selected = draft.componentType,
                onSelect = onComponentTypeChange,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.feature_globalifwrule_impl_target_mode),
                style = MaterialTheme.typography.titleSmall,
            )
            TargetModeRow(
                label = stringResource(R.string.feature_globalifwrule_impl_target_mode_single),
                selected = draft.targetMode == SimpleTargetMode.SINGLE,
                onClick = { onTargetModeChange(SimpleTargetMode.SINGLE) },
            )
            TargetModeRow(
                label = stringResource(R.string.feature_globalifwrule_impl_target_mode_multiple),
                selected = draft.targetMode == SimpleTargetMode.MULTIPLE,
                onClick = { onTargetModeChange(SimpleTargetMode.MULTIPLE) },
            )

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = draft.selectedPackageName,
                onValueChange = onPackageNameChange,
                label = { Text(stringResource(R.string.feature_globalifwrule_impl_target_app_package)) },
                singleLine = true,
                supportingText = {
                    Text(
                        text = if (selectedPackageLabel != null) {
                            stringResource(
                                R.string.feature_globalifwrule_impl_target_app_package_label,
                                selectedPackageLabel,
                            )
                        } else {
                            stringResource(R.string.feature_globalifwrule_impl_target_app_package_summary)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Text(
                text = stringResource(R.string.feature_globalifwrule_impl_target_component),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            if (draft.selectedPackageName.isBlank()) {
                Text(
                    text = stringResource(R.string.feature_globalifwrule_impl_target_component_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(
                    value = componentQuery,
                    onValueChange = onComponentQueryChange,
                    label = { Text(stringResource(R.string.feature_globalifwrule_impl_target_component_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                ComponentSelectionContent(
                    components = visibleComponents,
                    targetMode = draft.targetMode,
                    isLoading = isComponentLoading,
                    error = componentLoadError,
                    onSelectSingleTarget = onSelectSingleTarget,
                    onToggleMultiTarget = onToggleMultiTarget,
                )
                if (draft.targets.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.feature_globalifwrule_impl_target_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Text(
                text = stringResource(R.string.feature_globalifwrule_impl_behavior_section),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            SwitchRow(
                label = stringResource(R.string.feature_globalifwrule_impl_block),
                checked = draft.block,
                onCheckedChange = onBlockChange,
            )
            SwitchRow(
                label = stringResource(R.string.feature_globalifwrule_impl_log),
                checked = draft.log,
                onCheckedChange = onLogChange,
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Text(
                text = stringResource(R.string.feature_globalifwrule_impl_optional_conditions),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            if (!showOptionalConditions) {
                BlockerOutlinedButton(
                    text = { Text(stringResource(R.string.feature_globalifwrule_impl_optional_conditions_show)) },
                    onClick = { showOptionalConditions = true },
                )
            } else {
                OutlinedTextField(
                    value = draft.action,
                    onValueChange = onActionChange,
                    label = { Text(stringResource(R.string.feature_globalifwrule_impl_optional_action)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft.category,
                    onValueChange = onCategoryChange,
                    label = { Text(stringResource(R.string.feature_globalifwrule_impl_optional_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft.callerPackage,
                    onValueChange = onCallerPackageChange,
                    label = { Text(stringResource(R.string.feature_globalifwrule_impl_optional_caller_package)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
        }
    }

    if (showUnsavedDialog) {
        BlockerWarningAlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = stringResource(R.string.feature_globalifwrule_impl_unsaved_title),
            text = stringResource(R.string.feature_globalifwrule_impl_unsaved_message),
            onConfirmRequest = {
                showUnsavedDialog = false
                onBack()
            },
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun TargetModeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ComponentSelectionContent(
    components: List<SimpleRuleComponentUiState>,
    targetMode: SimpleTargetMode,
    isLoading: Boolean,
    error: String?,
    onSelectSingleTarget: (String) -> Unit,
    onToggleMultiTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when {
            isLoading -> {
                Text(
                    text = stringResource(R.string.feature_globalifwrule_impl_loading_components),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            error != null -> {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            components.isEmpty() -> {
                Text(
                    text = stringResource(R.string.feature_globalifwrule_impl_no_components),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                components.forEach { component ->
                    ComponentSelectionRow(
                        component = component,
                        targetMode = targetMode,
                        onSelectSingleTarget = onSelectSingleTarget,
                        onToggleMultiTarget = onToggleMultiTarget,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComponentSelectionRow(
    component: SimpleRuleComponentUiState,
    targetMode: SimpleTargetMode,
    onSelectSingleTarget: (String) -> Unit,
    onToggleMultiTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (component.selected) 2.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (targetMode == SimpleTargetMode.SINGLE) {
                        onSelectSingleTarget(component.flattenedName)
                    } else {
                        onToggleMultiTarget(component.flattenedName)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BlockerSwitch(
                checked = component.selected,
                onCheckedChange = {
                    if (targetMode == SimpleTargetMode.SINGLE) {
                        onSelectSingleTarget(component.flattenedName)
                    } else {
                        onToggleMultiTarget(component.flattenedName)
                    }
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = component.simpleName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (component.simpleName != component.componentName) {
                    Text(
                        text = component.componentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
