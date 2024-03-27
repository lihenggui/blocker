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

package com.merxury.blocker.ui.twopane.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.feature.appdetail.navigation.APP_DETAIL_ROUTE
import com.merxury.blocker.feature.appdetail.navigation.appDetailScreen
import com.merxury.blocker.feature.appdetail.navigation.navigateToAppDetail
import com.merxury.blocker.feature.ruledetail.navigation.navigateToRuleDetail
import com.merxury.blocker.feature.ruledetail.navigation.ruleDetailScreen
import com.merxury.blocker.feature.search.SearchRoute
import com.merxury.blocker.feature.search.navigation.KEYWORD_ARG
import com.merxury.blocker.feature.search.navigation.PACKAGE_NAME_ARG
import com.merxury.blocker.feature.search.navigation.RULE_ID_ARG
import com.merxury.blocker.feature.search.navigation.SEARCH_LIST_APP_DETAIL_ROUTE
import com.merxury.blocker.feature.search.navigation.SEARCH_ROUTE_BASIC
import com.merxury.blocker.feature.search.navigation.TAB_ARG
import com.merxury.blocker.feature.search.screen.SearchDetailPlaceholder
import com.merxury.blocker.ui.twopane.calculateNoContentPaddingScaffoldDirective
import com.merxury.blocker.ui.twopane.isDetailPaneVisible
import com.merxury.blocker.ui.twopane.isListPaneVisible

private const val SEARCH_PANE_ROUTE = "search_pane_route"

fun NavGraphBuilder.searchListDetailScreen(
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToComponentSortScreen: () -> Unit,
) {
    composable(
        route = SEARCH_LIST_APP_DETAIL_ROUTE,
        arguments = listOf(
            navArgument(PACKAGE_NAME_ARG) {
                type = NavType.StringType
                defaultValue = null
                nullable = true
            },
            navArgument(KEYWORD_ARG) {
                type = NavType.StringType
                defaultValue = null
                nullable = true
            },
            navArgument(TAB_ARG) {
                type = NavType.StringType
                defaultValue = null
                nullable = true
            },
            navArgument(RULE_ID_ARG) {
                type = NavType.StringType
                defaultValue = null
                nullable = true
            }
        ),
    ) {
        SearchListDetailScreen(
            snackbarHostState = snackbarHostState,
            updateIconThemingState = updateIconThemingState,
            navigateToComponentDetail = navigateToComponentDetail,
            navigateToComponentSortScreen = navigateToComponentSortScreen,
        )
    }
}

@Composable
internal fun SearchListDetailScreen(
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToComponentSortScreen: () -> Unit,
    viewModel: Search2PaneViewModel = hiltViewModel(),
) {
    val isAppPage by viewModel.isAppPage.collectAsStateWithLifecycle()
    val selectedPackageName by viewModel.selectedPackageName.collectAsStateWithLifecycle()
    val searchKeyword by viewModel.searchKeyword.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedRuleId by viewModel.selectedRuleId.collectAsStateWithLifecycle()
    SearchListDetailScreen(
        snackbarHostState = snackbarHostState,
        isAppPage = isAppPage,
        selectedPackageName = selectedPackageName,
        selectedTab = AppDetailTabs.fromName(selectedTab),
        searchKeyword = searchKeyword?.split(",") ?: listOf(),
        selectedRuleId = selectedRuleId,
        onAppClick = viewModel::onAppClick,
        onRuleClick = viewModel::onRuleClick,
        updateIconThemingState = updateIconThemingState,
        navigateToComponentDetail = navigateToComponentDetail,
        navigateToComponentSortScreen = navigateToComponentSortScreen,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun SearchListDetailScreen(
    snackbarHostState: SnackbarHostState,
    isAppPage: Boolean,
    selectedPackageName: String? = null,
    selectedTab: AppDetailTabs? = null,
    selectedRuleId: String? = null,
    searchKeyword: List<String> = listOf(),
    onAppClick: (String, AppDetailTabs, List<String>) -> Unit = { _, _, _ -> },
    onRuleClick: (String) -> Unit,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToComponentSortScreen: () -> Unit,
) {
    val scaffoldDirective = calculateNoContentPaddingScaffoldDirective(
        currentWindowAdaptiveInfo(),
    )
    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator<Nothing>(
        scaffoldDirective = scaffoldDirective,
    )
    BackHandler(listDetailNavigator.canNavigateBack()) {
        listDetailNavigator.navigateBack()
    }

    val nestedNavController = rememberNavController()

    fun onAppClickShowDetailPane(
        packageName: String,
        tab: AppDetailTabs,
        searchKeyword: List<String> = listOf(),
    ) {
        onAppClick(packageName, tab, searchKeyword)
        nestedNavController.navigateToAppDetail(
            packageName = packageName,
            tab = tab,
            searchKeyword = searchKeyword,
            navOptions = {
                popUpTo(SEARCH_PANE_ROUTE)
            },
        )
        listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }

    fun onAppClickShowDetailPane(
        packageName: String,
    ) {
        onAppClick(packageName, AppDetailTabs.Info, listOf())
        nestedNavController.navigateToAppDetail(
            packageName = packageName,
            navOptions = {
                popUpTo(SEARCH_PANE_ROUTE)
            },
        )
        listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }

    fun onRuleClickShowDetailPane(ruleId: String) {
        onRuleClick(ruleId)
        nestedNavController.navigateToRuleDetail(
            ruleId = ruleId,
            navOptions = {
                popUpTo(SEARCH_PANE_ROUTE)
            },
        )
        listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }

    ListDetailPaneScaffold(
        value = listDetailNavigator.scaffoldValue,
        directive = listDetailNavigator.scaffoldDirective,
        listPane = {
            SearchRoute(
                snackbarHostState = snackbarHostState,
                navigateToAppDetail = ::onAppClickShowDetailPane,
                navigateToRuleDetail = ::onRuleClickShowDetailPane,
                highlightSelectedItem = listDetailNavigator.isDetailPaneVisible(),
            )
        },
        detailPane = {
            if (selectedPackageName == null && selectedRuleId == null) {
                SearchDetailPlaceholder()
            } else if (isAppPage) {
                NavHost(
                    navController = nestedNavController,
                    startDestination = APP_DETAIL_ROUTE,
                    route = SEARCH_PANE_ROUTE,
                ) {
                    appDetailScreen(
                        onBackClick = listDetailNavigator::navigateBack,
                        snackbarHostState = snackbarHostState,
                        navigateToComponentDetail = navigateToComponentDetail,
                        navigateToComponentSortScreen = navigateToComponentSortScreen,
                        navigateToRuleDetail = ::onRuleClickShowDetailPane,
                        updateIconThemingState = updateIconThemingState,
                        showBackButton = !listDetailNavigator.isListPaneVisible(),
                    )
                    composable(route = APP_DETAIL_ROUTE) {
                        Box {
                            Text("Search Detail")
                        }
                    }
                }
            } else {
                NavHost(
                    navController = nestedNavController,
                    startDestination = SEARCH_ROUTE_BASIC,
                    route = SEARCH_PANE_ROUTE,
                ) {
                    ruleDetailScreen(
                        onBackClick = listDetailNavigator::navigateBack,
                        snackbarHostState = snackbarHostState,
                        navigateToAppDetail = ::onAppClickShowDetailPane,
                        updateIconThemingState = updateIconThemingState,
                        showBackButton = !listDetailNavigator.isListPaneVisible(),
                    )
                    composable(route = SEARCH_ROUTE_BASIC) {
                        Box {
                            Text("Search Detail")
                        }
                    }
                }
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
    LaunchedEffect(Unit) {
        if (selectedPackageName != null) {
            // Initial packageName was provided when navigating to AppList, so show its details.
            onAppClickShowDetailPane(
                packageName = selectedPackageName,
                tab = selectedTab ?: AppDetailTabs.Info,
                searchKeyword = searchKeyword
            )
        } else if (selectedRuleId != null) {
            // Initial ruleId was provided when navigating to RuleList, so show its details.
            onRuleClickShowDetailPane(ruleId = selectedRuleId)
        }
    }
}
