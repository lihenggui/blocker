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
import org.junit.Rule
import org.junit.Test
import com.merxury.blocker.core.ui.R as uiR

class SettingsScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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

        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.resources.getString(uiR.string.core_ui_loading),
        ).assertExists()
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
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_controller_type),
        ).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_shizuku),
        ).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_online_rule_source),
        ).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_options_gitlab),
        ).assertExists().assertHasClickAction()
        // Theme settings
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_theme),
        ).assertExists()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_dynamic_color),
        ).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_dark_mode),
        ).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_dark),
        ).assertExists().assertHasClickAction()
        // Application list settings
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_application_list),
        ).assertExists()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_show_system_apps),
        ).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_show_service_info),
        ).assertExists().assertHasClickAction()
        // Backup settings
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_backup),
        ).assertExists()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_folder_to_save),
        ).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_backup_system_apps),
        ).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_restore_system_apps),
        ).assertExists().assertHasClickAction()
        // Blocker rules settings
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_blocker_rules),
        ).assertExists()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_export_rules),
        ).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_import_rules),
        ).assertExists().assertHasClickAction()
        // IFW rules settings
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_ifw_rules),
        ).assertExists()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_export_ifw_rules),
        ).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.resources.getString(string.feature_settings_import_ifw_rules),
        ).assertExists().assertHasClickAction()
    }
}
