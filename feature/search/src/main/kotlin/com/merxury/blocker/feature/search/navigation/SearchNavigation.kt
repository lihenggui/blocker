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
import androidx.navigation.compose.composable
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.feature.search.SearchScreen
import kotlinx.serialization.Serializable

@Serializable
data class SearchRoute(
    val packageName: String? = null,
    val tab: String? = null,
    val searchKeyword: List<String> = listOf(),
    val ruleId: String? = null,
)

fun NavController.navigateToSearch(
    packageName: String? = null,
    tab: String = AppDetailTabs.Info.name,
    searchKeyword: List<String> = listOf(),
    ruleId: String? = null,
    navOptions: NavOptions? = null,
) {
    navigate(
        route = SearchRoute(
            packageName = packageName,
            tab = tab,
            searchKeyword = searchKeyword,
            ruleId = ruleId,
        ),
        navOptions,
    )
}

fun NavGraphBuilder.searchScreen(
    snackbarHostState: SnackbarHostState,
    navigateToAppDetail: (String, String, List<String>) -> Unit = { _, _, _ -> },
    navigateToRuleDetail: (String) -> Unit = {},
) {
    composable<SearchRoute> {
        SearchScreen(
            snackbarHostState = snackbarHostState,
            navigateToAppDetail = navigateToAppDetail,
            navigateToRuleDetail = navigateToRuleDetail,
        )
    }
}
