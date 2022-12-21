/*
 * Copyright 2022 Blocker
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
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.merxury.blocker.feature.applist.AppListRoute

const val appListGraphRoutePattern = "app_list_graph"
const val appListRoute = "app_list_route"

fun NavController.navigateToAppListGraph(navOptions: NavOptions? = null) {
    this.navigate(appListGraphRoutePattern, navOptions)
}

fun NavGraphBuilder.appListGraph(
    navigateToAppDetail: (String) -> Unit,
    nestedGraphs: NavGraphBuilder.() -> Unit
) {
    navigation(
        route = appListGraphRoutePattern,
        startDestination = appListRoute
    ) {
        composable(route = appListRoute) {
            AppListRoute(navigateToAppDetail = navigateToAppDetail)
        }
        nestedGraphs()
    }
}
