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

package com.merxury.blocker.feature.search

import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.core.ui.applist.AppList
import com.merxury.blocker.core.ui.applist.model.AppItem
import com.merxury.blocker.core.ui.rule.GeneralRulesList
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.InitializingScreen
import com.merxury.blocker.feature.applist.AppListViewModel
import com.merxury.blocker.feature.search.component.FilteredComponentItem
import com.merxury.blocker.feature.search.component.SearchBar
import com.merxury.blocker.feature.search.model.ComponentTabUiState
import com.merxury.blocker.feature.search.model.FilteredComponent
import com.merxury.blocker.feature.search.model.LocalSearchUiState
import com.merxury.blocker.feature.search.model.SearchBoxUiState
import com.merxury.blocker.feature.search.model.SearchViewModel
import com.merxury.blocker.feature.search.screen.NoSearchResultScreen
import com.merxury.blocker.feature.search.screen.SearchResultScreen
import com.merxury.blocker.feature.search.screen.SearchingScreen

@Composable
fun SearchRoute(
    navigateToAppDetail: (String, AppDetailTabs, List<String>) -> Unit = { _, _, _ -> },
    navigateToRuleDetail: (Int) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    appListViewModel: AppListViewModel = hiltViewModel(),
) {
    val searchBoxUiState by viewModel.searchBoxUiState.collectAsStateWithLifecycle()
    val localSearchUiState by viewModel.localSearchUiState.collectAsStateWithLifecycle()
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    val appList = appListViewModel.appListFlow.collectAsState()

    SearchScreen(
        searchBoxUiState = searchBoxUiState,
        tabState = tabState,
        localSearchUiState = localSearchUiState,
        switchTab = viewModel::switchTab,
        onSearchTextChanged = { keyword ->
            viewModel.search(keyword)
            appListViewModel.filter(keyword.text)
        },
        onClearClick = viewModel::resetSearchState,
        onNavigationClick = { viewModel.switchSelectedMode(false) },
        onSelectAll = viewModel::selectAll,
        onBlockAll = viewModel::blockAll,
        onCheckAll = viewModel::checkAll,
        switchSelectedMode = viewModel::switchSelectedMode,
        onSelect = viewModel::selectItem,
        navigateToAppDetail = navigateToAppDetail,
        navigateToRuleDetail = navigateToRuleDetail,
        appList = appList.value,
        onClearCacheClick = appListViewModel::clearCache,
        onClearDataClick = appListViewModel::clearData,
        onForceStopClick = appListViewModel::forceStop,
        onUninstallClick = appListViewModel::uninstall,
        onEnableClick = appListViewModel::enable,
        onDisableClick = appListViewModel::disable,
        onServiceStateUpdate = appListViewModel::updateServiceStatus,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    tabState: TabState<SearchScreenTabs>,
    searchBoxUiState: SearchBoxUiState,
    localSearchUiState: LocalSearchUiState,
    switchTab: (SearchScreenTabs) -> Unit,
    onSearchTextChanged: (TextFieldValue) -> Unit,
    onClearClick: () -> Unit,
    onNavigationClick: () -> Unit,
    onSelectAll: () -> Unit,
    onBlockAll: () -> Unit,
    onCheckAll: () -> Unit,
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
    Scaffold(
        topBar = {
            TopBar(
                localSearchUiState = localSearchUiState,
                searchBoxUiState = searchBoxUiState,
                onSearchTextChanged = onSearchTextChanged,
                onClearClick = onClearClick,
                onNavigationClick = onNavigationClick,
                onSelectAll = onSelectAll,
                onBlockAll = onBlockAll,
                onCheckAll = onCheckAll,
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .consumeWindowInsets(padding)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal,
                    ),
                ),
        ) {
            when (localSearchUiState) {
                LocalSearchUiState.Idle -> NoSearchResultScreen()
                LocalSearchUiState.Loading -> SearchingScreen()
                is LocalSearchUiState.Error -> ErrorScreen(localSearchUiState.uiMessage)
                is LocalSearchUiState.Initializing ->
                    InitializingScreen(localSearchUiState.processingName)

                is LocalSearchUiState.Success -> SearchResultScreen(
                    modifier = modifier,
                    tabState = tabState,
                    switchTab = switchTab,
                    localSearchUiState = localSearchUiState,
                    switchSelectedMode = switchSelectedMode,
                    onSelect = onSelect,
                    navigateToAppDetail = navigateToAppDetail,
                    navigateToRuleDetail = navigateToRuleDetail,
                    appList = appList,
                    onClearCacheClick = onClearCacheClick,
                    onClearDataClick = onClearDataClick,
                    onForceStopClick = onForceStopClick,
                    onUninstallClick = onUninstallClick,
                    onEnableClick = onEnableClick,
                    onDisableClick = onDisableClick,
                    onServiceStateUpdate = onServiceStateUpdate,
                )
            }
        }
    }
    TrackScreenViewEvent(screenName = "SearchScreen")
}

@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    localSearchUiState: LocalSearchUiState,
    searchBoxUiState: SearchBoxUiState,
    onSearchTextChanged: (TextFieldValue) -> Unit,
    onClearClick: () -> Unit,
    onNavigationClick: () -> Unit,
    onSelectAll: () -> Unit,
    onBlockAll: () -> Unit,
    onCheckAll: () -> Unit,
) {
    if (false) {
        // TODO Implement multi-select feature
//        SelectedAppTopBar(
//            localSearchUiState.selectedAppCount,
//            onNavigationClick = onNavigationClick,
//            onSelectAll = onSelectAll,
//            onBlockAll = onBlockAll,
//            onCheckAll = onCheckAll,
//        )
    } else {
        SearchBar(
            modifier = modifier,
            uiState = searchBoxUiState,
            onSearchTextChanged = onSearchTextChanged,
            onClearClick = onClearClick,
        )
    }
}

@Composable
fun ComponentSearchResultContent(
    modifier: Modifier = Modifier,
    componentTabUiState: ComponentTabUiState,
    switchSelectedMode: (Boolean) -> Unit,
    onSelect: (Boolean) -> Unit,
    onComponentClick: (FilteredComponent) -> Unit,
) {
    if (componentTabUiState.list.isEmpty()) {
        NoSearchResultScreen()
        return
    }
    val listState = rememberLazyListState()
    Box(modifier) {
        LazyColumn(
            modifier = modifier,
            state = listState,
        ) {
            items(componentTabUiState.list, key = { it.app.packageName }) {
                FilteredComponentItem(
                    items = it,
                    isSelectedMode = componentTabUiState.isSelectedMode,
                    switchSelectedMode = switchSelectedMode,
                    onSelect = onSelect,
                    onComponentClick = onComponentClick,
                )
            }
        }
    }
}

@Composable
fun AppSearchResultContent(
    modifier: Modifier = Modifier,
    appList: List<AppItem>,
    onClick: (String) -> Unit,
    onClearCacheClick: (String) -> Unit = { },
    onClearDataClick: (String) -> Unit = { },
    onForceStopClick: (String) -> Unit = { },
    onUninstallClick: (String) -> Unit = { },
    onEnableClick: (String) -> Unit = { },
    onDisableClick: (String) -> Unit = { },
    onServiceStateUpdate: (String, Int) -> Unit = { _, _ -> },
) {
    if (appList.isEmpty()) {
        NoSearchResultScreen()
        return
    }
    AppList(
        appList = appList,
        onAppItemClick = onClick,
        onClearCacheClick = onClearCacheClick,
        onClearDataClick = onClearDataClick,
        onForceStopClick = onForceStopClick,
        onUninstallClick = onUninstallClick,
        onEnableClick = onEnableClick,
        onDisableClick = onDisableClick,
        onServiceStateUpdate = onServiceStateUpdate,
        modifier = modifier,
    )
}

@Composable
fun RuleSearchResultContent(
    modifier: Modifier = Modifier,
    list: List<GeneralRule>,
    onClick: (Int) -> Unit,
) {
    if (list.isEmpty()) {
        NoSearchResultScreen()
        return
    }
    GeneralRulesList(
        modifier = modifier,
        rules = list,
        onClick = onClick,
    )
}

@Composable
@Preview
fun SearchScreenEmptyPreview() {
    val searchBoxUiState = SearchBoxUiState()
    val localSearchUiState = LocalSearchUiState.Loading
    val tabState = TabState(
        items = listOf(
            SearchScreenTabs.App(),
            SearchScreenTabs.Component(),
            SearchScreenTabs.Rule(),
        ),
        selectedItem = SearchScreenTabs.App(),
    )
    BlockerTheme {
        SearchScreen(
            searchBoxUiState = searchBoxUiState,
            localSearchUiState = localSearchUiState,
            onSearchTextChanged = {},
            onClearClick = {},
            tabState = tabState,
            switchTab = {},
            onNavigationClick = {},
            onSelectAll = {},
            onBlockAll = {},
            onCheckAll = {},
            switchSelectedMode = {},
            onSelect = {},
        )
    }
}

@Composable
@Preview
fun SearchScreenNoResultPreview() {
    val searchBoxUiState = SearchBoxUiState()
    val localSearchUiState = LocalSearchUiState.Idle
    val tabState = TabState(
        items = listOf(
            SearchScreenTabs.App(),
            SearchScreenTabs.Component(),
            SearchScreenTabs.Rule(),
        ),
        selectedItem = SearchScreenTabs.App(),
    )
    BlockerTheme {
        SearchScreen(
            searchBoxUiState = searchBoxUiState,
            localSearchUiState = localSearchUiState,
            onSearchTextChanged = {},
            onClearClick = {},
            tabState = tabState,
            switchTab = {},
            onNavigationClick = {},
            onSelectAll = {},
            onBlockAll = {},
            onCheckAll = {},
            switchSelectedMode = {},
            onSelect = {},
        )
    }
}

@Composable
@Preview
fun SearchScreenPreview() {
    val filterAppItem = FilteredComponent(
        app = AppItem(
            packageName = "com.merxury.blocker",
            label = "Blocker",
            isSystem = false,
        ),
    )
    val searchBoxUiState = SearchBoxUiState()
    val localSearchUiState = LocalSearchUiState.Success(
        componentTabUiState = ComponentTabUiState(list = listOf(filterAppItem)),
    )
    val tabState = TabState(
        items = listOf(
            SearchScreenTabs.App(),
            SearchScreenTabs.Component(),
            SearchScreenTabs.Rule(),
        ),
        selectedItem = SearchScreenTabs.App(),
    )
    BlockerTheme {
        SearchScreen(
            searchBoxUiState = searchBoxUiState,
            localSearchUiState = localSearchUiState,
            onSearchTextChanged = {},
            onClearClick = {},
            tabState = tabState,
            switchTab = {},
            onNavigationClick = {},
            onSelectAll = {},
            onBlockAll = {},
            onCheckAll = {},
            switchSelectedMode = {},
            onSelect = {},
        )
    }
}

@Composable
@Preview
fun SearchScreenSelectedPreview() {
    val filterAppItem = FilteredComponent(
        app = AppItem(
            packageName = "com.merxury.blocker",
            label = "Blocker",
            isSystem = false,
        ),
        isSelected = true,
    )
    val searchBoxUiState = SearchBoxUiState()
    val localSearchUiState = LocalSearchUiState.Success(
        componentTabUiState = ComponentTabUiState(list = listOf(filterAppItem)),
    )
    val tabState = TabState(
        items = listOf(
            SearchScreenTabs.App(),
            SearchScreenTabs.Component(),
            SearchScreenTabs.Rule(),
        ),
        selectedItem = SearchScreenTabs.App(),
    )
    BlockerTheme {
        SearchScreen(
            searchBoxUiState = searchBoxUiState,
            localSearchUiState = localSearchUiState,
            onSearchTextChanged = {},
            onClearClick = {},
            tabState = tabState,
            switchTab = {},
            onNavigationClick = {},
            onSelectAll = {},
            onBlockAll = {},
            onCheckAll = {},
            switchSelectedMode = {},
            onSelect = {},
        )
    }
}
