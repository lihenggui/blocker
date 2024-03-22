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

package com.merxury.blocker.feature.search.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.feature.search.SearchRoute

const val SEARCH_ROUTE = "search_route"
const val PACKAGE_NAME_ARG = "packageName"
const val TAB_ARG = "tab"
const val KEYWORD_ARG = "keyword"
const val RULE_ID_ARG = "ruleId"

internal class AppDetailArgs(
    val packageName: String,
    val tabs: String = AppDetailTabs.Info.name,
    val searchKeyword: List<String> = listOf(),
) {
    constructor(savedStateHandle: SavedStateHandle) :
        this(
            savedStateHandle[PACKAGE_NAME_ARG] ?: "",
            savedStateHandle[TAB_ARG] ?: AppDetailTabs.Info.name,
            savedStateHandle[KEYWORD_ARG] ?: listOf()
        )
}

fun NavController.navigateToSearch(navOptions: NavOptions) =
    navigate(SEARCH_ROUTE, navOptions)

fun NavGraphBuilder.searchScreen(
    snackbarHostState: SnackbarHostState,
    navigateToAppDetail: (String, AppDetailTabs, List<String>) -> Unit = { _, _, _ -> },
    navigateToRuleDetail: (String) -> Unit = {},
) {
    composable(route = SEARCH_ROUTE) {
        SearchRoute(
            snackbarHostState = snackbarHostState,
            navigateToAppDetail = navigateToAppDetail,
            navigateToRuleDetail = navigateToRuleDetail,
        )
    }
}
