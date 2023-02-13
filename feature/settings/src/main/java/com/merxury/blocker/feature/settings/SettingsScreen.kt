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

import android.Manifest.permission
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.designsystem.component.BlockerSettingItem
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.designsystem.theme.supportsDynamicTheming
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
import com.merxury.blocker.feature.settings.item.ThemeSettings

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SettingsRoute(
    onNavigationClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settingsUiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    SettingsScreen(
        onNavigationClick = onNavigationClick,
        uiState = settingsUiState,
        onChangeShowSystemApps = viewModel::updateShowSystemApp,
        onChangeShowServiceInfo = viewModel::updateShowServiceInfo,
        onChangeBackupSystemApp = viewModel::updateBackupSystemApp,
        onChangeRestoreSystemApp = viewModel::updateRestoreSystemApp,
        onChangeRuleBackupFolder = viewModel::updateRuleBackupFolder,
        importRules = viewModel::importBlockerRules,
        exportRules = viewModel::exportBlockerRules,
        importIfwRules = viewModel::importIfwRules,
        exportIfwRules = viewModel::exportIfwRules,
        resetIfwRules = viewModel::resetIfwRules,
        onChangeControllerType = viewModel::updateControllerType,
        onChangeRuleServerProvider = viewModel::updateRuleServerProvider,
        onChangeThemeBrand = viewModel::updateThemeBrand,
        onChangeDynamicColorPreference = viewModel::updateDynamicColorPreference,
        onChangeDarkThemeConfig = viewModel::updateDarkThemeConfig,
        importMatRules = viewModel::importMyAndroidToolsRules,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermissionState = rememberPermissionState(permission.POST_NOTIFICATIONS)
        if (!notificationPermissionState.status.isGranted) {
            LaunchedEffect(Unit) {
                notificationPermissionState.launchPermissionRequest()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigationClick: () -> Unit,
    uiState: SettingsUiState,
    onChangeShowSystemApps: (Boolean) -> Unit,
    onChangeShowServiceInfo: (Boolean) -> Unit,
    onChangeBackupSystemApp: (Boolean) -> Unit,
    onChangeRestoreSystemApp: (Boolean) -> Unit,
    onChangeRuleBackupFolder: (Uri?) -> Unit,
    onChangeControllerType: (ControllerType) -> Unit,
    onChangeRuleServerProvider: (RuleServerProvider) -> Unit,
    onChangeThemeBrand: (ThemeBrand) -> Unit,
    onChangeDynamicColorPreference: (Boolean) -> Unit,
    onChangeDarkThemeConfig: (DarkThemeConfig) -> Unit,
    exportRules: () -> Unit,
    importRules: () -> Unit,
    exportIfwRules: () -> Unit,
    importIfwRules: () -> Unit,
    resetIfwRules: () -> Unit,
    importMatRules: (Uri?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            BlockerTopAppBar(
                title = stringResource(id = string.settings),
                hasNavigationIcon = true,
                onNavigationClick = onNavigationClick,
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal,
                    ),
                ),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (uiState) {
                Loading -> {
                    Column(
                        modifier = modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        BlockerLoadingWheel(
                            modifier = modifier,
                            contentDesc = stringResource(id = string.loading),
                        )
                    }
                }

                is Success -> {
                    SettingsContent(
                        settings = uiState.settings,
                        supportDynamicColor = supportsDynamicTheming(),
                        onChangeShowSystemApps = onChangeShowSystemApps,
                        onChangeShowServiceInfo = onChangeShowServiceInfo,
                        onChangeBackupSystemApp = onChangeBackupSystemApp,
                        onChangeRestoreSystemApp = onChangeRestoreSystemApp,
                        onChangeRuleBackupFolder = onChangeRuleBackupFolder,
                        onChangeControllerType = onChangeControllerType,
                        onChangeRuleServerProvider = onChangeRuleServerProvider,
                        onChangeThemeBrand = onChangeThemeBrand,
                        onChangeDynamicColorPreference = onChangeDynamicColorPreference,
                        onChangeDarkThemeConfig = onChangeDarkThemeConfig,
                        exportRules = exportRules,
                        importRules = importRules,
                        exportIfwRules = exportIfwRules,
                        importIfwRules = importIfwRules,
                        resetIfwRules = resetIfwRules,
                        importMatRules = importMatRules,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsContent(
    settings: UserEditableSettings,
    supportDynamicColor: Boolean,
    onChangeShowSystemApps: (Boolean) -> Unit,
    onChangeShowServiceInfo: (Boolean) -> Unit,
    onChangeBackupSystemApp: (Boolean) -> Unit,
    onChangeRestoreSystemApp: (Boolean) -> Unit,
    onChangeRuleBackupFolder: (Uri?) -> Unit,
    onChangeControllerType: (ControllerType) -> Unit,
    onChangeRuleServerProvider: (RuleServerProvider) -> Unit,
    onChangeThemeBrand: (ThemeBrand) -> Unit,
    onChangeDynamicColorPreference: (Boolean) -> Unit,
    onChangeDarkThemeConfig: (DarkThemeConfig) -> Unit,
    exportRules: () -> Unit,
    importRules: () -> Unit,
    exportIfwRules: () -> Unit,
    importIfwRules: () -> Unit,
    resetIfwRules: () -> Unit,
    importMatRules: (Uri?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val getMatFileResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { importMatRules(it) },
    )
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        BlockerSettings(
            settings = settings,
            onChangeControllerType = onChangeControllerType,
            onChangeRuleServerProvider = onChangeRuleServerProvider,
        )
        Divider()
        ThemeSettings(
            modifier = modifier,
            settings = settings,
            supportDynamicColor = supportDynamicColor,
            onChangeThemeBrand = onChangeThemeBrand,
            onChangeDynamicColorPreference = onChangeDynamicColorPreference,
            onChangeDarkThemeConfig = onChangeDarkThemeConfig,
        )
        Divider()
        AppListSettings(
            showSystemApps = settings.showSystemApps,
            showServiceInfo = settings.showServiceInfo,
            onChangeShowSystemApps = onChangeShowSystemApps,
            onChangeShowServiceInfo = onChangeShowServiceInfo,
        )
        Divider()
        BackupSettings(
            backupSystemApps = settings.backupSystemApp,
            restoreSystemApp = settings.restoreSystemApp,
            ruleBackupFolder = settings.ruleBackupFolder,
            onChangeBackupSystemApp = onChangeBackupSystemApp,
            onChangeRestoreSystemApp = onChangeRestoreSystemApp,
            onChangeRuleBackupFolder = onChangeRuleBackupFolder,
        )
        Divider()
        BlockerRulesSettings(exportRules = exportRules, importRules = importRules)
        Divider()
        IfwRulesSettings(
            exportIfwRules = exportIfwRules,
            importIfwRules = importIfwRules,
            resetIfwRules = resetIfwRules,
        )
        Divider()
        BlockerSettingItem(
            title = stringResource(id = string.import_mat_rules),
            onItemClick = {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                if (intent.resolveActivity(context.packageManager) != null) {
                    getMatFileResult.launch(arrayOf("*/*"))
                }
            },
            extraIconPadding = true,
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
                    darkThemeConfig = FOLLOW_SYSTEM,
                    useDynamicColor = false,
                ),
            ),
            onChangeShowSystemApps = {},
            onChangeShowServiceInfo = {},
            onChangeBackupSystemApp = {},
            onChangeRestoreSystemApp = {},
            onChangeRuleBackupFolder = {},
            importRules = {},
            exportRules = {},
            importIfwRules = {},
            exportIfwRules = {},
            resetIfwRules = {},
            onChangeControllerType = {},
            onChangeRuleServerProvider = {},
            onChangeThemeBrand = {},
            onChangeDynamicColorPreference = {},
            onChangeDarkThemeConfig = {},
            importMatRules = {},
        )
    }
}
