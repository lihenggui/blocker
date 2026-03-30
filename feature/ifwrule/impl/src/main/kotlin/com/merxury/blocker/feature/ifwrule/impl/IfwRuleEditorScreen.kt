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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.ifwruleeditor.IfwRuleTreeEditor
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.ifwrule.api.R
import com.merxury.core.ifw.editor.IfwEditorNode

@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun IfwRuleEditorScreen(
    onBackClick: () -> Unit,
    viewModel: IfwRuleEditorViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val saveComplete by viewModel.saveComplete.collectAsStateWithLifecycle()
    val hasUnsavedChanges = (uiState as? RuleEditorScreenUiState.Success)?.hasUnsavedChanges == true
    var showUnsavedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(saveComplete) {
        if (saveComplete) {
            onBackClick()
        }
    }

    val handleBack: () -> Unit = {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onBackClick()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BlockerTopAppBar(
            title = stringResource(R.string.feature_ifwrule_impl_title),
            hasNavigationIcon = true,
            onNavigationClick = handleBack,
            actions = {
                if (uiState is RuleEditorScreenUiState.Success) {
                    SaveActionButton(onClick = viewModel::save)
                }
            },
        )
        when (val state = uiState) {
            RuleEditorScreenUiState.Loading -> LoadingScreen()
            is RuleEditorScreenUiState.Error -> ErrorScreen(error = UiMessage(state.message))
            is RuleEditorScreenUiState.Success -> EditorContent(
                editor = state.editor,
                onUpdateBlockMode = viewModel::updateBlockMode,
                onUpdateLog = viewModel::updateLog,
                onChangeBlockEnable = viewModel::updateBlockEnabled,
                onUpdateRootGroup = viewModel::updateRootGroup,
                onDelete = viewModel::deleteRule,
            )
        }
    }

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            onDiscard = {
                showUnsavedDialog = false
                onBackClick()
            },
            onCancel = { showUnsavedDialog = false },
        )
    }
}

@Composable
private fun SaveActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = BlockerIcons.Check,
            contentDescription = stringResource(R.string.feature_ifwrule_impl_save),
        )
    }
}

@Composable
private fun UnsavedChangesDialog(
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BlockerWarningAlertDialog(
        onDismissRequest = onCancel,
        title = stringResource(R.string.feature_ifwrule_impl_unsaved_title),
        text = stringResource(R.string.feature_ifwrule_impl_unsaved_message),
        onConfirmRequest = onDiscard,
        modifier = modifier,
    )
}

@Composable
private fun EditorContent(
    editor: RuleEditorUiState,
    onUpdateBlockMode: (BlockMode) -> Unit,
    onUpdateLog: (Boolean) -> Unit,
    onChangeBlockEnable: (Boolean) -> Unit,
    onUpdateRootGroup: (IfwEditorNode.Group) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        ComponentNameSubtitle(componentName = editor.componentName)

        if (editor.isAdvancedRule) {
            AdvancedRuleBanner(onDelete = onDelete)
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            return
        }

        BlockModeSection(
            blockMode = editor.blockMode,
            onUpdateBlockMode = onUpdateBlockMode,
        )

        if (editor.blockMode == BlockMode.CONDITIONAL) {
            ConditionalSection(
                rootGroup = editor.rootGroup,
                onUpdateRootGroup = onUpdateRootGroup,
            )
        }

        AdvancedOptionsSection(
            log = editor.log,
            blockEnabled = editor.blockEnabled,
            onUpdateLog = onUpdateLog,
            onChangeBlockEnable = onChangeBlockEnable,
        )

        Spacer(modifier = Modifier.height(16.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
    }
}

@Composable
private fun ComponentNameSubtitle(
    componentName: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = componentName.substringAfterLast('.'),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun BlockModeSection(
    blockMode: BlockMode,
    onUpdateBlockMode: (BlockMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider()
        Text(
            text = stringResource(R.string.feature_ifwrule_impl_block_mode),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        BlockModeRadioRow(
            label = stringResource(R.string.feature_ifwrule_impl_block_all),
            selected = blockMode == BlockMode.ALL,
            onClick = { onUpdateBlockMode(BlockMode.ALL) },
        )
        BlockModeRadioRow(
            label = stringResource(R.string.feature_ifwrule_impl_block_conditional),
            selected = blockMode == BlockMode.CONDITIONAL,
            onClick = { onUpdateBlockMode(BlockMode.CONDITIONAL) },
        )
    }
}

@Composable
private fun ConditionalSection(
    rootGroup: IfwEditorNode.Group,
    onUpdateRootGroup: (IfwEditorNode.Group) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Text(
            text = stringResource(R.string.feature_ifwrule_impl_conditions),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        IfwRuleTreeEditor(
            rootGroup = rootGroup,
            onChange = onUpdateRootGroup,
        )
    }
}

@Composable
private fun AdvancedOptionsSection(
    log: Boolean,
    blockEnabled: Boolean,
    onUpdateLog: (Boolean) -> Unit,
    onChangeBlockEnable: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Text(
            text = stringResource(R.string.feature_ifwrule_impl_advanced_options),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        SwitchRow(
            label = stringResource(R.string.feature_ifwrule_impl_log),
            summary = stringResource(R.string.feature_ifwrule_impl_log_summary),
            checked = log,
            onCheckedChange = onUpdateLog,
        )
        SwitchRow(
            label = stringResource(R.string.feature_ifwrule_impl_monitor_only),
            summary = stringResource(R.string.feature_ifwrule_impl_monitor_only_summary),
            checked = !blockEnabled,
            onCheckedChange = { onChangeBlockEnable(!it) },
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
                text = stringResource(R.string.feature_ifwrule_impl_advanced_rule_banner),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            BlockerButton(
                onClick = onDelete,
                text = { Text(stringResource(R.string.feature_ifwrule_impl_advanced_rule_delete)) },
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
