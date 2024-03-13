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

package com.merxury.blocker.ui.applist2pane

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.PaneAdaptedValue
import androidx.compose.material3.adaptive.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.rememberListDetailPaneScaffoldNavigator
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
import com.merxury.blocker.core.model.data.IconBasedThemingState
import com.merxury.blocker.feature.appdetail.navigation.APP_DETAIL_ROUTE
import com.merxury.blocker.feature.appdetail.navigation.appDetailScreen
import com.merxury.blocker.feature.appdetail.navigation.navigateToAppDetail
import com.merxury.blocker.feature.applist.AppListRoute
import com.merxury.blocker.feature.applist.navigation.APP_LIST_ROUTE
import com.merxury.blocker.feature.applist.navigation.KEYWORD_ARG
import com.merxury.blocker.feature.applist.navigation.PACKAGE_NAME_ARG
import com.merxury.blocker.feature.applist.navigation.TAB_ARG

private const val APP_LIST_DETAIL_PANE_ROUTE = "app_list_detail_pane_route"

fun NavGraphBuilder.appListDetailScreen(
    navigateToAppDetail: (String) -> Unit,
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    navigateTooAppSortScreen: () -> Unit,
    snackbarHostState: SnackbarHostState,
    updateIconBasedThemingState: (IconBasedThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToComponentSortScreen: () -> Unit,
    navigateToRuleDetail: (String) -> Unit,
) {
    composable(
        route = APP_LIST_ROUTE,
        arguments = listOf(
            navArgument(PACKAGE_NAME_ARG) {
                type = NavType.StringType
                defaultValue = null
                nullable = true
            },
            navArgument(TAB_ARG) {
                type = NavType.StringType
                defaultValue = null
                nullable = true
            },
            navArgument(KEYWORD_ARG) {
                type = NavType.StringType
                defaultValue = null
                nullable = true
            },
        ),
    ) {
        AppListDetailRoute(
            navigateToAppDetail = navigateToAppDetail,
            navigateToSettings = navigateToSettings,
            navigateToSupportAndFeedback = navigateToSupportAndFeedback,
            navigateTooAppSortScreen = navigateTooAppSortScreen,
            snackbarHostState = snackbarHostState,
            navigateToComponentDetail = navigateToComponentDetail,
            navigateToComponentSortScreen = navigateToComponentSortScreen,
            navigateToRuleDetail = navigateToRuleDetail,
            updateIconBasedThemingState = updateIconBasedThemingState,
        )
    }
}

@Composable
internal fun AppListDetailRoute(
    navigateToAppDetail: (String) -> Unit,
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    navigateTooAppSortScreen: () -> Unit,
    snackbarHostState: SnackbarHostState,
    updateIconBasedThemingState: (IconBasedThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToComponentSortScreen: () -> Unit,
    navigateToRuleDetail: (String) -> Unit,
    viewModel: AppList2PaneViewModel = hiltViewModel(),
) {
    val selectedPackageName by viewModel.selectedPackageName.collectAsStateWithLifecycle()
    AppListDetailScreen(
        navigateToAppDetail = navigateToAppDetail,
        navigateToSettings = navigateToSettings,
        navigateToSupportAndFeedback = navigateToSupportAndFeedback,
        navigateTooAppSortScreen = navigateTooAppSortScreen,
        selectedPackageName = selectedPackageName,
        onAppClick = viewModel::onAppClick,
        snackbarHostState = snackbarHostState,
        navigateToComponentDetail = navigateToComponentDetail,
        navigateToComponentSortScreen = navigateToComponentSortScreen,
        navigateToRuleDetail = navigateToRuleDetail,
        updateIconBasedThemingState = updateIconBasedThemingState,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun AppListDetailScreen(
    navigateToAppDetail: (String) -> Unit,
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    navigateTooAppSortScreen: () -> Unit,
    selectedPackageName: String?,
    snackbarHostState: SnackbarHostState,
    updateIconBasedThemingState: (IconBasedThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToComponentSortScreen: () -> Unit,
    navigateToRuleDetail: (String) -> Unit,
    onAppClick: (String) -> Unit,
) {
    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator<Nothing>()
    BackHandler(listDetailNavigator.canNavigateBack()) {
        listDetailNavigator.navigateBack()
    }

    val nestedNavController = rememberNavController()

    fun onAppClickShowDetailPane(packageName: String) {
        onAppClick(packageName)
        nestedNavController.navigateToAppDetail(
            packageName = packageName,
            navOptions = {
                popUpTo(APP_LIST_DETAIL_PANE_ROUTE)
            },
        )
        listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }

    ListDetailPaneScaffold(
        scaffoldState = listDetailNavigator.scaffoldState,
        listPane = {
            AppListRoute(
                navigateToAppDetail = navigateToAppDetail,
                navigateToSettings = navigateToSettings,
                navigateToSupportAndFeedback = navigateToSupportAndFeedback,
                navigateTooAppSortScreen = navigateTooAppSortScreen,
                highlightSelectedApp = listDetailNavigator.isDetailPaneVisible(),
            )
        },
        detailPane = {
            NavHost(
                navController = nestedNavController,
                startDestination = APP_DETAIL_ROUTE,
                route = APP_LIST_DETAIL_PANE_ROUTE,
            ) {
                appDetailScreen(
                    onBackClick = listDetailNavigator::navigateBack,
                    snackbarHostState = snackbarHostState,
                    navigateToComponentDetail = navigateToComponentDetail,
                    navigateToComponentSortScreen = navigateToComponentSortScreen,
                    navigateToRuleDetail = navigateToRuleDetail,
                    updateIconBasedThemingState = updateIconBasedThemingState,
                    onAppClick = ::onAppClickShowDetailPane,
                    showBackButton = !listDetailNavigator.isListPaneVisible(),
                )
                composable(route = APP_DETAIL_ROUTE) {
                    Box {
                        Text("Placeholder")
                    }
                }
            }
        },
    )
    LaunchedEffect(Unit) {
        if (selectedPackageName != null) {
            // Initial packageName was provided when navigating to AppList, so show its details.
            onAppClickShowDetailPane(selectedPackageName)
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun <T> ThreePaneScaffoldNavigator<T>.isListPaneVisible(): Boolean =
    scaffoldState.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun <T> ThreePaneScaffoldNavigator<T>.isDetailPaneVisible(): Boolean =
    scaffoldState.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
