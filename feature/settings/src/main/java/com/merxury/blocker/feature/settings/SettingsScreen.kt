/*
 * Copyright 2023 Blocker
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumedWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.DarkThemeConfig.FOLLOW_SYSTEM
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITHUB
import com.merxury.blocker.core.model.preference.ThemeBrand
import com.merxury.blocker.core.model.preference.ThemeBrand.ANDROID
import com.merxury.blocker.feature.settings.R.string
import com.merxury.blocker.feature.settings.SettingsUiState.Loading
import com.merxury.blocker.feature.settings.SettingsUiState.Success
import com.merxury.blocker.feature.settings.item.AppListSettings
import com.merxury.blocker.feature.settings.item.BackupSettings
import com.merxury.blocker.feature.settings.item.BlockerRulesSettings
import com.merxury.blocker.feature.settings.item.BlockerSettings
import com.merxury.blocker.feature.settings.item.IfwRulesSettings
import com.merxury.blocker.feature.settings.item.SingleRowSettingItem
import com.merxury.blocker.feature.settings.item.ThemeSettings

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
        updateRuleServerProvider = viewModel::updateRuleServerProvider,
        updateThemeBrand = viewModel::updateThemeBrand,
        updateDarkThemeConfig = viewModel::updateDarkThemeConfig
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    updateThemeBrand: (ThemeBrand) -> Unit,
    updateDarkThemeConfig: (DarkThemeConfig) -> Unit,
    exportRules: () -> Unit,
    importRules: () -> Unit,
    exportIfwRules: () -> Unit,
    importIfwRules: () -> Unit,
    resetIfwRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            BlockerTopAppBar(
                titleRes = string.settings,
                onNavigationClick = onNavigationClick
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .consumedWindowInsets(padding)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal
                    )
                ),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState) {
                Loading -> {
                    Column(
                        modifier = modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BlockerLoadingWheel(
                            modifier = modifier,
                            contentDesc = stringResource(id = string.loading),
                        )
                    }
                }

                is Success -> {
                    SettingsContent(
                        uiState = uiState,
                        updateShowSystemApps = updateShowSystemApps,
                        updateShowServiceInfo = updateShowServiceInfo,
                        updateBackupSystemApp = updateBackupSystemApp,
                        updateRestoreSystemApp = updateRestoreSystemApp,
                        updateRuleBackupFolder = updateRuleBackupFolder,
                        updateControllerType = updateControllerType,
                        updateRuleServerProvider = updateRuleServerProvider,
                        exportRules = exportRules,
                        importRules = importRules,
                        exportIfwRules = exportIfwRules,
                        importIfwRules = importIfwRules,
                        resetIfwRules = resetIfwRules,
                        updateThemeBrand = updateThemeBrand,
                        updateDarkThemeConfig = updateDarkThemeConfig
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsContent(
    uiState: Success,
    updateShowSystemApps: (Boolean) -> Unit,
    updateShowServiceInfo: (Boolean) -> Unit,
    updateBackupSystemApp: (Boolean) -> Unit,
    updateRestoreSystemApp: (Boolean) -> Unit,
    updateRuleBackupFolder: (String) -> Unit,
    updateControllerType: (ControllerType) -> Unit,
    updateRuleServerProvider: (RuleServerProvider) -> Unit,
    updateThemeBrand: (ThemeBrand) -> Unit,
    updateDarkThemeConfig: (DarkThemeConfig) -> Unit,
    exportRules: () -> Unit,
    importRules: () -> Unit,
    exportIfwRules: () -> Unit,
    importIfwRules: () -> Unit,
    resetIfwRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        BlockerSettings(
            uiState = uiState,
            updateControllerType = updateControllerType,
            updateRuleServerProvider = updateRuleServerProvider
        )
        Divider()
        ThemeSettings(
            modifier = modifier,
            uiState = uiState,
            updateThemeBrand = updateThemeBrand,
            updateDarkThemeConfig = updateDarkThemeConfig
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
        Divider()
        SingleRowSettingItem(
            itemRes = string.import_mat_rules,
            onItemClick = {}
        )
    }
}

@Composable
@Preview
fun SettingsScreenPreview() {
    BlockerTheme {
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
                    showServiceInfo = true,
                    themeBrand = ANDROID,
                    darkThemeConfig = FOLLOW_SYSTEM
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
            updateRuleServerProvider = {},
            updateThemeBrand = {},
            updateDarkThemeConfig = {}
        )
    }
}
