/*
 * Copyright 2025 Blocker
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

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation.Vertical
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarDuration.Short
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.analytics.LocalAnalyticsHelper
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.component.scrollbar.DraggableScrollbar
import com.merxury.blocker.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.merxury.blocker.core.designsystem.component.scrollbar.scrollbarState
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.FilteredComponent
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.PreviewDevices
import com.merxury.blocker.core.ui.SearchScreenTabs
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.core.ui.applist.AppList
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.previewparameter.AppListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.SearchTabStatePreviewParameterProvider
import com.merxury.blocker.core.ui.rule.GeneralRulesList
import com.merxury.blocker.core.ui.screen.EmptyScreen
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.InitializingScreen
import com.merxury.blocker.core.ui.topbar.SelectedAppTopBar
import com.merxury.blocker.feature.search.LocalSearchUiState.Error
import com.merxury.blocker.feature.search.LocalSearchUiState.Idle
import com.merxury.blocker.feature.search.LocalSearchUiState.Initializing
import com.merxury.blocker.feature.search.LocalSearchUiState.Loading
import com.merxury.blocker.feature.search.LocalSearchUiState.Success
import com.merxury.blocker.feature.search.R.string
import com.merxury.blocker.feature.search.component.FilteredComponentItem
import com.merxury.blocker.feature.search.component.SearchBar
import com.merxury.blocker.feature.search.screen.SearchResultScreen
import com.merxury.blocker.feature.search.screen.SearchingScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.merxury.blocker.core.ui.R.string as uistring

@Composable
fun SearchScreen(
    snackbarHostState: SnackbarHostState,
    highlightSelectedItem: Boolean = false,
    navigateToAppDetail: (String, String, List<String>) -> Unit = { _, _, _ -> },
    navigateToRuleDetail: (String) -> Unit = { _ -> },
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val localSearchUiState by viewModel.localSearchUiState.collectAsStateWithLifecycle()
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    val selectUiState by viewModel.searchUiState.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()
    val warningState by viewModel.warningState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SearchScreen(
        tabState = tabState,
        localSearchUiState = localSearchUiState,
        switchTab = viewModel::switchTab,
        onSearchTrigger = { keyword ->
            viewModel.search(keyword)
        },
        onSearchQueryChange = { keyword ->
            viewModel.search(keyword)
        },
        onSelectAll = viewModel::selectAll,
        onDeselect = viewModel::deselectItem,
        onBlockAll = {
            handleBlockAllClick(context, viewModel, scope, snackbarHostState)
        },
        onEnableAll = {
            handleEnableAppClick(context, viewModel, scope, snackbarHostState)
        },
        searchUiState = selectUiState,
        highlightSelectedItem = highlightSelectedItem,
        switchSelectedMode = viewModel::switchSelectedMode,
        onSelect = viewModel::selectItem,
        navigateToAppDetail = navigateToAppDetail,
        onAppClick = viewModel::onAppClick,
        onComponentClick = viewModel::onComponentClick,
        navigateToRuleDetail = {
            viewModel.onRuleClick(it)
            navigateToRuleDetail(it)
        },
        onClearCacheClick = viewModel::clearCache,
        onClearDataClick = viewModel::clearData,
        onForceStopClick = viewModel::forceStop,
        onUninstallClick = viewModel::uninstall,
        onEnableClick = viewModel::enable,
        onDisableClick = viewModel::disable,
    )
    if (errorState != null) {
        BlockerErrorAlertDialog(
            title = errorState?.title.orEmpty(),
            text = errorState?.content.orEmpty(),
            onDismissRequest = viewModel::dismissAlert,
        )
    }
    warningState?.let {
        BlockerWarningAlertDialog(
            title = it.title,
            text = stringResource(id = it.message),
            onDismissRequest = viewModel::dismissWarningDialog,
            onConfirmRequest = it.onPositiveButtonClicked,
        )
    }
}

private fun handleEnableAppClick(
    context: Context,
    viewModel: SearchViewModel,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    val doneMessage = context.getString(uistring.core_ui_operation_completed)
    viewModel.controlAllSelectedComponents(true) { current, total ->
        scope.launch {
            if (current == total) {
                snackbarHostState.showSnackbarWithoutQueue(
                    message = doneMessage,
                    duration = Short,
                    withDismissAction = true,
                )
            } else {
                snackbarHostState.showSnackbarWithoutQueue(
                    message = context.getString(
                        uistring.core_ui_enabling_component_hint,
                        current,
                        total,
                    ),
                    duration = Short,
                    withDismissAction = false,
                )
            }
        }
    }
}

private fun handleBlockAllClick(
    context: Context,
    viewModel: SearchViewModel,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    val doneMessage = context.getString(uistring.core_ui_operation_completed)
    viewModel.controlAllSelectedComponents(false) { current, total ->
        scope.launch {
            if (current == total) {
                snackbarHostState.showSnackbarWithoutQueue(
                    message = doneMessage,
                    duration = Short,
                    withDismissAction = true,
                )
            } else {
                snackbarHostState.showSnackbarWithoutQueue(
                    message = context.getString(
                        uistring.core_ui_disabling_component_hint,
                        current,
                        total,
                    ),
                    duration = Short,
                    withDismissAction = false,
                )
            }
        }
    }
}

@Composable
fun SearchScreen(
    tabState: TabState<SearchScreenTabs>,
    localSearchUiState: LocalSearchUiState,
    searchUiState: SearchUiState,
    modifier: Modifier = Modifier,
    highlightSelectedItem: Boolean = false,
    onAppClick: (String) -> Unit = { },
    onComponentClick: (String) -> Unit = { },
    switchTab: (SearchScreenTabs) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchTrigger: (String) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onBlockAll: () -> Unit = {},
    onEnableAll: () -> Unit = {},
    switchSelectedMode: (Boolean) -> Unit = {},
    onSelect: (FilteredComponent) -> Unit = {},
    onDeselect: (FilteredComponent) -> Unit = {},
    navigateToAppDetail: (String, String, List<String>) -> Unit = { _, _, _ -> },
    navigateToRuleDetail: (String) -> Unit = { },
    onClearCacheClick: (String) -> Unit = { },
    onClearDataClick: (String) -> Unit = { },
    onForceStopClick: (String) -> Unit = { },
    onUninstallClick: (String) -> Unit = { },
    onEnableClick: (String) -> Unit = { },
    onDisableClick: (String) -> Unit = { },
) {
    Column(modifier = modifier) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
        TopBar(
            searchUiState = searchUiState,
            onSearchQueryChange = onSearchQueryChange,
            onSearchTrigger = onSearchTrigger,
            onNavigationClick = { switchSelectedMode(false) },
            onSelectAll = onSelectAll,
            onBlockAll = onBlockAll,
            onEnableAll = onEnableAll,
            modifier = Modifier.testTag("blockerTopAppBar"),
        )
        when (localSearchUiState) {
            is Idle -> EmptyScreen(textRes = string.feature_search_no_search_result)
            is Loading -> SearchingScreen()
            is Error -> ErrorScreen(localSearchUiState.uiMessage)
            is Initializing ->
                InitializingScreen(localSearchUiState.processingName)

            is Success -> SearchResultScreen(
                tabState = tabState,
                highlightSelectedItem = highlightSelectedItem,
                switchTab = switchTab,
                localSearchUiState = localSearchUiState,
                searchUiState = searchUiState,
                switchSelectedMode = switchSelectedMode,
                onSelect = onSelect,
                onDeselect = onDeselect,
                onAppClick = onAppClick,
                onComponentClick = onComponentClick,
                navigateToAppDetail = navigateToAppDetail,
                navigateToRuleDetail = navigateToRuleDetail,
                appList = localSearchUiState.appTabUiState.list,
                onClearCacheClick = onClearCacheClick,
                onClearDataClick = onClearDataClick,
                onForceStopClick = onForceStopClick,
                onUninstallClick = onUninstallClick,
                onEnableClick = onEnableClick,
                onDisableClick = onDisableClick,
            )
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
    }

    BackHandler(enabled = searchUiState.isSelectedMode) {
        switchSelectedMode(false)
    }
    TrackScreenViewEvent(screenName = "SearchScreen")
}

@Composable
fun TopBar(
    searchUiState: SearchUiState,
    modifier: Modifier = Modifier,
    onSearchQueryChange: (String) -> Unit = { _ -> },
    onSearchTrigger: (String) -> Unit = { _ -> },
    onNavigationClick: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onBlockAll: () -> Unit = {},
    onEnableAll: () -> Unit = {},
) {
    Crossfade(
        searchUiState.isSelectedMode,
        animationSpec = tween(500),
        label = "topBar",
        modifier = modifier,
    ) { targetState ->
        if (targetState) {
            SelectedAppTopBar(
                title = R.plurals.feature_search_selected_app_count,
                selectedItemCount = searchUiState.selectedAppList.size,
                selectedComponentCount = searchUiState.selectedComponentList.size,
                onNavigationClick = onNavigationClick,
                onSelectAll = onSelectAll,
                onBlockAllSelectedComponents = onBlockAll,
                onEnableAllSelectedComponents = onEnableAll,
            )
        } else {
            SearchBar(
                searchQuery = searchUiState.keyword,
                onSearchQueryChange = onSearchQueryChange,
                onSearchTrigger = onSearchTrigger,
            )
        }
    }
}

@Composable
fun ComponentSearchResultContent(
    searchUiState: SearchUiState,
    componentTabUiState: ComponentTabUiState,
    modifier: Modifier = Modifier,
    highlightSelectedApp: Boolean = false,
    switchSelectedMode: (Boolean) -> Unit = {},
    onSelect: (FilteredComponent) -> Unit = {},
    onDeselect: (FilteredComponent) -> Unit = {},
    onComponentClick: (FilteredComponent) -> Unit = {},
) {
    if (componentTabUiState.list.isEmpty()) {
        EmptyScreen(textRes = string.feature_search_no_search_result)
        return
    }
    val listState = rememberLazyListState()
    val scrollbarState = listState.scrollbarState(
        itemsAvailable = componentTabUiState.list.size,
    )
    val analyticsHelper = LocalAnalyticsHelper.current
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
        ) {
            items(componentTabUiState.list, key = { it.app.packageName }) {
                val isSelected =
                    highlightSelectedApp && it.app.packageName == componentTabUiState.selectedPackageName
                FilteredComponentItem(
                    items = it,
                    isSelectedMode = searchUiState.isSelectedMode,
                    isSelected = isSelected,
                    switchSelectedMode = switchSelectedMode,
                    onSelect = onSelect,
                    onDeselect = onDeselect,
                    onComponentClick = { component ->
                        onComponentClick(component)
                        analyticsHelper.logComponentSearchResultClicked()
                    },
                    isSelectedInSelectedMode = searchUiState.selectedAppList.contains(it),
                )
            }
            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
        listState.DraggableScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd),
            state = scrollbarState,
            orientation = Vertical,
            onThumbMove = listState.rememberDraggableScroller(
                itemsAvailable = componentTabUiState.list.size,
            ),
        )
    }
}

@Composable
fun AppSearchResultContent(
    appList: List<AppItem>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    highlightSelectedApp: Boolean = false,
    selectedPackageName: String? = null,
    onClearCacheClick: (String) -> Unit = { },
    onClearDataClick: (String) -> Unit = { },
    onForceStopClick: (String) -> Unit = { },
    onUninstallClick: (String) -> Unit = { },
    onEnableClick: (String) -> Unit = { },
    onDisableClick: (String) -> Unit = { },
) {
    if (appList.isEmpty()) {
        EmptyScreen(textRes = string.feature_search_no_search_result)
        return
    }
    val analyticsHelper = LocalAnalyticsHelper.current
    AppList(
        appList = appList,
        highlightSelectedApp = highlightSelectedApp,
        selectedPackageName = selectedPackageName,
        onAppItemClick = { packageName ->
            onClick(packageName)
            analyticsHelper.logAppSearchResultClicked()
        },
        onClearCacheClick = onClearCacheClick,
        onClearDataClick = onClearDataClick,
        onForceStopClick = onForceStopClick,
        onUninstallClick = onUninstallClick,
        onEnableClick = onEnableClick,
        onDisableClick = onDisableClick,
        modifier = modifier,
    )
}

@Composable
fun RuleSearchResultContent(
    matchedRules: List<GeneralRule>,
    unmatchedRules: List<GeneralRule>,
    modifier: Modifier = Modifier,
    highlightSelectedRule: Boolean = false,
    selectedRuleId: String? = null,
    onClick: (String) -> Unit = { _ -> },
) {
    if (matchedRules.isEmpty()) {
        EmptyScreen(textRes = string.feature_search_no_search_result)
        return
    }
    val analyticsHelper = LocalAnalyticsHelper.current
    GeneralRulesList(
        modifier = modifier.fillMaxSize(),
        highlightSelectedRule = highlightSelectedRule,
        selectedRuleId = selectedRuleId,
        matchedRules = matchedRules,
        unmatchedRules = unmatchedRules,
        onClick = { id ->
            onClick(id)
            analyticsHelper.logRuleSearchResultClicked(id)
        },
    )
}

@Composable
@PreviewThemes
private fun SearchScreenSelectedAppPreview() {
    val tabState = SearchTabStatePreviewParameterProvider().values.first()
    val appList = AppListPreviewParameterProvider().values.first()
    val keyword = "blocker"

    BlockerTheme {
        Surface {
            SearchScreen(
                localSearchUiState = Success(
                    searchKeyword = listOf(keyword),
                    appTabUiState = AppTabUiState(
                        list = appList,
                    ),
                ),
                tabState = tabState[0],
                searchUiState = SearchUiState(
                    keyword = keyword,
                ),
            )
        }
    }
}

@Composable
@PreviewDevices
private fun SearchScreenSelectedComponentPreview() {
    val tabState = SearchTabStatePreviewParameterProvider().values.first()
    val appList = AppListPreviewParameterProvider().values.first()
    val components = ComponentListPreviewParameterProvider().values.first()
    val keyword = "blocker"

    BlockerTheme {
        Surface {
            SearchScreen(
                localSearchUiState = Success(
                    searchKeyword = listOf(keyword),
                    componentTabUiState = ComponentTabUiState(
                        list = listOf(
                            FilteredComponent(
                                app = appList[0],
                                activity = components.filter { it.type == ACTIVITY },
                                receiver = components.filter { it.type == RECEIVER },
                            ),
                        ),
                    ),
                ),
                tabState = tabState[1],
                searchUiState = SearchUiState(
                    keyword = keyword,
                ),
            )
        }
    }
}

@Composable
@PreviewThemes
private fun SearchScreenSelectedRule() {
    val tabState = SearchTabStatePreviewParameterProvider().values.first()
    val ruleList = RuleListPreviewParameterProvider().values.first()
    val keyword = "blocker"

    BlockerTheme {
        Surface {
            SearchScreen(
                localSearchUiState = Success(
                    searchKeyword = listOf(keyword),
                    ruleTabUiState = RuleTabUiState(
                        matchedRules = ruleList,
                    ),
                ),
                tabState = tabState[2],
                searchUiState = SearchUiState(
                    keyword = keyword,
                ),
            )
        }
    }
}

@Composable
@PreviewThemes
@PreviewDevices
private fun SearchScreenSelectedModePreview() {
    val tabState = SearchTabStatePreviewParameterProvider().values.first()
    val appList = AppListPreviewParameterProvider().values.first()
    val components = ComponentListPreviewParameterProvider().values.first()
    val keyword = "blocker"

    BlockerTheme {
        Surface {
            SearchScreen(
                localSearchUiState = Success(
                    searchKeyword = listOf(keyword),
                    componentTabUiState = ComponentTabUiState(
                        list = listOf(
                            FilteredComponent(
                                app = appList[0],
                                activity = components.filter { it.type == ACTIVITY },
                                receiver = components.filter { it.type == RECEIVER },
                            ),
                        ),
                    ),
                ),
                tabState = tabState[1],
                searchUiState = SearchUiState(
                    keyword = keyword,
                    isSelectedMode = true,
                    selectedComponentList = listOf(
                        components[0],
                        components[1],
                    ),
                    selectedAppList = listOf(
                        FilteredComponent(
                            app = appList[0],
                            activity = components.filter { it.type == ACTIVITY },
                            receiver = components.filter { it.type == RECEIVER },
                        ),
                    ),
                ),
            )
        }
    }
}

@Composable
@Preview
private fun SearchScreenEmptyPreview() {
    val tabState = SearchTabStatePreviewParameterProvider().values.first()

    BlockerTheme {
        Surface {
            SearchScreen(
                localSearchUiState = Idle,
                tabState = tabState[0],
                searchUiState = SearchUiState(),
            )
        }
    }
}

@Composable
@Preview
private fun SearchScreenNoResultPreview() {
    val tabState = SearchTabStatePreviewParameterProvider().values.first()
    val keyword = "blocker"

    BlockerTheme {
        Surface {
            SearchScreen(
                localSearchUiState = Success(
                    searchKeyword = listOf(keyword),
                    appTabUiState = AppTabUiState(
                        list = emptyList(),
                    ),
                ),
                tabState = tabState[3],
                searchUiState = SearchUiState(
                    keyword = keyword,
                ),
            )
        }
    }
}

@Composable
@Preview
private fun SearchScreenInitializingPreview() {
    val tabState = SearchTabStatePreviewParameterProvider().values.first()

    BlockerTheme {
        Surface {
            SearchScreen(
                localSearchUiState = Initializing("Blocker"),
                tabState = tabState[0],
                searchUiState = SearchUiState(),
            )
        }
    }
}

@Composable
@Preview
private fun SearchScreenLoadingPreview() {
    val tabState = SearchTabStatePreviewParameterProvider().values.first()

    BlockerTheme {
        Surface {
            SearchScreen(
                localSearchUiState = Loading,
                tabState = tabState[0],
                searchUiState = SearchUiState(),
            )
        }
    }
}

@Composable
@Preview
private fun SearchScreenErrorPreview() {
    val tabState = SearchTabStatePreviewParameterProvider().values.first()

    BlockerTheme {
        Surface {
            SearchScreen(
                localSearchUiState = Error(uiMessage = UiMessage("Error")),
                tabState = tabState[0],
                searchUiState = SearchUiState(),
            )
        }
    }
}
