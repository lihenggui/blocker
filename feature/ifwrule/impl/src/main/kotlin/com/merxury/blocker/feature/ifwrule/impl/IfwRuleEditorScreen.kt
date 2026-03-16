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

package com.merxury.blocker.feature.ifwrule.impl

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerButton
import com.merxury.blocker.core.designsystem.component.BlockerOutlinedButton
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.ifwrule.impl.component.ConditionCard
import com.merxury.blocker.feature.ifwrule.impl.component.ConditionTypeBottomSheet
import com.merxury.blocker.feature.ifwrule.impl.model.BlockMode
import com.merxury.blocker.feature.ifwrule.impl.model.CombineMode
import com.merxury.blocker.feature.ifwrule.impl.model.RuleEditorScreenUiState
import com.merxury.blocker.feature.ifwrule.impl.model.RuleEditorUiState

@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun IfwRuleEditorScreen(
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    viewModel: IfwRuleEditorViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val saveComplete by viewModel.saveComplete.collectAsStateWithLifecycle()

    LaunchedEffect(saveComplete) {
        if (saveComplete) {
            onBackClick()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BlockerTopAppBar(
            title = stringResource(R.string.feature_ifwrule_title),
            hasNavigationIcon = true,
            onNavigationClick = onBackClick,
        )
        when (val state = uiState) {
            is RuleEditorScreenUiState.Loading -> LoadingScreen()
            is RuleEditorScreenUiState.Error -> ErrorContent(state.message)
            is RuleEditorScreenUiState.Success -> EditorContent(
                editor = state.editor,
                onUpdateBlockMode = viewModel::updateBlockMode,
                onUpdateCombineMode = viewModel::updateCombineMode,
                onUpdateLog = viewModel::updateLog,
                onChangeBlockEnable = viewModel::updateBlockEnabled,
                onAddCondition = viewModel::addCondition,
                onRemoveCondition = viewModel::removeCondition,
                onUpdateCondition = viewModel::updateCondition,
                onSave = viewModel::save,
                onDelete = viewModel::deleteRule,
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun EditorContent(
    editor: RuleEditorUiState,
    onUpdateBlockMode: (BlockMode) -> Unit,
    onUpdateCombineMode: (CombineMode) -> Unit,
    onUpdateLog: (Boolean) -> Unit,
    onChangeBlockEnable: (Boolean) -> Unit,
    onAddCondition: (com.merxury.blocker.feature.ifwrule.impl.model.ConditionUiState) -> Unit,
    onRemoveCondition: (String) -> Unit,
    onUpdateCondition: (com.merxury.blocker.feature.ifwrule.impl.model.ConditionUiState) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConditionPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // Component name subtitle
        Text(
            text = editor.componentName.substringAfterLast('.'),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        // Advanced rule banner
        if (editor.isAdvancedRule) {
            AdvancedRuleBanner(onDelete = onDelete)
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            return
        }

        // Block mode
        HorizontalDivider()
        Text(
            text = stringResource(R.string.feature_ifwrule_block_mode),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        BlockModeRadioRow(
            label = stringResource(R.string.feature_ifwrule_block_all),
            selected = editor.blockMode == BlockMode.ALL,
            onClick = { onUpdateBlockMode(BlockMode.ALL) },
        )
        BlockModeRadioRow(
            label = stringResource(R.string.feature_ifwrule_block_conditional),
            selected = editor.blockMode == BlockMode.CONDITIONAL,
            onClick = { onUpdateBlockMode(BlockMode.CONDITIONAL) },
        )

        // Conditional section
        if (editor.blockMode == BlockMode.CONDITIONAL) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Text(
                text = stringResource(R.string.feature_ifwrule_conditions),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )

            // Combine mode
            CombineModeSelector(
                combineMode = editor.combineMode,
                onUpdate = onUpdateCombineMode,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Condition list
            editor.conditions.forEach { condition ->
                ConditionCard(
                    condition = condition,
                    onUpdate = onUpdateCondition,
                    onDelete = onRemoveCondition,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            BlockerOutlinedButton(
                onClick = { showConditionPicker = true },
                text = { Text(stringResource(R.string.feature_ifwrule_add_condition)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Advanced options
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Text(
            text = stringResource(R.string.feature_ifwrule_advanced_options),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        SwitchRow(
            label = stringResource(R.string.feature_ifwrule_log),
            summary = stringResource(R.string.feature_ifwrule_log_summary),
            checked = editor.log,
            onCheckedChange = onUpdateLog,
        )
        SwitchRow(
            label = stringResource(R.string.feature_ifwrule_monitor_only),
            summary = stringResource(R.string.feature_ifwrule_monitor_only_summary),
            checked = !editor.blockEnabled,
            onCheckedChange = { onChangeBlockEnable(!it) },
        )

        // Save button
        Spacer(modifier = Modifier.height(24.dp))
        BlockerButton(
            onClick = onSave,
            text = { Text(stringResource(R.string.feature_ifwrule_save)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
    }

    if (showConditionPicker) {
        ConditionTypeBottomSheet(
            onDismiss = { showConditionPicker = false },
            onSelect = onAddCondition,
        )
    }
}

@Composable
private fun AdvancedRuleBanner(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.feature_ifwrule_advanced_rule_banner),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            BlockerOutlinedButton(
                onClick = onDelete,
                text = { Text(stringResource(R.string.feature_ifwrule_advanced_rule_delete)) },
            )
        }
    }
}

@Composable
private fun BlockModeRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CombineModeSelector(
    combineMode: CombineMode,
    onUpdate: (CombineMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feature_ifwrule_combine_label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = combineMode == CombineMode.ALL_MATCH,
                onClick = { onUpdate(CombineMode.ALL_MATCH) },
            )
            Text(
                text = stringResource(R.string.feature_ifwrule_combine_all),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = combineMode == CombineMode.ANY_MATCH,
                onClick = { onUpdate(CombineMode.ANY_MATCH) },
            )
            Text(
                text = stringResource(R.string.feature_ifwrule_combine_any),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    summary: String,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BlockerSwitch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}
