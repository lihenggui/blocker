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

package com.merxury.blocker.feature.search.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.designsystem.component.BlockerScrollableTabRow
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.feature.search.AppSearchResultContent
import com.merxury.blocker.feature.search.ComponentSearchResultContent
import com.merxury.blocker.feature.search.RuleSearchResultContent
import com.merxury.blocker.feature.search.SearchScreenTabs
import com.merxury.blocker.feature.search.model.LocalSearchUiState.Success

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
fun SearchResultScreen(
    modifier: Modifier,
    tabState: TabState<SearchScreenTabs>,
    switchTab: (SearchScreenTabs) -> Unit,
    localSearchUiState: Success,
    switchSelectedMode: (Boolean) -> Unit,
    onSelect: (Boolean) -> Unit,
    navigateToAppDetail: (String, AppDetailTabs, List<String>) -> Unit = { _, _, _ -> },
    navigateToRuleDetail: (Int) -> Unit = {},
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SearchResultTabRow(tabState = tabState, switchTab = switchTab)
        when (tabState.currentIndex) {
            0 -> AppSearchResultContent(
                appList = localSearchUiState.appTabUiState.list,
                onClick = { packageName ->
                    navigateToAppDetail(packageName, AppDetailTabs.Info, listOf())
                    keyboardController?.hide()
                },
                onClearCacheClick = {},
                onClearDataClick = {},
                onForceStopClick = {},
                onUninstallClick = {},
                onEnableClick = {},
                onDisableClick = {},
                onServiceStateUpdate = { _, _ -> },
            )

            1 -> ComponentSearchResultContent(
                componentTabUiState = localSearchUiState.componentTabUiState,
                switchSelectedMode = switchSelectedMode,
                onSelect = onSelect,
                onComponentClick = { filterResult ->
                    val searchKeyword = localSearchUiState.searchKeyword
                    val firstTab = if (filterResult.receiver.isNotEmpty()) {
                        AppDetailTabs.Receiver()
                    } else if (filterResult.service.isNotEmpty()) {
                        AppDetailTabs.Service()
                    } else if (filterResult.activity.isNotEmpty()) {
                        AppDetailTabs.Activity()
                    } else if (filterResult.provider.isNotEmpty()) {
                        AppDetailTabs.Provider()
                    } else {
                        AppDetailTabs.Info
                    }
                    navigateToAppDetail(filterResult.app.packageName, firstTab, searchKeyword)
                },
            )

            2 -> RuleSearchResultContent(
                list = localSearchUiState.ruleTabUiState.list,
                onClick = navigateToRuleDetail,
            )
        }
    }
}

@Composable
fun SearchResultTabRow(
    tabState: TabState<SearchScreenTabs>,
    switchTab: (SearchScreenTabs) -> Unit,
) {
    BlockerScrollableTabRow(
        selectedTabIndex = tabState.currentIndex,
    ) {
        tabState.items.forEachIndexed { index, tabItem ->
            BlockerTab(
                selected = index == tabState.currentIndex,
                onClick = { switchTab(tabItem) },
                text = {
                    Text(
                        text = stringResource(
                            id = tabItem.title,
                            tabItem.itemCount,
                        ),
                    )
                },
            )
        }
    }
}
