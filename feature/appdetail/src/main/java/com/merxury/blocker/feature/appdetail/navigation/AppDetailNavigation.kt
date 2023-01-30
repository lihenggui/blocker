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
import com.merxury.blocker.feature.appdetail.navigation.Screen.Detail
import timber.log.Timber

@VisibleForTesting
internal const val packageNameArg = "packageName"

@VisibleForTesting
internal const val screenNameArg = "screenName"

internal class AppDetailArgs(val packageName: String, val screenName: String) {
    constructor(savedStateHandle: SavedStateHandle, stringDecoder: StringDecoder) :
        this(
            stringDecoder.decodeString(checkNotNull(savedStateHandle[packageNameArg])),
            stringDecoder.decodeString(checkNotNull(savedStateHandle[screenNameArg])),
        )
}

fun NavController.navigateToAppDetail(packageName: String, screen: String) {
    val encodedId = Uri.encode(packageName)
    this.navigate("app_detail_route/$encodedId?screen=$screen") {
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

fun NavGraphBuilder.detailScreen(
    navController: NavController,
    onBackClick: () -> Unit,
) {
    composable(
        route = "app_detail_route/{$packageNameArg}?screen={$screenNameArg}",
        arguments = listOf(
            navArgument(packageNameArg) { type = NavType.StringType },
            navArgument(screenNameArg) {
                type = NavType.StringType
                defaultValue = Detail.name
            },
        ),
    ) { backStackEntry ->
        val packageName = backStackEntry.arguments?.getString(packageNameArg).orEmpty()
        val screen = Screen.fromName(backStackEntry.arguments?.getString(screenNameArg))
        AppDetailRoute(
            screen = screen,
            packageName = packageName,
            onBackClick = onBackClick,
            onNavigate = { destinationScreen ->
                if (destinationScreen == screen) {
                    // Don't navigate to the same destination
                    Timber.d("Ignore navigating to the same detail screen.")
                    return@AppDetailRoute
                }
                navController.navigateToAppDetail(packageName, destinationScreen.name)
            },
        )
    }
}

sealed class Screen(val name: String, val tabPosition: Int) {
    object Detail : Screen(DETAIL, tabPosition = 0)
    object Receiver : Screen(RECEIVER, tabPosition = 1)
    object Service : Screen(SERVICE, tabPosition = 2)
    object Activity : Screen(ACTIVITY, tabPosition = 3)
    object Provider : Screen(PROVIDER, tabPosition = 4)

    companion object {
        private const val DETAIL = "detail"
        private const val RECEIVER = "receiver"
        private const val SERVICE = "service"
        private const val ACTIVITY = "activity"
        private const val PROVIDER = "provider"

        fun fromName(name: String?): Screen = when (name) {
            DETAIL -> Detail
            RECEIVER -> Receiver
            SERVICE -> Service
            ACTIVITY -> Activity
            PROVIDER -> Provider
            else -> throw IllegalArgumentException("Invalid screen name in detail page")
        }

        fun fromPosition(pos: Int): Screen = when (pos) {
            Detail.tabPosition -> Detail
            Receiver.tabPosition -> Receiver
            Service.tabPosition -> Service
            Activity.tabPosition -> Activity
            Provider.tabPosition -> Provider
            else -> throw IllegalArgumentException("Invalid tab position in detail page")
        }
    }
}
