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

package com.merxury.blocker.ui.twopane.rule

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
import com.merxury.blocker.feature.generalrules.GeneralRulesRoute
import com.merxury.blocker.feature.ruledetail.RuleDetailPlaceholder
import com.merxury.blocker.feature.ruledetail.navigation.RuleDetailRoute
import com.merxury.blocker.feature.ruledetail.navigation.navigateToRuleDetail
import com.merxury.blocker.feature.ruledetail.navigation.ruleDetailScreen
import com.merxury.blocker.ui.twopane.isDetailPaneVisible
import com.merxury.blocker.ui.twopane.isListPaneVisible
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
internal object RuleDetailPlaceholderRoute

// TODO: Remove @Keep when https://issuetracker.google.com/353898971 is fixed
@Keep
@Serializable
internal object RuleListDetailPaneNavHostRoute

fun NavGraphBuilder.ruleListDetailScreen(
    snackbarHostState: SnackbarHostState,
    navigateToAppDetail: (String) -> Unit,
    updateIconThemingState: (IconThemingState) -> Unit,
) {
    composable<RuleListDetailPaneNavHostRoute> {
        RuleListDetailRoute(
            snackbarHostState = snackbarHostState,
            updateIconThemingState = updateIconThemingState,
            navigateToAppDetail = navigateToAppDetail,
        )
    }
}

@Composable
internal fun RuleListDetailRoute(
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToAppDetail: (String) -> Unit,
    viewModel: RuleList2PaneViewModel = hiltViewModel(),
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
) {
    val selectedRuleId by viewModel.selectedRuleId.collectAsStateWithLifecycle()
    RuleListDetailScreen(
        selectedRuleId = selectedRuleId,
        onRuleClick = viewModel::onRuleClick,
        snackbarHostState = snackbarHostState,
        updateIconThemingState = updateIconThemingState,
        navigateToAppDetail = navigateToAppDetail,
        windowAdaptiveInfo = windowAdaptiveInfo,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun RuleListDetailScreen(
    selectedRuleId: String?,
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToAppDetail: (String) -> Unit,
    onRuleClick: (String) -> Unit,
    windowAdaptiveInfo: WindowAdaptiveInfo,
) {
    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator(
        scaffoldDirective = calculatePaneScaffoldDirective(windowAdaptiveInfo),
        initialDestinationHistory = listOfNotNull(
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List),
            ThreePaneScaffoldDestinationItem<Nothing>(ListDetailPaneScaffoldRole.Detail).takeIf {
                selectedRuleId != null
            },
        ),
    )
    BackHandler(listDetailNavigator.canNavigateBack()) {
        listDetailNavigator.navigateBack()
    }

    var nestedNavHostStartRoute by remember {
        val route =
            selectedRuleId?.let { RuleDetailRoute(ruleId = it) } ?: RuleDetailPlaceholderRoute
        mutableStateOf(route)
    }
    var nestedNavKey by rememberSaveable(
        stateSaver = Saver({ it.toString() }, UUID::fromString),
    ) {
        mutableStateOf(UUID.randomUUID())
    }
    val nestedNavController = key(nestedNavKey) { rememberNavController() }

    fun onRuleClickShowDetailPane(ruleId: String) {
        onRuleClick(ruleId)
        if (listDetailNavigator.isDetailPaneVisible()) {
            // If the detail pane was visible, then use the nestedNavController navigate call
            // directly
            nestedNavController.navigateToRuleDetail(ruleId) {
                popUpTo<RuleListDetailPaneNavHostRoute>()
            }
        } else {
            // Otherwise, recreate the NavHost entirely, and start at the new destination
            nestedNavHostStartRoute = RuleDetailRoute(ruleId = ruleId)
            nestedNavKey = UUID.randomUUID()
        }
        listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }

    ListDetailPaneScaffold(
        modifier = Modifier.background(color = MaterialTheme.colorScheme.background),
        value = listDetailNavigator.scaffoldValue,
        directive = listDetailNavigator.scaffoldDirective,
        listPane = {
            GeneralRulesRoute(
                highlightSelectedRule = listDetailNavigator.isDetailPaneVisible(),
                navigateToRuleDetail = ::onRuleClickShowDetailPane,
            )
        },
        detailPane = {
            key(nestedNavKey) {
                NavHost(
                    navController = nestedNavController,
                    startDestination = nestedNavHostStartRoute,
                    route = RuleListDetailPaneNavHostRoute::class,
                ) {
                    ruleDetailScreen(
                        showBackButton = !listDetailNavigator.isListPaneVisible(),
                        onBackClick = listDetailNavigator::navigateBack,
                        snackbarHostState = snackbarHostState,
                        navigateToAppDetail = navigateToAppDetail,
                        updateIconThemingState = updateIconThemingState,
                    )
                    composable<RuleDetailPlaceholderRoute> {
                        RuleDetailPlaceholder()
                    }
                }
            }
        },
    )
}
