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

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.bottomsheet.ModalBottomSheetValue
import com.merxury.blocker.core.designsystem.bottomsheet.ModalBottomSheetValue.Expanded
import com.merxury.blocker.core.designsystem.bottomsheet.ModalBottomSheetValue.HalfExpanded
import com.merxury.blocker.core.designsystem.bottomsheet.rememberModalBottomSheetState
import com.merxury.blocker.core.designsystem.component.BlockerModalBottomSheetLayout
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.applist.AppList
import com.merxury.blocker.core.ui.applist.model.AppItem
import com.merxury.blocker.core.ui.rule.GeneralRulesList
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.feature.search.component.ComponentSearchResultContent
import com.merxury.blocker.feature.search.component.FilteredComponentItem
import com.merxury.blocker.feature.search.component.SearchBar
import com.merxury.blocker.feature.search.model.ComponentTabUiState
import com.merxury.blocker.feature.search.model.FilteredComponentItem
import com.merxury.blocker.feature.search.model.LocalSearchUiState
import com.merxury.blocker.feature.search.model.SearchBoxUiState
import com.merxury.blocker.feature.search.model.SearchViewModel
import com.merxury.blocker.feature.search.screen.InitializingScreen
import com.merxury.blocker.feature.search.screen.NoSearchResultScreen
import com.merxury.blocker.feature.search.screen.SearchResultScreen
import com.merxury.blocker.feature.search.screen.SearchingScreen
import kotlinx.coroutines.launch

@Composable
fun SearchRoute(
    navigateToAppDetail: (String) -> Unit,
    navigateToComponentsDetail: () -> Unit,
    navigateToRuleDetail: (Int) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val searchBoxUiState by viewModel.searchBoxUiState.collectAsStateWithLifecycle()
    val localSearchUiState by viewModel.localSearchUiState.collectAsStateWithLifecycle()
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    val bottomSheetTabState by viewModel.bottomSheetTabState.collectAsStateWithLifecycle()

    SearchScreen(
        searchBoxUiState = searchBoxUiState,
        tabState = tabState,
        bottomSheetTabState = bottomSheetTabState,
        localSearchUiState = localSearchUiState,
        switchTab = viewModel::switchTab,
        switchBottomSheetTab = viewModel::switchBottomSheetTab,
        onSearchTextChanged = viewModel::search,
        onClearClick = viewModel::resetSearchState,
        onNavigationClick = { viewModel.switchSelectedMode(false) },
        onSelectAll = viewModel::selectAll,
        onBlockAll = viewModel::blockAll,
        onCheckAll = viewModel::checkAll,
        switchSelectedMode = viewModel::switchSelectedMode,
        onSelect = viewModel::selectItem,
        navigateToAppDetail = navigateToAppDetail,
        navigateToComponentsDetail = navigateToComponentsDetail,
        navigateToRuleDetail = navigateToRuleDetail,
        onComponentClick = viewModel::openComponentFilterResult,
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    tabState: TabState<SearchScreenTabs>,
    bottomSheetTabState: TabState<SearchScreenTabs>,
    searchBoxUiState: SearchBoxUiState,
    localSearchUiState: LocalSearchUiState,
    switchTab: (SearchScreenTabs) -> Unit,
    switchBottomSheetTab: (SearchScreenTabs) -> Unit,
    onSearchTextChanged: (TextFieldValue) -> Unit,
    onClearClick: () -> Unit,
    onNavigationClick: () -> Unit,
    onSelectAll: () -> Unit,
    onBlockAll: () -> Unit,
    onCheckAll: () -> Unit,
    switchSelectedMode: (Boolean) -> Unit,
    onSelect: (Boolean) -> Unit,
    navigateToAppDetail: (String) -> Unit = {},
    navigateToComponentsDetail: () -> Unit = {},
    navigateToRuleDetail: (Int) -> Unit = {},
    onComponentClick: (FilteredComponentItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        confirmStateChange = { it != HalfExpanded },
    )
    val coroutineScope = rememberCoroutineScope()

    BackHandler(sheetState.isVisible) {
        coroutineScope.launch { sheetState.hide() }
    }
    if (sheetState.targetValue == Expanded) {
        navigateToComponentsDetail()
        // TODO
    }
    BlockerModalBottomSheetLayout(
        sheetState = sheetState,
        sheetContent = {
            if (localSearchUiState is LocalSearchUiState.Success) {
                if (localSearchUiState.componentTabUiState.currentOpeningItem != null) {
                    ComponentSearchResultContent(
                        result = localSearchUiState.componentTabUiState.currentOpeningItem,
                        tabState = bottomSheetTabState,
                        switchTab = switchBottomSheetTab,
                    )
                }
            }
        },
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
                    is LocalSearchUiState.Error -> ErrorScreen(localSearchUiState.message)
                    is LocalSearchUiState.Initializing ->
                        InitializingScreen(localSearchUiState.processingName)

                    is LocalSearchUiState.Success -> SearchResultScreen(
                        modifier = modifier,
                        tabState = tabState,
                        switchTab = switchTab,
                        localSearchUiState = localSearchUiState,
                        switchSelectedMode = switchSelectedMode,
                        onSelect = onSelect,
                        coroutineScope = coroutineScope,
                        sheetState = sheetState,
                        navigateToAppDetail = navigateToAppDetail,
                        navigateToRuleDetail = navigateToRuleDetail,
                        onComponentClick = onComponentClick,
                    )
                }
            }
        }
    }
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
    hideBottomSheet: () -> Unit,
    onComponentClick: (FilteredComponentItem) -> Unit,
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
                    hideBottomSheet = hideBottomSheet,
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
    onClearCacheClick: (String) -> Unit,
    onClearDataClick: (String) -> Unit,
    onForceStopClick: (String) -> Unit,
    onUninstallClick: (String) -> Unit,
    onEnableClick: (String) -> Unit,
    onDisableClick: (String) -> Unit,
    onServiceStateUpdate: (String, Int) -> Unit,
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
    val bottomSheetTabState = TabState(
        items = listOf(
            SearchScreenTabs.Receiver(1),
            SearchScreenTabs.Service(),
        ),
        selectedItem = SearchScreenTabs.Service(),
    )
    BlockerTheme {
        SearchScreen(
            searchBoxUiState = searchBoxUiState,
            localSearchUiState = localSearchUiState,
            onSearchTextChanged = {},
            onClearClick = {},
            tabState = tabState,
            bottomSheetTabState = bottomSheetTabState,
            switchBottomSheetTab = {},
            switchTab = {},
            onNavigationClick = {},
            onSelectAll = {},
            onBlockAll = {},
            onCheckAll = {},
            switchSelectedMode = {},
            onSelect = {},
            navigateToComponentsDetail = {},
            onComponentClick = {},
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
    val bottomSheetTabState = TabState(
        items = listOf(
            SearchScreenTabs.Receiver(1),
            SearchScreenTabs.Service(),
        ),
        selectedItem = SearchScreenTabs.Service(),
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
            bottomSheetTabState = bottomSheetTabState,
            switchBottomSheetTab = {},
            onComponentClick = {},
        )
    }
}

@Composable
@Preview
fun SearchScreenPreview() {
    val filterAppItem = FilteredComponentItem(
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
    val bottomSheetTabState = TabState(
        items = listOf(
            SearchScreenTabs.Receiver(1),
            SearchScreenTabs.Service(),
        ),
        selectedItem = SearchScreenTabs.Service(),
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
            bottomSheetTabState = bottomSheetTabState,
            switchBottomSheetTab = {},
            onComponentClick = {},
        )
    }
}

@Composable
@Preview
fun SearchScreenSelectedPreview() {
    val filterAppItem = FilteredComponentItem(
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
    val bottomSheetTabState = TabState(
        items = listOf(
            SearchScreenTabs.Receiver(1),
            SearchScreenTabs.Service(),
        ),
        selectedItem = SearchScreenTabs.Service(),
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
            bottomSheetTabState = bottomSheetTabState,
            switchBottomSheetTab = {},
            onComponentClick = {},
        )
    }
}
