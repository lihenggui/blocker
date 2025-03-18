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

package com.merxury.blocker.ui.twopane.search

import androidx.activity.compose.BackHandler
import androidx.annotation.Keep
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.feature.appdetail.navigation.AppDetailRoute
import com.merxury.blocker.feature.appdetail.navigation.appDetailScreen
import com.merxury.blocker.feature.appdetail.navigation.navigateToAppDetail
import com.merxury.blocker.feature.ruledetail.navigation.RuleDetailRoute
import com.merxury.blocker.feature.ruledetail.navigation.navigateToRuleDetail
import com.merxury.blocker.feature.ruledetail.navigation.ruleDetailScreen
import com.merxury.blocker.feature.search.SearchScreen
import com.merxury.blocker.feature.search.navigation.SearchRoute
import com.merxury.blocker.ui.twopane.isDetailPaneVisible
import com.merxury.blocker.ui.twopane.isListPaneVisible
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
internal object SearchPlaceholderRoute

@Keep
@Serializable
internal object SearchPaneNavHostRoute

fun NavGraphBuilder.searchListDetailScreen(
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToComponentSortScreen: () -> Unit,
) {
    composable<SearchRoute> {
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
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
) {
    val selectedPackageName by viewModel.selectedPackageName.collectAsStateWithLifecycle()
    val selectedAppTabs by viewModel.selectedAppTabs.collectAsStateWithLifecycle()
    val searchKeywords by viewModel.searchKeyword.collectAsStateWithLifecycle()
    val selectedRuleId by viewModel.selectedRuleId.collectAsStateWithLifecycle()
    val isAppDetailPage by viewModel.isAppDetailPage.collectAsStateWithLifecycle()
    SearchListDetailScreen(
        snackbarHostState = snackbarHostState,
        isAppDetailPage = isAppDetailPage,
        selectedPackageName = selectedPackageName,
        selectedTab = AppDetailTabs.fromName(selectedAppTabs),
        searchKeyword = listOf(searchKeywords ?: ""),
        selectedRuleId = selectedRuleId,
        onAppClick = viewModel::onAppClick,
        onRuleClick = viewModel::onRuleClick,
        updateIconThemingState = updateIconThemingState,
        navigateToComponentDetail = navigateToComponentDetail,
        navigateToComponentSortScreen = navigateToComponentSortScreen,
        windowAdaptiveInfo = windowAdaptiveInfo,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun SearchListDetailScreen(
    snackbarHostState: SnackbarHostState,
    isAppDetailPage: Boolean,
    windowAdaptiveInfo: WindowAdaptiveInfo,
    selectedPackageName: String? = null,
    selectedTab: AppDetailTabs? = null,
    selectedRuleId: String? = null,
    searchKeyword: List<String> = listOf(),
    onAppClick: (String, String, List<String>) -> Unit = { _, _, _ -> },
    onRuleClick: (String) -> Unit = {},
    updateIconThemingState: (IconThemingState) -> Unit = {},
    navigateToComponentDetail: (String) -> Unit = {},
    navigateToComponentSortScreen: () -> Unit = {},
) {
    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator(
        scaffoldDirective = calculatePaneScaffoldDirective(windowAdaptiveInfo),
        initialDestinationHistory = listOfNotNull(
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List),
            ThreePaneScaffoldDestinationItem<Nothing>(ListDetailPaneScaffoldRole.Detail).takeIf {
                selectedRuleId != null || selectedPackageName != null
            },
        ),
    )
    BackHandler(listDetailNavigator.canNavigateBack()) {
        listDetailNavigator.navigateBack()
    }

    var nestedNavHostStartRoute by remember {
        val route =
            if (selectedRuleId != null) RuleDetailRoute(ruleId = selectedRuleId) else if (selectedPackageName != null) SearchPlaceholderRoute else SearchPlaceholderRoute
        mutableStateOf(route)
    }
    var nestedNavKey by rememberSaveable(
        stateSaver = Saver({ it.toString() }, UUID::fromString),
    ) {
        mutableStateOf(UUID.randomUUID())
    }
    val nestedNavController = key(nestedNavKey) {
        rememberNavController()
    }

    fun onPackageNameClickShowDetailPane(
        packageName: String,
        tab: String,
        searchKeyword: List<String> = listOf(),
    ) {
        onAppClick(packageName, tab, searchKeyword)
        if (listDetailNavigator.isDetailPaneVisible()) {
            // If the detail pane was visible, then use the nestedNavController navigate call
            // directly
            nestedNavController.navigateToAppDetail(
                packageName = packageName,
                tab = tab,
                searchKeyword = searchKeyword,
            ) {
                popUpTo<SearchPlaceholderRoute>()
            }
        } else {
            // Otherwise, recreate the NavHost entirely, and start at the new destination
            nestedNavHostStartRoute = AppDetailRoute(
                packageName = packageName,
                tab = AppDetailTabs.Info.name,
                searchKeyword = searchKeyword,
            )
            nestedNavKey = UUID.randomUUID()
        }
        listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }

    fun onRuleClickShowDetailPane(ruleId: String) {
        onRuleClick(ruleId)
        if (listDetailNavigator.isDetailPaneVisible()) {
            // If the detail pane was visible, then use the nestedNavController navigate call
            // directly
            nestedNavController.navigateToRuleDetail(
                ruleId = ruleId,
            ) {
                popUpTo<SearchPlaceholderRoute>()
            }
        } else {
            // Otherwise, recreate the NavHost entirely, and start at the new destination
            nestedNavHostStartRoute = RuleDetailRoute(ruleId = ruleId)
            nestedNavKey = UUID.randomUUID()
        }
        listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }

    ListDetailPaneScaffold(
        value = listDetailNavigator.scaffoldValue,
        directive = listDetailNavigator.scaffoldDirective,
        listPane = {
            AnimatedPane {
                SearchScreen(
                    snackbarHostState = snackbarHostState,
                    navigateToAppDetail = ::onPackageNameClickShowDetailPane,
                    navigateToRuleDetail = ::onRuleClickShowDetailPane,
                    highlightSelectedItem = listDetailNavigator.isDetailPaneVisible(),
                )
            }
        },
        detailPane = {
            AnimatedPane {
                key(nestedNavKey) {
                    NavHost(
                        navController = nestedNavController,
                        startDestination = nestedNavHostStartRoute,
                        route = SearchPaneNavHostRoute::class,
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
                        ruleDetailScreen(
                            showBackButton = !listDetailNavigator.isListPaneVisible(),
                            onBackClick = listDetailNavigator::navigateBack,
                            snackbarHostState = snackbarHostState,
                            navigateToAppDetail = ::onRuleClickShowDetailPane,
                            updateIconThemingState = updateIconThemingState,
                        )
                        composable<SearchPlaceholderRoute> {
                            Text("Search Placeholder")
                        }
                    }
                }
            }
        },
    )
    LaunchedEffect(Unit) {
        if (!selectedPackageName.isNullOrEmpty()) {
            // Initial packageName was provided when navigating to AppList, so show its details.
            onPackageNameClickShowDetailPane(
                packageName = selectedPackageName,
                tab = selectedTab?.name ?: AppDetailTabs.Info.name,
                searchKeyword = searchKeyword,
            )
        } else if (selectedRuleId != null) {
            // Initial ruleId was provided when navigating to RuleList, so show its details.
            onRuleClickShowDetailPane(ruleId = selectedRuleId)
        }
    }
}
