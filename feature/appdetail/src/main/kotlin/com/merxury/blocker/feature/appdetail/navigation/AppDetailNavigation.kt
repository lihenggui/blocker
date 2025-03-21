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

package com.merxury.blocker.feature.appdetail.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.feature.appdetail.AppDetailScreen
import kotlinx.serialization.Serializable

@Serializable
data class AppDetailRoute(
    val packageName: String,
    val tab: String = AppDetailTabs.Info.name,
    val searchKeyword: List<String> = listOf(),
)

fun NavController.navigateToAppDetail(
    packageName: String,
    tab: String = AppDetailTabs.Info.name,
    searchKeyword: List<String> = listOf(),
    navOptions: NavOptionsBuilder.() -> Unit = {},
) {
    navigate(route = AppDetailRoute(packageName, tab, searchKeyword)) {
        navOptions()
    }
}

fun NavGraphBuilder.appDetailScreen(
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToRuleDetail: (String) -> Unit,
    showBackButton: Boolean,
) {
    composable<AppDetailRoute> {
        AppDetailScreen(
            onBackClick = onBackClick,
            snackbarHostState = snackbarHostState,
            navigateToComponentDetail = navigateToComponentDetail,
            navigateToRuleDetail = navigateToRuleDetail,
            updateIconThemingState = updateIconThemingState,
            showBackButton = showBackButton,
        )
    }
}
