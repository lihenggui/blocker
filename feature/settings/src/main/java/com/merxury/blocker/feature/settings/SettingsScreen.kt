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
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITHUB
import com.merxury.blocker.feature.settings.R.string
import com.merxury.blocker.feature.settings.SettingsUiState.Loading
import com.merxury.blocker.feature.settings.SettingsUiState.Success
import com.merxury.blocker.feature.settings.item.SettingsItem

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun SettingsRoute(
    onNavigationClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settingsUiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    SettingsScreen(onNavigationClick = onNavigationClick, uiState = settingsUiState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigationClick: () -> Unit,
    uiState: SettingsUiState,
    modifier: Modifier = Modifier
) {
    if (uiState is Loading) {
        Text("Loading")
    } else if (uiState is Success) {
        Column {
            BlockerTopAppBar(
                titleRes = string.settings,
                onNavigationClick = onNavigationClick
            )
            SettingsItem(
                icon = BlockerIcons.AutoFix,
                itemRes = string.controller_type,
                itemValue = uiState.settings.controllerType.toString(),
                onItemClick = {},
                modifier = modifier
            )
            SettingsItem(
                icon = BlockerIcons.Block,
                itemRes = string.online_rule_source,
                itemValue = uiState.settings.ruleServerProvider.toString(),
                onItemClick = {},
                modifier = modifier
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
                    )
                )
            )
        }
    }
}
