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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.designsystem.theme.supportsDynamicTheming
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.UserEditableSettings
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.DarkThemeConfig.FOLLOW_SYSTEM
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITHUB
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.entity.RuleWorkType.EXPORT_BLOCKER_RULES
import com.merxury.blocker.core.rule.entity.RuleWorkType.EXPORT_IFW_RULES
import com.merxury.blocker.core.rule.entity.RuleWorkType.IMPORT_BLOCKER_RULES
import com.merxury.blocker.core.rule.entity.RuleWorkType.IMPORT_IFW_RULES
import com.merxury.blocker.core.rule.entity.RuleWorkType.RESET_IFW
import com.merxury.blocker.core.ui.BlockerSettingItem
import com.merxury.blocker.core.ui.DevicePreviews
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.settings.R.string
import com.merxury.blocker.feature.settings.SettingsUiState.Loading
import com.merxury.blocker.feature.settings.SettingsUiState.Success
import com.merxury.blocker.feature.settings.item.AppListSettings
import com.merxury.blocker.feature.settings.item.BackupSettings
import com.merxury.blocker.feature.settings.item.BlockerRulesSettings
import com.merxury.blocker.feature.settings.item.BlockerSettings
import com.merxury.blocker.feature.settings.item.IfwRulesSettings
import com.merxury.blocker.feature.settings.item.ThemeSettings
import kotlinx.coroutines.launch
import com.merxury.blocker.core.rule.R.string as CoreRuleR

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SettingsRoute(
    onNavigationClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settingsUiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    val event by viewModel.eventFlow.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    SupportAndFeedbackScreen(
        onNavigationClick = onNavigationClick,
        uiState = settingsUiState,
        onChangeControllerType = viewModel::updateControllerType,
        onChangeRuleServerProvider = viewModel::updateRuleServerProvider,
        onChangeAppDisplayLanguage = viewModel::updateAppDisplayLanguage,
        onChangeLibDisplayLanguage = viewModel::updateLibDisplayLanguage,
        onChangeDynamicColorPreference = viewModel::updateDynamicColorPreference,
        onChangeDarkThemeConfig = viewModel::updateDarkThemeConfig,
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
    event?.let {
        val work = it.first
        val result = it.second
        val messageRes = when (result) {
            RuleWorkResult.STARTED -> when (work) {
                IMPORT_BLOCKER_RULES -> CoreRuleR.core_rule_import_app_rules_please_wait
                EXPORT_BLOCKER_RULES -> CoreRuleR.core_rule_backing_up_apps_please_wait
                EXPORT_IFW_RULES -> CoreRuleR.core_rule_backing_up_ifw_please_wait
                IMPORT_IFW_RULES -> CoreRuleR.core_rule_import_ifw_please_wait
                RESET_IFW -> CoreRuleR.core_rule_reset_ifw_please_wait
            }

            RuleWorkResult.FINISHED -> CoreRuleR.core_rule_done
            RuleWorkResult.FOLDER_NOT_DEFINED,
            RuleWorkResult.MISSING_STORAGE_PERMISSION,
            -> CoreRuleR.core_rule_error_msg_folder_not_defined

            RuleWorkResult.MISSING_ROOT_PERMISSION -> CoreRuleR.core_rule_error_msg_missing_root_permission
            RuleWorkResult.UNEXPECTED_EXCEPTION -> CoreRuleR.core_rule_error_msg_unexpected_exception
            RuleWorkResult.CANCELLED -> CoreRuleR.core_rule_task_cancelled
            else -> CoreRuleR.core_rule_error_msg_unexpected_exception
        }
        val message = stringResource(id = messageRes)
        val duration = if (result == RuleWorkResult.STARTED) {
            SnackbarDuration.Long
        } else {
            SnackbarDuration.Short
        }
        LaunchedEffect(message) {
            coroutineScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = duration,
                    withDismissAction = true,
                )
            }
        }
    }
}

@Composable
fun SupportAndFeedbackScreen(
    uiState: SettingsUiState,
    modifier: Modifier = Modifier,
    onNavigationClick: () -> Unit = { },
    onChangeControllerType: (ControllerType) -> Unit = { },
    onChangeRuleServerProvider: (RuleServerProvider) -> Unit = { },
    onChangeAppDisplayLanguage: (String) -> Unit = { },
    onChangeLibDisplayLanguage: (String) -> Unit = { },
    onChangeDynamicColorPreference: (Boolean) -> Unit = { },
    onChangeDarkThemeConfig: (DarkThemeConfig) -> Unit = { },
    onChangeShowSystemApps: (Boolean) -> Unit = { },
    onChangeShowServiceInfo: (Boolean) -> Unit = { },
    onChangeBackupSystemApp: (Boolean) -> Unit = { },
    onChangeRestoreSystemApp: (Boolean) -> Unit = { },
    onChangeRuleBackupFolder: (Uri?) -> Unit = { },
    exportRules: () -> Unit = { },
    importRules: () -> Unit = { },
    exportIfwRules: () -> Unit = { },
    importIfwRules: () -> Unit = { },
    resetIfwRules: () -> Unit = { },
    importMatRules: (Uri?) -> Unit = { },
) {
    Scaffold(
        topBar = {
            BlockerTopAppBar(
                title = stringResource(id = string.feature_settings_settings),
                hasNavigationIcon = true,
                onNavigationClick = onNavigationClick,
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
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
                    LoadingScreen()
                }

                is Success -> {
                    SettingsContent(
                        settings = uiState.settings,
                        supportDynamicColor = supportsDynamicTheming(),
                        onChangeControllerType = onChangeControllerType,
                        onChangeRuleServerProvider = onChangeRuleServerProvider,
                        onChangeAppDisplayLanguage = onChangeAppDisplayLanguage,
                        onChangeLibDisplayLanguage = onChangeLibDisplayLanguage,
                        onChangeDynamicColorPreference = onChangeDynamicColorPreference,
                        onChangeDarkThemeConfig = onChangeDarkThemeConfig,
                        onChangeShowSystemApps = onChangeShowSystemApps,
                        onChangeShowServiceInfo = onChangeShowServiceInfo,
                        onChangeBackupSystemApp = onChangeBackupSystemApp,
                        onChangeRestoreSystemApp = onChangeRestoreSystemApp,
                        onChangeRuleBackupFolder = onChangeRuleBackupFolder,
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
    modifier: Modifier = Modifier,
    onChangeControllerType: (ControllerType) -> Unit = { },
    onChangeRuleServerProvider: (RuleServerProvider) -> Unit = { },
    onChangeAppDisplayLanguage: (String) -> Unit = { },
    onChangeLibDisplayLanguage: (String) -> Unit = { },
    onChangeDynamicColorPreference: (Boolean) -> Unit = { },
    onChangeDarkThemeConfig: (DarkThemeConfig) -> Unit = { },
    onChangeShowSystemApps: (Boolean) -> Unit = { },
    onChangeShowServiceInfo: (Boolean) -> Unit = { },
    onChangeBackupSystemApp: (Boolean) -> Unit = { },
    onChangeRestoreSystemApp: (Boolean) -> Unit = { },
    onChangeRuleBackupFolder: (Uri?) -> Unit = { },
    exportRules: () -> Unit = { },
    importRules: () -> Unit = { },
    exportIfwRules: () -> Unit = { },
    importIfwRules: () -> Unit = { },
    resetIfwRules: () -> Unit = { },
    importMatRules: (Uri?) -> Unit = { },
) {
    val context = LocalContext.current
    val getMatFileResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { importMatRules(it) },
    )
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .testTag("settings:content"),
    ) {
        BlockerSettings(
            settings = settings,
            onChangeControllerType = onChangeControllerType,
            onChangeRuleServerProvider = onChangeRuleServerProvider,
            onChangeAppDisplayLanguage = onChangeAppDisplayLanguage,
            onChangeLibDisplayLanguage = onChangeLibDisplayLanguage,
        )
        HorizontalDivider()
        ThemeSettings(
            modifier = modifier,
            settings = settings,
            supportDynamicColor = supportDynamicColor,
            onChangeDynamicColorPreference = onChangeDynamicColorPreference,
            onChangeDarkThemeConfig = onChangeDarkThemeConfig,
        )
        HorizontalDivider()
        AppListSettings(
            showSystemApps = settings.showSystemApps,
            showServiceInfo = settings.showServiceInfo,
            onChangeShowSystemApps = onChangeShowSystemApps,
            onChangeShowServiceInfo = onChangeShowServiceInfo,
        )
        HorizontalDivider()
        BackupSettings(
            backupSystemApps = settings.backupSystemApp,
            restoreSystemApp = settings.restoreSystemApp,
            ruleBackupFolder = settings.ruleBackupFolder,
            onChangeBackupSystemApp = onChangeBackupSystemApp,
            onChangeRestoreSystemApp = onChangeRestoreSystemApp,
            onChangeRuleBackupFolder = onChangeRuleBackupFolder,
        )
        HorizontalDivider()
        BlockerRulesSettings(exportRules = exportRules, importRules = importRules)
        HorizontalDivider()
        IfwRulesSettings(
            exportIfwRules = exportIfwRules,
            importIfwRules = importIfwRules,
            resetIfwRules = resetIfwRules,
        )
        HorizontalDivider()
        BlockerSettingItem(
            title = stringResource(id = string.feature_settings_import_mat_rules),
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
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
    }
}

@Composable
@DevicePreviews
fun SettingsScreenPreview() {
    BlockerTheme {
        Surface {
            SupportAndFeedbackScreen(
                uiState = Success(
                    UserEditableSettings(
                        controllerType = IFW,
                        ruleServerProvider = GITHUB,
                        ruleBackupFolder = "/emulated/0/Blocker",
                        backupSystemApp = true,
                        restoreSystemApp = false,
                        showSystemApps = false,
                        showServiceInfo = true,
                        darkThemeConfig = FOLLOW_SYSTEM,
                        useDynamicColor = false,
                    ),
                ),
            )
        }
    }
}
