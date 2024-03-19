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

package com.merxury.blocker.ui.rulelist2pane

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.ListDetailPaneScaffoldRole
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
import com.merxury.blocker.core.designsystem.theme.IconBasedThemingState
import com.merxury.blocker.core.ui.extension.isDetailPaneVisible
import com.merxury.blocker.core.ui.extension.isListPaneVisible
import com.merxury.blocker.feature.generalrules.GeneralRulesRoute
import com.merxury.blocker.feature.generalrules.navigation.GENERAL_RULE_ROUTE
import com.merxury.blocker.feature.generalrules.navigation.RULE_ID_ARG
import com.merxury.blocker.feature.ruledetail.navigation.RULE_DETAIL_ROUTE
import com.merxury.blocker.feature.ruledetail.navigation.navigateToRuleDetail
import com.merxury.blocker.feature.ruledetail.navigation.ruleDetailScreen

private const val RULE_LIST_DETAIL_PANE_ROUTE = "rule_list_detail_pane_route"

fun NavGraphBuilder.ruleListDetailScreen(
    snackbarHostState: SnackbarHostState,
    navigateToAppDetail: (String) -> Unit,
    updateIconBasedThemingState: (IconBasedThemingState) -> Unit,
) {
    composable(
        route = GENERAL_RULE_ROUTE,
        arguments = listOf(
            navArgument(RULE_ID_ARG) {
                type = NavType.StringType
                defaultValue = null
                nullable = true
            },
        ),
    ) {
        RuleListDetailRoute(
            snackbarHostState = snackbarHostState,
            updateIconBasedThemingState = updateIconBasedThemingState,
            navigateToAppDetail = navigateToAppDetail,
        )
    }
}

@Composable
internal fun RuleListDetailRoute(
    snackbarHostState: SnackbarHostState,
    updateIconBasedThemingState: (IconBasedThemingState) -> Unit,
    navigateToAppDetail: (String) -> Unit,
    viewModel: RuleList2PaneViewModel = hiltViewModel(),
) {
    val selectedPackageName by viewModel.selectedRuleId.collectAsStateWithLifecycle()
    RuleListDetailScreen(
        selectedRuleId = selectedPackageName,
        onRuleClick = viewModel::onRuleClick,
        snackbarHostState = snackbarHostState,
        updateIconBasedThemingState = updateIconBasedThemingState,
        navigateToAppDetail = navigateToAppDetail,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun RuleListDetailScreen(
    selectedRuleId: String?,
    snackbarHostState: SnackbarHostState,
    updateIconBasedThemingState: (IconBasedThemingState) -> Unit,
    navigateToAppDetail: (String) -> Unit,
    onRuleClick: (String) -> Unit,
) {
    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator<Nothing>()
    BackHandler(listDetailNavigator.canNavigateBack()) {
        listDetailNavigator.navigateBack()
    }

    val nestedNavController = rememberNavController()

    fun onRuleClickShowDetailPane(ruleId: String) {
        onRuleClick(ruleId)
        nestedNavController.navigateToRuleDetail(
            ruleId = ruleId,
            navOptions = {
                popUpTo(RULE_LIST_DETAIL_PANE_ROUTE)
            },
        )
        listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
    }

    ListDetailPaneScaffold(
        scaffoldState = listDetailNavigator.scaffoldState,
        listPane = {
            GeneralRulesRoute(
                highlightSelectedRule = listDetailNavigator.isDetailPaneVisible(),
                navigateToRuleDetail = ::onRuleClickShowDetailPane,
            )
        },
        detailPane = {
            NavHost(
                navController = nestedNavController,
                startDestination = RULE_DETAIL_ROUTE,
                route = RULE_LIST_DETAIL_PANE_ROUTE,
            ) {
                ruleDetailScreen(
                    showBackButton = !listDetailNavigator.isListPaneVisible(),
                    onBackClick = listDetailNavigator::navigateBack,
                    snackbarHostState = snackbarHostState,
                    navigateToAppDetail = navigateToAppDetail,
                    updateIconBasedThemingState = updateIconBasedThemingState,
                )
                composable(route = RULE_DETAIL_ROUTE) {
                    Box {
                        Text("Placeholder")
                    }
                }
            }
        },
    )
    LaunchedEffect(Unit) {
        if (selectedRuleId != null) {
            // Initial ruleId was provided when navigating to RuleList, so show its details.
            onRuleClickShowDetailPane(selectedRuleId)
        }
    }
}
