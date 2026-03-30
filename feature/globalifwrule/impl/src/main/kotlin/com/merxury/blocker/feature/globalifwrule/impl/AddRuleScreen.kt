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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.AdvancedGlobalIfwRuleDraft
import com.merxury.blocker.core.ui.ifwruleeditor.IfwRuleTreeEditor
import com.merxury.blocker.core.ui.previewparameter.GlobalIfwRulePreviewParameterData
import com.merxury.blocker.feature.globalifwrule.api.R
import com.merxury.blocker.feature.globalifwrule.impl.components.BehaviorSection
import com.merxury.blocker.feature.globalifwrule.impl.components.ComponentTypeDropdown
import com.merxury.blocker.feature.globalifwrule.impl.components.IntentFilterBanner
import com.merxury.blocker.feature.globalifwrule.impl.components.RuleEditorTopBar
import com.merxury.blocker.feature.globalifwrule.impl.components.SectionLabel
import com.merxury.core.ifw.editor.IfwEditorConditionKind
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.editor.hasTopLevelComponentFilter
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwIntentFilter

@Composable
fun AdvancedGlobalIfwRuleScreen(
    draft: AdvancedGlobalIfwRuleDraft,
    isDirty: Boolean,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onPackageNameChange: (String) -> Unit,
    onComponentTypeChange: (IfwComponentType) -> Unit,
    onBlockChange: (Boolean) -> Unit,
    onLogChange: (Boolean) -> Unit,
    onRootGroupChange: (IfwEditorNode.Group) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEditing = draft.editingRuleIndex != null
    val selectorRequiredMessage = if (!draft.hasReadOnlyIntentFilters && !draft.rootGroup.hasTopLevelComponentFilter()) {
        stringResource(R.string.feature_globalifwrule_api_selector_required)
    } else {
        null
    }
    var showUnsavedDialog by remember { mutableStateOf(false) }

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
                        R.string.feature_globalifwrule_api_edit_advanced_rule
                    } else {
                        R.string.feature_globalifwrule_api_add_advanced_rule
                    },
                ),
                canSave = draft.canSave,
                onSave = onSave,
                onBack = handleBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionLabel(text = stringResource(R.string.feature_globalifwrule_api_target_section))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.storagePackageName,
                onValueChange = onPackageNameChange,
                label = { Text(stringResource(R.string.feature_globalifwrule_api_target_package)) },
                supportingText = {
                    Text(stringResource(R.string.feature_globalifwrule_api_target_package_summary))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))
            ComponentTypeDropdown(
                selected = draft.componentType,
                onSelect = onComponentTypeChange,
            )

            Spacer(modifier = Modifier.height(16.dp))
            BehaviorSection(
                block = draft.block,
                log = draft.log,
                onBlockChange = onBlockChange,
                onLogChange = onLogChange,
            )

            if (draft.hasReadOnlyIntentFilters) {
                Spacer(modifier = Modifier.height(12.dp))
                IntentFilterBanner()
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Text(
                text = stringResource(R.string.feature_globalifwrule_api_conditions),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            IfwRuleTreeEditor(
                rootGroup = draft.rootGroup,
                onChange = onRootGroupChange,
                rootValidationMessage = selectorRequiredMessage,
            )
            Spacer(modifier = Modifier.height(24.dp))
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
private fun AdvancedGlobalIfwRuleScreenPreviewContainer(
    content: @Composable () -> Unit,
) {
    BlockerTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
private fun AdvancedGlobalIfwRuleScreenPreview(
    draft: AdvancedGlobalIfwRuleDraft,
    isDirty: Boolean = false,
) {
    AdvancedGlobalIfwRuleScreenPreviewContainer {
        AdvancedGlobalIfwRuleScreen(
            draft = draft,
            isDirty = isDirty,
            onSave = {},
            onBack = {},
            onPackageNameChange = {},
            onComponentTypeChange = {},
            onBlockChange = {},
            onLogChange = {},
            onRootGroupChange = {},
        )
    }
}

@Composable
@PreviewThemes
private fun AdvancedGlobalIfwRuleScreenAddPreview() {
    AdvancedGlobalIfwRuleScreenPreview(
        draft = GlobalIfwRulePreviewParameterData.advancedRuleDraft,
    )
}

@Composable
@PreviewThemes
private fun AdvancedGlobalIfwRuleScreenMissingSelectorPreview() {
    AdvancedGlobalIfwRuleScreenPreview(
        draft = AdvancedGlobalIfwRuleDraft(
            storagePackageName = "com.spotify.music",
            componentType = IfwComponentType.ACTIVITY,
            block = true,
            log = true,
            rootGroup = IfwEditorNode.Group(
                children = listOf(
                    IfwEditorNode.Condition(
                        kind = IfwEditorConditionKind.ACTION,
                        value = "android.intent.action.VIEW",
                    ),
                ),
            ),
        ),
    )
}

@Composable
@PreviewThemes
private fun AdvancedGlobalIfwRuleScreenEditPreview() {
    AdvancedGlobalIfwRuleScreenPreview(
        draft = GlobalIfwRulePreviewParameterData.advancedRuleDraft.copy(
            intentFilters = listOf(
                IfwIntentFilter(
                    actions = listOf("android.intent.action.SEND"),
                    categories = listOf("android.intent.category.DEFAULT"),
                ),
            ),
            editingRuleIndex = 2,
        ),
        isDirty = true,
    )
}
