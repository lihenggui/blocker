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

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerOutlinedButton
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.SimpleGlobalIfwRuleDraft
import com.merxury.blocker.core.model.data.SimpleRuleComponentUiState
import com.merxury.blocker.core.model.data.SimpleTargetMode
import com.merxury.blocker.core.ui.previewparameter.SimpleGlobalIfwRuleScreenPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.SimpleGlobalIfwRuleScreenPreviewState
import com.merxury.blocker.feature.globalifwrule.api.R
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
                    R.string.feature_globalifwrule_api_edit_rule
                } else {
                    R.string.feature_globalifwrule_api_add_rule
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
            SectionTitle(title = stringResource(R.string.feature_globalifwrule_api_target_section))
            ComponentTypeDropdown(
                selected = draft.componentType,
                onSelect = onComponentTypeChange,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.feature_globalifwrule_api_target_mode),
                style = MaterialTheme.typography.titleSmall,
            )
            TargetModeRow(
                label = stringResource(R.string.feature_globalifwrule_api_target_mode_single),
                selected = draft.targetMode == SimpleTargetMode.SINGLE,
                onClick = { onTargetModeChange(SimpleTargetMode.SINGLE) },
            )
            TargetModeRow(
                label = stringResource(R.string.feature_globalifwrule_api_target_mode_multiple),
                selected = draft.targetMode == SimpleTargetMode.MULTIPLE,
                onClick = { onTargetModeChange(SimpleTargetMode.MULTIPLE) },
            )

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = draft.selectedPackageName,
                onValueChange = onPackageNameChange,
                label = { Text(stringResource(R.string.feature_globalifwrule_api_target_app_package)) },
                singleLine = true,
                supportingText = {
                    Text(
                        text = if (selectedPackageLabel != null) {
                            stringResource(
                                R.string.feature_globalifwrule_api_target_app_package_label,
                                selectedPackageLabel,
                            )
                        } else {
                            stringResource(R.string.feature_globalifwrule_api_target_app_package_summary)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Text(
                text = stringResource(R.string.feature_globalifwrule_api_target_component),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            if (draft.selectedPackageName.isBlank()) {
                Text(
                    text = stringResource(R.string.feature_globalifwrule_api_target_component_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(
                    value = componentQuery,
                    onValueChange = onComponentQueryChange,
                    label = { Text(stringResource(R.string.feature_globalifwrule_api_target_component_search)) },
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
                        text = stringResource(R.string.feature_globalifwrule_api_target_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Text(
                text = stringResource(R.string.feature_globalifwrule_api_behavior_section),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            SwitchRow(
                label = stringResource(R.string.feature_globalifwrule_api_block),
                checked = draft.block,
                onCheckedChange = onBlockChange,
            )
            SwitchRow(
                label = stringResource(R.string.feature_globalifwrule_api_log),
                checked = draft.log,
                onCheckedChange = onLogChange,
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Text(
                text = stringResource(R.string.feature_globalifwrule_api_optional_conditions),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            if (!showOptionalConditions) {
                BlockerOutlinedButton(
                    text = { Text(stringResource(R.string.feature_globalifwrule_api_optional_conditions_show)) },
                    onClick = { showOptionalConditions = true },
                )
            } else {
                OutlinedTextField(
                    value = draft.action,
                    onValueChange = onActionChange,
                    label = { Text(stringResource(R.string.feature_globalifwrule_api_optional_action)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft.category,
                    onValueChange = onCategoryChange,
                    label = { Text(stringResource(R.string.feature_globalifwrule_api_optional_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft.callerPackage,
                    onValueChange = onCallerPackageChange,
                    label = { Text(stringResource(R.string.feature_globalifwrule_api_optional_caller_package)) },
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
            title = stringResource(R.string.feature_globalifwrule_api_unsaved_title),
            text = stringResource(R.string.feature_globalifwrule_api_unsaved_message),
            onConfirmRequest = {
                showUnsavedDialog = false
                onBack()
            },
        )
    }
}

@Composable
private fun SimpleGlobalIfwRuleScreenPreviewContainer(
    content: @Composable () -> Unit,
) {
    BlockerTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
@PreviewThemes
private fun SimpleGlobalIfwRuleScreenPreview(
    @PreviewParameter(SimpleGlobalIfwRuleScreenPreviewParameterProvider::class)
    state: SimpleGlobalIfwRuleScreenPreviewState,
) {
    SimpleGlobalIfwRuleScreenPreviewContainer {
        SimpleGlobalIfwRuleScreen(
            draft = state.draft,
            isDirty = state.isDirty,
            selectedPackageLabel = state.selectedPackageLabel,
            componentQuery = state.componentQuery,
            visibleComponents = state.visibleComponents,
            isComponentLoading = state.isComponentLoading,
            componentLoadError = state.componentLoadError,
            onSave = {},
            onBack = {},
            onPackageNameChange = {},
            onComponentTypeChange = {},
            onTargetModeChange = {},
            onBlockChange = {},
            onLogChange = {},
            onActionChange = {},
            onCategoryChange = {},
            onCallerPackageChange = {},
            onComponentQueryChange = {},
            onSelectSingleTarget = {},
            onToggleMultiTarget = {},
        )
    }
}
