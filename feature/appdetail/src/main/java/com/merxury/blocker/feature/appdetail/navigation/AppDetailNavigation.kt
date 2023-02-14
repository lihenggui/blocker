/*
 * Copyright 2023 Blocker
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

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.feature.appdetail.AppDetailRoute
import com.merxury.blocker.feature.appdetail.Screen

@VisibleForTesting
internal const val packageNameArg = "packageName"

@VisibleForTesting
internal const val screenArg = "screen"

internal class AppDetailArgs(val packageName: String, val screen: Screen = Screen.Detail) {
    constructor(savedStateHandle: SavedStateHandle, stringDecoder: StringDecoder) :
        this(
            stringDecoder.decodeString(checkNotNull(savedStateHandle[packageNameArg])),
            Screen.fromName(savedStateHandle[screenArg]),
        )
}

fun NavController.navigateToAppDetail(packageName: String, screen: Screen = Screen.Detail) {
    val encodedId = Uri.encode(packageName)
    this.navigate("app_detail_route/$encodedId?screen=${screen.name}") {
        // Pop up to the start destination of the graph to
        // avoid building up a large stack of destinations
        // on the back stack as users select items
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        // Avoid multiple copies of the same destination when
        // reselecting the same item
        launchSingleTop = true
    }
}

fun NavGraphBuilder.detailScreen(onBackClick: () -> Unit) {
    composable(
        route = "app_detail_route/{$packageNameArg}?screen={$screenArg}",
        arguments = listOf(
            navArgument(packageNameArg) { type = NavType.StringType },
            navArgument(screenArg) { type = NavType.StringType },
        ),
    ) {
        AppDetailRoute(onBackClick = onBackClick)
    }
}
