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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.designsystem.component.BlockerScrollableTabRow
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.AppDetailTabs.Activity
import com.merxury.blocker.core.ui.AppDetailTabs.Info
import com.merxury.blocker.core.ui.AppDetailTabs.Provider
import com.merxury.blocker.core.ui.AppDetailTabs.Receiver
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.applist.model.AppItem
import com.merxury.blocker.feature.search.AppSearchResultContent
import com.merxury.blocker.feature.search.ComponentSearchResultContent
import com.merxury.blocker.feature.search.RuleSearchResultContent
import com.merxury.blocker.feature.search.SearchScreenTabs
import com.merxury.blocker.feature.search.model.LocalSearchUiState.Success
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
fun SearchResultScreen(
    modifier: Modifier,
    tabState: TabState<SearchScreenTabs>,
    switchTab: (SearchScreenTabs) -> Unit,
    localSearchUiState: Success,
    switchSelectedMode: (Boolean) -> Unit,
    onSelect: (Boolean) -> Unit,
    navigateToAppDetail: (String, AppDetailTabs, List<String>) -> Unit = { _, _, _ -> },
    navigateToRuleDetail: (Int) -> Unit = { },
    appList: List<AppItem> = emptyList(),
    onClearCacheClick: (String) -> Unit = { },
    onClearDataClick: (String) -> Unit = { },
    onForceStopClick: (String) -> Unit = { },
    onUninstallClick: (String) -> Unit = { },
    onEnableClick: (String) -> Unit = { },
    onDisableClick: (String) -> Unit = { },
    onServiceStateUpdate: (String, Int) -> Unit = { _, _ -> },
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val pagerState = rememberPagerState(initialPage = tabState.currentIndex)
    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BlockerScrollableTabRow(
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
        HorizontalPager(
            pageCount = tabState.items.size,
            state = pagerState,
        ) {
            when (pagerState.currentPage) {
                0 -> AppSearchResultContent(
                    appList = appList,
                    onClick = { packageName ->
                        navigateToAppDetail(packageName, Info, listOf())
                        keyboardController?.hide()
                    },
                    onClearCacheClick = onClearCacheClick,
                    onClearDataClick = onClearDataClick,
                    onForceStopClick = onForceStopClick,
                    onUninstallClick = onUninstallClick,
                    onEnableClick = onEnableClick,
                    onDisableClick = onDisableClick,
                    onServiceStateUpdate = onServiceStateUpdate,
                )

                1 -> ComponentSearchResultContent(
                    componentTabUiState = localSearchUiState.componentTabUiState,
                    switchSelectedMode = switchSelectedMode,
                    onSelect = onSelect,
                    onComponentClick = { filterResult ->
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
                    list = localSearchUiState.ruleTabUiState.list,
                    onClick = navigateToRuleDetail,
                )
            }
        }
    }
}
