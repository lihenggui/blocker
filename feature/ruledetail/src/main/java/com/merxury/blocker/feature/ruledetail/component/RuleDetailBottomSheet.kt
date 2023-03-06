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

package com.merxury.blocker.feature.ruledetail.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.bottomSheet.BottomSheetTopBar
import com.merxury.blocker.core.ui.rule.RuleDetailTabs
import com.merxury.blocker.core.ui.rule.RuleMatchedAppListUiState
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.ruledetail.RuleDetailTabContent
import com.merxury.blocker.feature.ruledetail.model.RuleDetailViewModel
import com.merxury.blocker.feature.ruledetail.model.RuleInfoUiState

@Composable
fun BottomSheetRoute(
    viewModel: RuleDetailViewModel = hiltViewModel(),
) {
    val ruleInfoUiState by viewModel.ruleInfoUiState.collectAsStateWithLifecycle()
    val ruleMatchedAppListUiState by viewModel.ruleMatchedAppListUiState.collectAsStateWithLifecycle()
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    RuleDetailBottomSheet(
        ruleMatchedAppListUiState = ruleMatchedAppListUiState,
        ruleInfoUiState = ruleInfoUiState,
        tabState = tabState,
        switchTab = viewModel::switchTab,
        onStopServiceClick = viewModel::stopService,
        onLaunchActivityClick = viewModel::launchActivity,
        onCopyNameClick = { clipboardManager.setText(AnnotatedString(it)) },
        onCopyFullNameClick = { clipboardManager.setText(AnnotatedString(it)) },
        onSwitch = viewModel::controlComponent,
    )
}

@Composable
fun RuleDetailBottomSheet(
    modifier: Modifier = Modifier,
    ruleMatchedAppListUiState: RuleMatchedAppListUiState,
    ruleInfoUiState: RuleInfoUiState,
    tabState: TabState<RuleDetailTabs>,
    switchTab: (RuleDetailTabs) -> Unit,
    onStopServiceClick: (String, String) -> Unit,
    onLaunchActivityClick: (String, String) -> Unit,
    onCopyNameClick: (String) -> Unit,
    onCopyFullNameClick: (String) -> Unit,
    onSwitch: (String, String, Boolean) -> Unit,
) {
    when (ruleInfoUiState) {
        RuleInfoUiState.Loading -> {
            LoadingScreen()
        }

        is RuleInfoUiState.Success -> {
            Column(modifier = modifier.defaultMinSize(1.dp)) {
                BottomSheetTopBar(
                    title = ruleInfoUiState.ruleInfo.name,
                    subTitle = ruleInfoUiState.ruleInfo.company.toString(),
                    iconSource = ruleInfoUiState.ruleInfo.iconUrl,
                )
                RuleDetailTabContent(
                    ruleMatchedAppListUiState = ruleMatchedAppListUiState,
                    ruleInfoUiState = ruleInfoUiState,
                    tabState = tabState,
                    switchTab = switchTab,
                    onStopServiceClick = onStopServiceClick,
                    onLaunchActivityClick = onLaunchActivityClick,
                    onCopyNameClick = onCopyNameClick,
                    onCopyFullNameClick = onCopyFullNameClick,
                    onSwitch = onSwitch,
                )
            }
        }

        is RuleInfoUiState.Error -> {
            ErrorScreen(error = ruleInfoUiState.error)
        }
    }
}
