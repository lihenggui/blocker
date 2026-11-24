/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.feature.search.impl.component

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.analytics.LocalAnalyticsHelper
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.component.BlockerTabRow
import com.merxury.blocker.core.designsystem.component.scrollbar.DraggableScrollbar
import com.merxury.blocker.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.merxury.blocker.core.designsystem.component.scrollbar.scrollbarState
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.system.AppAction
import com.merxury.blocker.core.ui.applist.AppRow
import com.merxury.blocker.core.ui.applist.AppRowList
import com.merxury.blocker.core.ui.component.ComponentListItem
import com.merxury.blocker.core.ui.rule.GeneralRulesList
import com.merxury.blocker.core.ui.screen.EmptyScreen
import com.merxury.blocker.feature.search.api.R.string
import com.merxury.blocker.feature.search.impl.ComponentGroup
import com.merxury.blocker.feature.search.impl.FoundComponent
import com.merxury.blocker.feature.search.impl.SearchResults
import com.merxury.blocker.feature.search.impl.SearchTab
import com.merxury.blocker.feature.search.impl.SearchUiState
import com.merxury.blocker.feature.search.impl.logAppSearchResultClicked
import com.merxury.blocker.feature.search.impl.logComponentSearchResultClicked
import com.merxury.blocker.feature.search.impl.logRuleSearchResultClicked
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** What the results can ask for, one object for the life of the screen so unchanged rows are skipped. */
class SearchActions(
    val onSelectTab: (SearchTab) -> Unit = {},
    /** An app of the apps tab was tapped. */
    val onOpenApp: (AppRow) -> Unit = {},
    /** An action on an app is to run: one that needs it was confirmed already. */
    val onAppAction: (AppKey, AppAction) -> Unit = { _, _ -> },
    val countServices: suspend (AppKey) -> Unit = {},
    /** The header of an app's components was tapped outside selection mode. */
    val onOpenGroup: (ComponentGroup) -> Unit = {},
    /** A component was tapped outside selection mode. */
    val onOpenComponent: (FoundComponent) -> Unit = {},
    /** A component's switch was turned; true means on. */
    val onSwitch: (FoundComponent, Boolean) -> Unit = { _, _ -> },
    val onSelectionModeChange: (Boolean) -> Unit = {},
    val onToggleSelectedApp: (AppKey) -> Unit = {},
    val onRefreshComponents: () -> Unit = {},
    val onOpenRule: (String) -> Unit = {},
)

/** These actions, where each that opens a result runs [leave] first. */
internal fun SearchActions.leavingWith(leave: () -> Unit) = SearchActions(
    onSelectTab = onSelectTab,
    onOpenApp = {
        leave()
        onOpenApp(it)
    },
    onAppAction = onAppAction,
    countServices = countServices,
    onOpenGroup = {
        leave()
        onOpenGroup(it)
    },
    onOpenComponent = {
        leave()
        onOpenComponent(it)
    },
    onSwitch = onSwitch,
    onSelectionModeChange = onSelectionModeChange,
    onToggleSelectedApp = onToggleSelectedApp,
    onRefreshComponents = onRefreshComponents,
    onOpenRule = {
        leave()
        onOpenRule(it)
    },
)

/**
 * The tabs of the results, which the pages follow when swiped and the other way round.
 *
 * @param onAppAction asks for an action from an app's menu, which confirms it first if needed
 */
@Composable
internal fun SearchResultTabs(
    uiState: SearchUiState,
    results: SearchResults.Loaded,
    actions: SearchActions,
    onAppAction: (AppRow, AppAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = uiState.tabs
    val pagerState = rememberPagerState(initialPage = tabs.currentIndex) { tabs.items.size }
    val scope = rememberCoroutineScope()
    val onSelectTab by rememberUpdatedState(actions.onSelectTab)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { onSelectTab(tabs.items[it]) }
    }
    Column(modifier = modifier.fillMaxSize()) {
        BlockerTabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.items.forEachIndexed { index, tab ->
                BlockerTab(
                    selected = index == pagerState.currentPage,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(text = tab.title(tabs.itemCount[tab] ?: 0)) },
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.testTag("search:pager")) { page ->
            when (tabs.items[page]) {
                SearchTab.APPS -> AppResults(results, actions, onAppAction)
                SearchTab.COMPONENTS -> ComponentResults(uiState, results.components, actions)
                SearchTab.RULES -> RuleResults(results, actions)
            }
        }
    }
}

@Composable
private fun AppResults(results: SearchResults.Loaded, actions: SearchActions, onAppAction: (AppRow, AppAction) -> Unit) {
    if (results.apps.isEmpty()) {
        EmptyScreen(textRes = string.feature_search_api_no_search_result)
        return
    }
    val analyticsHelper = LocalAnalyticsHelper.current
    AppRowList(
        apps = results.apps,
        onAppClick = { app ->
            analyticsHelper.logAppSearchResultClicked()
            actions.onOpenApp(app)
        },
        onAction = onAppAction,
        countServices = actions.countServices.takeIf { results.showServiceInfo },
        modifier = Modifier.testTag("search:appList"),
    )
}

/**
 * The components found, by app, each with its switch. A thin bar shows while the components of
 * every app are still being read; the rows found so far show meanwhile. Pulling down reads every
 * app again.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ComponentResults(uiState: SearchUiState, groups: List<ComponentGroup>, actions: SearchActions) {
    val refreshState = rememberPullRefreshState(refreshing = false, onRefresh = actions.onRefreshComponents)
    Column(modifier = Modifier.fillMaxSize()) {
        uiState.indexing?.let { IndexingBar(it) }
        Box(modifier = Modifier.fillMaxSize().pullRefresh(refreshState)) {
            if (groups.isEmpty()) {
                // Scrollable, so the empty page takes the pull too.
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { EmptyScreen(textRes = string.feature_search_api_no_search_result, modifier = Modifier.fillParentMaxSize()) }
                }
            } else {
                ComponentGroupList(groups, uiState, actions)
            }
            PullRefreshIndicator(refreshing = false, state = refreshState, modifier = Modifier.align(Alignment.TopCenter), scale = true)
        }
    }
}

/**
 * Each app found with its components below it. Selection mode selects whole apps, as a long press
 * on the app or one of its components starts it.
 */
@Composable
private fun ComponentGroupList(groups: List<ComponentGroup>, uiState: SearchUiState, actions: SearchActions) {
    val analyticsHelper = LocalAnalyticsHelper.current
    val selecting = uiState.isSelectionMode
    val listState = rememberLazyListState()
    val itemCount = groups.sumOf { it.components.size + 1 }
    val scrollbarState = listState.scrollbarState(itemsAvailable = itemCount)
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.testTag("search:componentList"), state = listState) {
            groups.forEach { group ->
                val packageName = group.app.packageName
                val selected = group.components.isNotEmpty() && group.components.all { it.id in uiState.selected }
                val startSelection = {
                    actions.onSelectionModeChange(true)
                    actions.onToggleSelectedApp(group.app)
                }
                item(key = packageName) {
                    ComponentGroupHeader(
                        group = group,
                        selected = selected,
                        isSelectionMode = selecting,
                        onClick = {
                            if (selecting) {
                                actions.onToggleSelectedApp(group.app)
                            } else {
                                analyticsHelper.logComponentSearchResultClicked()
                                actions.onOpenGroup(group)
                            }
                        },
                        onLongClick = startSelection,
                    )
                }
                items(group.components, key = { "$packageName/${it.id.className}" }) { component ->
                    ComponentListItem(
                        simpleName = component.id.simpleName,
                        name = component.id.className,
                        enabled = component.enabled,
                        type = component.type,
                        isServiceRunning = false,
                        modifier = Modifier.testTag("search:component:${component.id.className}"),
                        inFlight = component.inFlight,
                        onClick = {
                            analyticsHelper.logComponentSearchResultClicked()
                            actions.onOpenComponent(component)
                        },
                        onLongClick = { if (!selecting) startSelection() },
                        onSwitchClick = { actions.onSwitch(component, it) },
                        isSelected = selected,
                        isSelectedMode = selecting,
                        onSelectionToggle = { actions.onToggleSelectedApp(group.app) },
                    )
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing)) }
        }
        listState.DraggableScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd),
            state = scrollbarState,
            orientation = Orientation.Vertical,
            onThumbMove = listState.rememberDraggableScroller(itemsAvailable = itemCount),
        )
    }
}

@Composable
private fun RuleResults(results: SearchResults.Loaded, actions: SearchActions) {
    // As before: a search that matches no rule of an installed app shows nothing.
    if (results.matchedRules.isEmpty()) {
        EmptyScreen(textRes = string.feature_search_api_no_search_result)
        return
    }
    val analyticsHelper = LocalAnalyticsHelper.current
    GeneralRulesList(
        modifier = Modifier.fillMaxSize(),
        matchedRules = results.matchedRules,
        unmatchedRules = results.unmatchedRules,
        onClick = { id ->
            analyticsHelper.logRuleSearchResultClicked(id)
            actions.onOpenRule(id)
        },
    )
}
