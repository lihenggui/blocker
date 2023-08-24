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

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.merxury.blocker.core.testing.testing.data.userEditableSettingsTestData
import com.merxury.blocker.feature.settings.R.string
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.merxury.blocker.core.ui.R as uiR

class SettingsScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var loadingDescription: String
    private lateinit var controllerType: String
    private lateinit var shizuku: String
    private lateinit var onlineRuleSource: String
    private lateinit var optionsGitlab: String
    private lateinit var themeString: String
    private lateinit var dynamicColor: String
    private lateinit var darkMode: String
    private lateinit var dark: String
    private lateinit var applicationList: String
    private lateinit var showSystemApps: String
    private lateinit var showServiceInfo: String
    private lateinit var backup: String
    private lateinit var folderToSave: String
    private lateinit var backupSystemApps: String
    private lateinit var restoreSystemApps: String
    private lateinit var blockerRules: String
    private lateinit var exportRules: String
    private lateinit var importRules: String
    private lateinit var ifwRules: String
    private lateinit var exportIfwRules: String
    private lateinit var importIfwRules: String

    @Before
    fun setup() {
        composeTestRule.activity.apply {
            loadingDescription = getString(uiR.string.core_ui_loading)
            controllerType = getString(string.feature_settings_controller_type)
            shizuku = getString(string.feature_settings_shizuku)
            onlineRuleSource = getString(string.feature_settings_online_rule_source)
            optionsGitlab = getString(string.feature_settings_options_gitlab)
            themeString = getString(string.feature_settings_theme)
            dynamicColor = getString(string.feature_settings_dynamic_color)
            darkMode = getString(string.feature_settings_dark_mode)
            dark = getString(string.feature_settings_dark)
            applicationList = getString(string.feature_settings_application_list)
            showSystemApps = getString(string.feature_settings_show_system_apps)
            showServiceInfo = getString(string.feature_settings_show_service_info)
            backup = getString(string.feature_settings_backup)
            folderToSave = getString(string.feature_settings_folder_to_save)
            backupSystemApps = getString(string.feature_settings_backup_system_apps)
            restoreSystemApps = getString(string.feature_settings_restore_system_apps)
            blockerRules = getString(string.feature_settings_blocker_rules)
            exportRules = getString(string.feature_settings_export_rules)
            importRules = getString(string.feature_settings_import_rules)
            ifwRules = getString(string.feature_settings_ifw_rules)
            exportIfwRules = getString(string.feature_settings_export_ifw_rules)
            importIfwRules = getString(string.feature_settings_import_ifw_rules)

        }
    }

    @Test
    fun circularProgressIndicator_whenScreenIsLoading_exists() {
        composeTestRule.setContent {
            BoxWithConstraints {
                SettingsScreen(
                    onNavigationClick = {},
                    uiState = SettingsUiState.Loading,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(loadingDescription).assertExists()
    }

    @Test
    fun showSettings_afterLoading() {
        composeTestRule.setContent {
            BoxWithConstraints {
                SettingsScreen(
                    onNavigationClick = {},
                    uiState = SettingsUiState.Success(
                        userEditableSettingsTestData,
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag("settings:content").assertExists()
        composeTestRule.onNodeWithText(controllerType).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(shizuku).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(onlineRuleSource).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(optionsGitlab).assertExists().assertHasClickAction()
        // Theme settings
        composeTestRule.onNodeWithText(themeString).assertExists()
        composeTestRule.onNodeWithText(dynamicColor).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(darkMode).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(dark).assertExists().assertHasClickAction()
        // Application list settings
        composeTestRule.onNodeWithText(applicationList).assertExists()
        composeTestRule.onNodeWithText(showSystemApps).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(showServiceInfo).assertExists().assertHasClickAction()
        // Backup settings
        composeTestRule.onNodeWithText(backup).assertExists()
        composeTestRule.onNodeWithText(folderToSave).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(backupSystemApps).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(restoreSystemApps).assertExists().assertHasClickAction()
        // Blocker rules settings
        composeTestRule.onNodeWithText(blockerRules).assertExists()
        composeTestRule.onNodeWithText(exportRules).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(importRules).assertExists().assertHasClickAction()
        // IFW rules settings
        composeTestRule.onNodeWithText(ifwRules).assertExists()
        composeTestRule.onNodeWithText(exportIfwRules).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(importIfwRules).assertExists().assertHasClickAction()
    }
}
