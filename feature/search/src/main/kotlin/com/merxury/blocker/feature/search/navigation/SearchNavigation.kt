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

package com.merxury.blocker.feature.search.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.feature.search.SearchRoute

const val SEARCH_ROUTE_BASIC = "search_route"
const val PACKAGE_NAME_ARG = "packageName"
const val TAB_ARG = "tab"
const val KEYWORD_ARG = "keyword"
const val RULE_ID_ARG = "ruleId"
const val SEARCH_DETAIL_ROUTE =
    "$SEARCH_ROUTE_BASIC?$PACKAGE_NAME_ARG={$PACKAGE_NAME_ARG}?$TAB_ARG={$TAB_ARG}?$KEYWORD_ARG={$KEYWORD_ARG}?$SEARCH_ROUTE_BASIC?$RULE_ID_ARG={$RULE_ID_ARG}"

fun NavController.navigateToSearch(
    packageName: String? = null,
    tab: AppDetailTabs = AppDetailTabs.Info,
    searchKeyword: List<String> = listOf(),
    ruleId: String? = null,
    navOptions: NavOptions? = null,
) {
    val keywords = searchKeyword.joinToString(",")
    val route = if (!packageName.isNullOrEmpty()) {
        StringBuilder(SEARCH_ROUTE_BASIC).apply {
            append("?$PACKAGE_NAME_ARG=$packageName")
            append("?$TAB_ARG=${tab.name}")
            append("?$KEYWORD_ARG=$keywords")
        }.toString()
    } else if (ruleId != null) {
        "$SEARCH_ROUTE_BASIC?${RULE_ID_ARG}=$ruleId"
    } else {
        SEARCH_ROUTE_BASIC
    }
    navigate(route, navOptions)
}

fun NavGraphBuilder.searchScreen(
    snackbarHostState: SnackbarHostState,
    navigateToAppDetail: (String, AppDetailTabs, List<String>) -> Unit = { _, _, _ -> },
    navigateToRuleDetail: (String) -> Unit = {},
) {
    composable(
        route = SEARCH_ROUTE_BASIC,
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
            navArgument(RULE_ID_ARG) {
                type = NavType.StringType
                defaultValue = null
                nullable = true
            },
        ),
    ) {
        SearchRoute(
            snackbarHostState = snackbarHostState,
            navigateToAppDetail = navigateToAppDetail,
            navigateToRuleDetail = navigateToRuleDetail,
        )
    }
}
