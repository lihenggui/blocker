/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITHUB
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITLAB
import com.merxury.blocker.feature.settings.R.string
import com.merxury.blocker.feature.settings.SettingsUiState.Loading
import com.merxury.blocker.feature.settings.SettingsUiState.Success
import com.merxury.blocker.feature.settings.item.AppListSettings
import com.merxury.blocker.feature.settings.item.BackupSettings
import com.merxury.blocker.feature.settings.item.BlockerRulesSettings
import com.merxury.blocker.feature.settings.item.IfwRulesSettings
import com.merxury.blocker.feature.settings.item.SettingItem
import com.merxury.blocker.feature.settings.item.SettingsItem

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun SettingsRoute(
    onNavigationClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settingsUiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    SettingsScreen(
        onNavigationClick = onNavigationClick,
        uiState = settingsUiState,
        updateShowSystemApps = viewModel::updateShowSystemApp,
        updateShowServiceInfo = viewModel::updateShowServiceInfo,
        updateBackupSystemApp = viewModel::updateBackupSystemApp,
        updateRestoreSystemApp = viewModel::updateRestoreSystemApp,
        updateRuleBackupFolder = viewModel::updateRuleBackupFolder,
        importRules = viewModel::importBlockerRules,
        exportRules = viewModel::exportBlockerRules,
        importIfwRules = viewModel::importIfwRules,
        exportIfwRules = viewModel::exportIfwRules,
        resetIfwRules = viewModel::resetIfwRules,
        updateControllerType = viewModel::updateControllerType,
        updateRuleServerProvider = viewModel::updateRuleServerProvider
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigationClick: () -> Unit,
    uiState: SettingsUiState,
    updateShowSystemApps: (Boolean) -> Unit,
    updateShowServiceInfo: (Boolean) -> Unit,
    updateBackupSystemApp: (Boolean) -> Unit,
    updateRestoreSystemApp: (Boolean) -> Unit,
    updateRuleBackupFolder: (String) -> Unit,
    updateControllerType: (ControllerType) -> Unit,
    updateRuleServerProvider: (RuleServerProvider) -> Unit,
    exportRules: () -> Unit,
    importRules: () -> Unit,
    exportIfwRules: () -> Unit,
    importIfwRules: () -> Unit,
    resetIfwRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState is Loading) {
        Text("Loading")
    } else if (uiState is Success) {
        Column(modifier = modifier.verticalScroll(rememberScrollState())) {
            BlockerTopAppBar(
                titleRes = string.settings,
                onNavigationClick = onNavigationClick
            )
            SettingItem(
                icon = BlockerIcons.AutoFix,
                itemRes = string.controller_type,
                itemValue = uiState.settings.controllerType.toString(),
                menuList = listOf(IFW, PM, SHIZUKU),
                onMenuClick = updateControllerType as (Any) -> Unit,
                modifier = modifier
            )
            SettingItem(
                icon = BlockerIcons.Block,
                itemRes = string.online_rule_source,
                itemValue = uiState.settings.ruleServerProvider.toString(),
                menuList = listOf(GITHUB, GITLAB),
                onMenuClick = updateRuleServerProvider as (Any) -> Unit,
                modifier = modifier
            )
            SettingsItem(
                itemRes = string.import_mat_rules,
                onItemClick = {}
            )
            Divider()
            AppListSettings(
                showSystemApps = uiState.settings.showSystemApps,
                showServiceInfo = uiState.settings.showServiceInfo,
                updateShowSystemApps = updateShowSystemApps,
                updateShowServiceInfo = updateShowServiceInfo
            )
            Divider()
            BackupSettings(
                backupSystemApps = uiState.settings.backupSystemApp,
                restoreSystemApp = uiState.settings.restoreSystemApp,
                ruleBackupFolder = uiState.settings.ruleBackupFolder,
                updateBackupSystemApp = updateBackupSystemApp,
                updateRestoreSystemApp = updateRestoreSystemApp,
                updateRuleBackupFolder = updateRuleBackupFolder
            )
            Divider()
            BlockerRulesSettings(exportRules = exportRules, importRules = importRules)
            Divider()
            IfwRulesSettings(
                exportIfwRules = exportIfwRules,
                importIfwRules = importIfwRules,
                resetIfwRules = resetIfwRules
            )
        }
    }
}

@Composable
@Preview
fun SettingsScreenPreview() {
    BlockerTheme {
        Surface {
            SettingsScreen(
                onNavigationClick = {},
                uiState = Success(
                    UserEditableSettings(
                        controllerType = IFW,
                        ruleServerProvider = GITHUB,
                        ruleBackupFolder = "/emulated/0/Blocker",
                        backupSystemApp = true,
                        restoreSystemApp = false,
                        showSystemApps = false,
                        showServiceInfo = true
                    )
                ),
                updateShowSystemApps = {},
                updateShowServiceInfo = {},
                updateBackupSystemApp = {},
                updateRestoreSystemApp = {},
                updateRuleBackupFolder = {},
                importRules = {},
                exportRules = {},
                importIfwRules = {},
                exportIfwRules = {},
                resetIfwRules = {},
                updateControllerType = {},
                updateRuleServerProvider = {}
            )
        }
    }
}
