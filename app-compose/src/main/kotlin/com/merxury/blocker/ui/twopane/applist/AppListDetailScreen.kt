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

package com.merxury.blocker.ui.twopane.applist

import androidx.activity.compose.BackHandler
import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.feature.appdetail.AppDetailPlaceholder
import com.merxury.blocker.feature.appdetail.navigation.AppDetailRoute
import com.merxury.blocker.feature.appdetail.navigation.appDetailScreen
import com.merxury.blocker.feature.appdetail.navigation.navigateToAppDetail
import com.merxury.blocker.feature.applist.AppListScreen
import com.merxury.blocker.feature.applist.navigation.AppListRoute
import com.merxury.blocker.ui.twopane.isDetailPaneVisible
import com.merxury.blocker.ui.twopane.isListPaneVisible
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
internal object AppDetailPlaceholderRoute

// TODO: Remove @Keep when https://issuetracker.google.com/353898971 is fixed
@Keep
@Serializable
internal object AppListDetailNavHostRoute

fun NavGraphBuilder.appListDetailScreen(
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToRuleDetail: (String) -> Unit,
) {
    composable<AppListRoute> {
        AppListDetailRoute(
            navigateToSettings = navigateToSettings,
            navigateToSupportAndFeedback = navigateToSupportAndFeedback,
            snackbarHostState = snackbarHostState,
            navigateToComponentDetail = navigateToComponentDetail,
            navigateToRuleDetail = navigateToRuleDetail,
            updateIconThemingState = updateIconThemingState,
        )
    }
}

@Composable
internal fun AppListDetailRoute(
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToRuleDetail: (String) -> Unit,
    viewModel: AppList2PaneViewModel = hiltViewModel(),
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
) {
    val selectedPackageName by viewModel.selectedPackageName.collectAsStateWithLifecycle()
    AppListDetailScreen(
        navigateToSettings = navigateToSettings,
        navigateToSupportAndFeedback = navigateToSupportAndFeedback,
        selectedPackageName = selectedPackageName,
        onAppClick = viewModel::onAppClick,
        snackbarHostState = snackbarHostState,
        navigateToComponentDetail = navigateToComponentDetail,
        navigateToRuleDetail = navigateToRuleDetail,
        updateIconThemingState = updateIconThemingState,
        windowAdaptiveInfo = windowAdaptiveInfo,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun AppListDetailScreen(
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    selectedPackageName: String?,
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToRuleDetail: (String) -> Unit,
    onAppClick: (String) -> Unit,
    windowAdaptiveInfo: WindowAdaptiveInfo,
) {
    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator(
        scaffoldDirective = calculatePaneScaffoldDirective(windowAdaptiveInfo),
        initialDestinationHistory = listOfNotNull(
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List),
            ThreePaneScaffoldDestinationItem<Nothing>(ListDetailPaneScaffoldRole.Detail).takeIf {
                selectedPackageName != null
            },
        ),
    )
    BackHandler(listDetailNavigator.canNavigateBack()) {
        listDetailNavigator.navigateBack()
    }

    var nestedNavHostStartRoute by remember {
        val route =
            selectedPackageName?.let { AppDetailRoute(packageName = it) }
                ?: AppDetailPlaceholderRoute
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

    fun onAppClickShowDetailPane(packageName: String) {
        onAppClick(packageName)
        if (listDetailNavigator.isDetailPaneVisible()) {
            // If the detail pane was visible, then use the nestedNavController navigate call
            // directly
            nestedNavController.navigateToAppDetail(packageName) {
                popUpTo<AppListDetailNavHostRoute>()
            }
        } else {
            // Otherwise, recreate the NavHost entirely, and start at the new destination
            nestedNavHostStartRoute = AppDetailRoute(packageName = packageName)
            nestedNavKey = UUID.randomUUID()
        }
        listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }

    ListDetailPaneScaffold(
        modifier = Modifier.background(color = MaterialTheme.colorScheme.background),
        value = listDetailNavigator.scaffoldValue,
        directive = listDetailNavigator.scaffoldDirective,
        listPane = {
            AppListScreen(
                navigateToAppDetail = ::onAppClickShowDetailPane,
                navigateToSettings = navigateToSettings,
                navigateToSupportAndFeedback = navigateToSupportAndFeedback,
                highlightSelectedApp = listDetailNavigator.isDetailPaneVisible(),
            )
        },
        detailPane = {
            key(nestedNavKey) {
                NavHost(
                    navController = nestedNavController,
                    startDestination = nestedNavHostStartRoute,
                    route = AppListDetailNavHostRoute::class,
                ) {
                    appDetailScreen(
                        onBackClick = listDetailNavigator::navigateBack,
                        snackbarHostState = snackbarHostState,
                        navigateToComponentDetail = navigateToComponentDetail,
                        navigateToRuleDetail = navigateToRuleDetail,
                        updateIconThemingState = updateIconThemingState,
                        showBackButton = !listDetailNavigator.isListPaneVisible(),
                    )
                    composable<AppDetailPlaceholderRoute> {
                        AppDetailPlaceholder()
                    }
                }
            }
        },
    )
}
