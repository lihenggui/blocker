/*
 * Copyright 2024 Blocker
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.component.BlockerTabRow
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.FilteredComponent
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.AppDetailTabs.Activity
import com.merxury.blocker.core.ui.AppDetailTabs.Info
import com.merxury.blocker.core.ui.AppDetailTabs.Provider
import com.merxury.blocker.core.ui.AppDetailTabs.Receiver
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.SearchScreenTabs
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.feature.search.AppSearchResultContent
import com.merxury.blocker.feature.search.ComponentSearchResultContent
import com.merxury.blocker.feature.search.LocalSearchUiState.Success
import com.merxury.blocker.feature.search.RuleSearchResultContent
import com.merxury.blocker.feature.search.SearchUiState
import kotlinx.coroutines.launch

@Composable
fun SearchResultScreen(
    tabState: TabState<SearchScreenTabs>,
    switchTab: (SearchScreenTabs) -> Unit,
    localSearchUiState: Success,
    searchUiState: SearchUiState,
    highlightSelectedItem: Boolean,
    modifier: Modifier = Modifier,
    switchSelectedMode: (Boolean) -> Unit = { _ -> },
    onSelect: (FilteredComponent) -> Unit = { _ -> },
    onDeselect: (FilteredComponent) -> Unit = { _ -> },
    navigateToAppDetail: (String, AppDetailTabs, List<String>) -> Unit = { _, _, _ -> },
    onAppClick: (String) -> Unit = { },
    onComponentClick: (String) -> Unit = { },
    navigateToRuleDetail: (String) -> Unit = { },
    appList: List<AppItem> = emptyList(),
    onClearCacheClick: (String) -> Unit = { },
    onClearDataClick: (String) -> Unit = { },
    onForceStopClick: (String) -> Unit = { },
    onUninstallClick: (String) -> Unit = { },
    onEnableClick: (String) -> Unit = { },
    onDisableClick: (String) -> Unit = { },
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val pagerState = rememberPagerState(initialPage = tabState.currentIndex) { tabState.items.size }
    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BlockerTabRow(
            selectedTabIndex = pagerState.currentPage,
        ) {
            tabState.items.forEachIndexed { index, tabItem ->
                BlockerTab(
                    selected = index == pagerState.currentPage,
                    onClick = {
                        switchTab(tabItem)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
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
        HorizontalPager(state = pagerState) {
            when (it) {
                0 -> AppSearchResultContent(
                    appList = appList,
                    highlightSelectedApp = highlightSelectedItem,
                    selectedPackageName = localSearchUiState.appTabUiState.selectedPackageName,
                    onClick = { packageName ->
                        onAppClick(packageName)
                        navigateToAppDetail(packageName, Info, listOf())
                        keyboardController?.hide()
                    },
                    onClearCacheClick = onClearCacheClick,
                    onClearDataClick = onClearDataClick,
                    onForceStopClick = onForceStopClick,
                    onUninstallClick = onUninstallClick,
                    onEnableClick = onEnableClick,
                    onDisableClick = onDisableClick,
                )

                1 -> ComponentSearchResultContent(
                    componentTabUiState = localSearchUiState.componentTabUiState,
                    switchSelectedMode = switchSelectedMode,
                    searchUiState = searchUiState,
                    onSelect = onSelect,
                    onDeselect = onDeselect,
                    highlightSelectedApp = highlightSelectedItem,
                    onComponentClick = { filterResult ->
                        onComponentClick(filterResult.app.packageName)
                        val searchKeyword = localSearchUiState.searchKeyword
                        val firstTab = if (filterResult.receiver.isNotEmpty()) {
                            Receiver
                        } else if (filterResult.service.isNotEmpty()) {
                            Service
                        } else if (filterResult.activity.isNotEmpty()) {
                            Activity
                        } else if (filterResult.provider.isNotEmpty()) {
                            Provider
                        } else {
                            Info
                        }
                        navigateToAppDetail(filterResult.app.packageName, firstTab, searchKeyword)
                    },
                )

                2 -> RuleSearchResultContent(
                    matchedRules = localSearchUiState.ruleTabUiState.matchedRules,
                    unmatchedRules = localSearchUiState.ruleTabUiState.unmatchedRules,
                    highlightSelectedRule = highlightSelectedItem,
                    selectedRuleId = localSearchUiState.ruleTabUiState.selectedRuleId,
                    onClick = navigateToRuleDetail,
                )
            }
        }
    }
}
