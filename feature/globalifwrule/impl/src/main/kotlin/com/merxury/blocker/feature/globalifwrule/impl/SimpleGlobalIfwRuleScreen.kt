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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerOutlinedButton
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.SimpleGlobalIfwRuleDraft
import com.merxury.blocker.core.model.data.SimpleRuleComponentUiState
import com.merxury.blocker.core.model.data.SimpleTargetMode
import com.merxury.blocker.core.ui.previewparameter.GlobalIfwRulePreviewParameterData
import com.merxury.blocker.feature.globalifwrule.api.R
import com.merxury.blocker.feature.globalifwrule.impl.components.ComponentSelectionContent
import com.merxury.blocker.feature.globalifwrule.impl.components.ComponentTypeDropdown
import com.merxury.blocker.feature.globalifwrule.impl.components.RuleEditorTopBar
import com.merxury.blocker.feature.globalifwrule.impl.components.SwitchRow
import com.merxury.blocker.feature.globalifwrule.impl.components.TargetModeRow
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
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
        },
    ) { innerPadding ->
        SimpleGlobalIfwRuleContent(
            draft = draft,
            selectedPackageLabel = selectedPackageLabel,
            componentQuery = componentQuery,
            visibleComponents = visibleComponents,
            isComponentLoading = isComponentLoading,
            componentLoadError = componentLoadError,
            showOptionalConditions = showOptionalConditions,
            onPackageNameChange = onPackageNameChange,
            onComponentTypeChange = onComponentTypeChange,
            onTargetModeChange = onTargetModeChange,
            onBlockChange = onBlockChange,
            onLogChange = onLogChange,
            onActionChange = onActionChange,
            onCategoryChange = onCategoryChange,
            onCallerPackageChange = onCallerPackageChange,
            onComponentQueryChange = onComponentQueryChange,
            onSelectSingleTarget = onSelectSingleTarget,
            onToggleMultiTarget = onToggleMultiTarget,
            onShowOptionalConditions = { showOptionalConditions = true },
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        )
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
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun SimpleGlobalIfwRuleContent(
    draft: SimpleGlobalIfwRuleDraft,
    selectedPackageLabel: String?,
    componentQuery: String,
    visibleComponents: List<SimpleRuleComponentUiState>,
    isComponentLoading: Boolean,
    componentLoadError: String?,
    showOptionalConditions: Boolean,
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
    onShowOptionalConditions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(8.dp))
        TargetSection(
            draft = draft,
            selectedPackageLabel = selectedPackageLabel,
            componentQuery = componentQuery,
            visibleComponents = visibleComponents,
            isComponentLoading = isComponentLoading,
            componentLoadError = componentLoadError,
            onPackageNameChange = onPackageNameChange,
            onComponentTypeChange = onComponentTypeChange,
            onTargetModeChange = onTargetModeChange,
            onComponentQueryChange = onComponentQueryChange,
            onSelectSingleTarget = onSelectSingleTarget,
            onToggleMultiTarget = onToggleMultiTarget,
        )
        Spacer(modifier = Modifier.height(16.dp))
        BehaviorSection(
            block = draft.block,
            log = draft.log,
            onBlockChange = onBlockChange,
            onLogChange = onLogChange,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OptionalConditionsSection(
            draft = draft,
            showOptionalConditions = showOptionalConditions,
            onActionChange = onActionChange,
            onCategoryChange = onCategoryChange,
            onCallerPackageChange = onCallerPackageChange,
            onShowOptionalConditions = onShowOptionalConditions,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TargetSection(
    draft: SimpleGlobalIfwRuleDraft,
    selectedPackageLabel: String?,
    componentQuery: String,
    visibleComponents: List<SimpleRuleComponentUiState>,
    isComponentLoading: Boolean,
    componentLoadError: String?,
    onPackageNameChange: (String) -> Unit,
    onComponentTypeChange: (IfwComponentType) -> Unit,
    onTargetModeChange: (SimpleTargetMode) -> Unit,
    onComponentQueryChange: (String) -> Unit,
    onSelectSingleTarget: (String) -> Unit,
    onToggleMultiTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionLabel(text = stringResource(R.string.feature_globalifwrule_api_target_section))
        Spacer(modifier = Modifier.height(8.dp))
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
        ComponentTypeDropdown(
            selected = draft.componentType,
            onSelect = onComponentTypeChange,
        )
        Spacer(modifier = Modifier.height(16.dp))
        TargetModeSection(
            selectedMode = draft.targetMode,
            onTargetModeChange = onTargetModeChange,
        )
        Spacer(modifier = Modifier.height(16.dp))
        TargetComponentSection(
            selectedPackageName = draft.selectedPackageName,
            targetMode = draft.targetMode,
            targets = draft.targets,
            componentQuery = componentQuery,
            visibleComponents = visibleComponents,
            isComponentLoading = isComponentLoading,
            componentLoadError = componentLoadError,
            onComponentQueryChange = onComponentQueryChange,
            onSelectSingleTarget = onSelectSingleTarget,
            onToggleMultiTarget = onToggleMultiTarget,
        )
    }
}

@Composable
private fun TargetModeSection(
    selectedMode: SimpleTargetMode,
    onTargetModeChange: (SimpleTargetMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.feature_globalifwrule_api_target_mode),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TargetModeRow(
                label = stringResource(R.string.feature_globalifwrule_api_target_mode_single),
                selected = selectedMode == SimpleTargetMode.SINGLE,
                onClick = { onTargetModeChange(SimpleTargetMode.SINGLE) },
            )
            TargetModeRow(
                label = stringResource(R.string.feature_globalifwrule_api_target_mode_multiple),
                selected = selectedMode == SimpleTargetMode.MULTIPLE,
                onClick = { onTargetModeChange(SimpleTargetMode.MULTIPLE) },
            )
        }
    }
}

@Composable
private fun TargetComponentSection(
    selectedPackageName: String,
    targetMode: SimpleTargetMode,
    targets: List<String>,
    componentQuery: String,
    visibleComponents: List<SimpleRuleComponentUiState>,
    isComponentLoading: Boolean,
    componentLoadError: String?,
    onComponentQueryChange: (String) -> Unit,
    onSelectSingleTarget: (String) -> Unit,
    onToggleMultiTarget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.feature_globalifwrule_api_target_component),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (selectedPackageName.isBlank()) {
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
                        targetMode = targetMode,
                        isLoading = isComponentLoading,
                        error = componentLoadError,
                        onSelectSingleTarget = onSelectSingleTarget,
                        onToggleMultiTarget = onToggleMultiTarget,
                    )
                    if (targets.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.feature_globalifwrule_api_target_required),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BehaviorSection(
    block: Boolean,
    log: Boolean,
    onBlockChange: (Boolean) -> Unit,
    onLogChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionLabel(text = stringResource(R.string.feature_globalifwrule_api_behavior_section))
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SwitchRow(
                    label = stringResource(R.string.feature_globalifwrule_api_block),
                    checked = block,
                    onCheckedChange = onBlockChange,
                )
                SwitchRow(
                    label = stringResource(R.string.feature_globalifwrule_api_log),
                    checked = log,
                    onCheckedChange = onLogChange,
                )
            }
        }
    }
}

@Composable
private fun OptionalConditionsSection(
    draft: SimpleGlobalIfwRuleDraft,
    showOptionalConditions: Boolean,
    onActionChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onCallerPackageChange: (String) -> Unit,
    onShowOptionalConditions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionLabel(text = stringResource(R.string.feature_globalifwrule_api_optional_conditions))
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!showOptionalConditions) {
                    BlockerOutlinedButton(
                        text = { Text(stringResource(R.string.feature_globalifwrule_api_optional_conditions_show)) },
                        onClick = onShowOptionalConditions,
                        modifier = Modifier.fillMaxWidth(),
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
            }
        }
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
private fun SimpleGlobalIfwRuleScreenPreview(
    draft: SimpleGlobalIfwRuleDraft,
    selectedPackageLabel: String? = null,
    componentQuery: String = "",
    visibleComponents: List<SimpleRuleComponentUiState> = emptyList(),
    isComponentLoading: Boolean = false,
    componentLoadError: String? = null,
    isDirty: Boolean = false,
) {
    SimpleGlobalIfwRuleScreenPreviewContainer {
        SimpleGlobalIfwRuleScreen(
            draft = draft,
            isDirty = isDirty,
            selectedPackageLabel = selectedPackageLabel,
            componentQuery = componentQuery,
            visibleComponents = visibleComponents,
            isComponentLoading = isComponentLoading,
            componentLoadError = componentLoadError,
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

@Composable
@PreviewThemes
private fun SimpleGlobalIfwRuleScreenAddPreview() {
    SimpleGlobalIfwRuleScreenPreview(
        draft = SimpleGlobalIfwRuleDraft(),
    )
}

@Composable
@PreviewThemes
private fun SimpleGlobalIfwRuleScreenMultiTargetPreview() {
    SimpleGlobalIfwRuleScreenPreview(
        draft = SimpleGlobalIfwRuleDraft(
            selectedPackageName = GlobalIfwRulePreviewParameterData.PREVIEW_SIMPLE_RULE_PACKAGE_NAME,
            componentType = IfwComponentType.BROADCAST,
            targetMode = SimpleTargetMode.MULTIPLE,
            targets = listOf(
                GlobalIfwRulePreviewParameterData.PREVIEW_BOOT_RECEIVER_NAME,
                GlobalIfwRulePreviewParameterData.PREVIEW_ALARM_RECEIVER_NAME,
            ),
            action = "android.intent.action.BOOT_COMPLETED",
            category = "android.intent.category.DEFAULT",
            callerPackage = "android",
        ),
        isDirty = true,
        selectedPackageLabel = "Spotify",
        visibleComponents = GlobalIfwRulePreviewParameterData.simpleRuleComponents(
            selectedTargets = setOf(
                GlobalIfwRulePreviewParameterData.PREVIEW_BOOT_RECEIVER_NAME,
                GlobalIfwRulePreviewParameterData.PREVIEW_ALARM_RECEIVER_NAME,
            ),
        ),
    )
}

@Composable
@PreviewThemes
private fun SimpleGlobalIfwRuleScreenLoadingPreview() {
    SimpleGlobalIfwRuleScreenPreview(
        draft = SimpleGlobalIfwRuleDraft(
            selectedPackageName = GlobalIfwRulePreviewParameterData.PREVIEW_SIMPLE_RULE_PACKAGE_NAME,
            componentType = IfwComponentType.BROADCAST,
        ),
        selectedPackageLabel = "Spotify",
        isComponentLoading = true,
    )
}

@Composable
@PreviewThemes
private fun SimpleGlobalIfwRuleScreenLoadErrorPreview() {
    SimpleGlobalIfwRuleScreenPreview(
        draft = SimpleGlobalIfwRuleDraft(
            selectedPackageName = GlobalIfwRulePreviewParameterData.PREVIEW_SIMPLE_RULE_PACKAGE_NAME,
            componentType = IfwComponentType.BROADCAST,
        ),
        selectedPackageLabel = "Spotify",
        componentLoadError = "Failed to query receivers for this package.",
    )
}
