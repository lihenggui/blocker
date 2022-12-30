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
import com.merxury.blocker.feature.settings.R.string
import com.merxury.blocker.feature.settings.component.SettingsItemComponent

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
    Column {
        BlockerTopAppBar(
            titleRes = string.settings,
            onNavigationClick = onNavigationClick
        )
        SettingsItemComponent(
            icon = BlockerIcons.AutoFix,
            itemRes = string.controller_type,
            itemValue = uiState.controllerType.type,
            onItemClick = {},
            modifier = modifier
        )
        SettingsItemComponent(
            icon = BlockerIcons.Block,
            itemRes = string.online_rule_source,
            itemValue = uiState.onlineRulesSource.source,
            onItemClick = {},
            modifier = modifier
        )
    }
}

@Composable
@Preview
fun SettingsScreenPreview() {
    BlockerTheme {
        SettingsScreen(
            onNavigationClick = {},
            uiState = SettingsUiState()
        )
    }
}
