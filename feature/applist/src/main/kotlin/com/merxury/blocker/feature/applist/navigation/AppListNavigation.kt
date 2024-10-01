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

package com.merxury.blocker.feature.applist.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.merxury.blocker.feature.applist.AppListRoute

const val APP_LIST_ROUTE_BASIC = "app_list_route"
const val PACKAGE_NAME_ARG = "packageName"
const val APP_LIST_ROUTE = "$APP_LIST_ROUTE_BASIC?$PACKAGE_NAME_ARG={$PACKAGE_NAME_ARG}"

fun NavController.navigateToAppList(
    packageName: String? = null,
    navOptions: NavOptions? = null,
) {
    val route = if (packageName != null) {
        "$APP_LIST_ROUTE_BASIC?${PACKAGE_NAME_ARG}=$packageName"
    } else {
        APP_LIST_ROUTE_BASIC
    }
    navigate(route, navOptions)
}

fun NavGraphBuilder.appListScreen(
    navigateToAppDetail: (String) -> Unit,
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    navigateTooAppSortScreen: () -> Unit,
) {
    composable(
        route = APP_LIST_ROUTE,
        arguments = listOf(
            navArgument(PACKAGE_NAME_ARG) {
                defaultValue = null
                nullable = true
                type = NavType.StringType
            },
        ),
    ) {
        AppListRoute(
            navigateToAppDetail = navigateToAppDetail,
            navigateToSettings = navigateToSettings,
            navigateToSupportAndFeedback = navigateToSupportAndFeedback,
            navigateTooAppSortScreen = navigateTooAppSortScreen,
        )
    }
}
