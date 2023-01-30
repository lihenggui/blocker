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
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.feature.appdetail.AppDetailRoute
import com.merxury.blocker.feature.appdetail.navigation.Screen.Detail

internal const val packageNameArg = "packageName"
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
    this.navigate("app_detail_route/$encodedId?screen=$screen")
}

fun NavGraphBuilder.appDetailScreen(
    onBackClick: () -> Unit,
) {
    composable(
        route = "app_detail_route/{$packageNameArg}?screen={screenName}",
        arguments = listOf(
            navArgument(packageNameArg) { type = NavType.StringType },
            navArgument(screenNameArg) {
                type = NavType.StringType
                defaultValue = Detail.name
            },
        ),
    ) {
        AppDetailRoute(onBackClick = onBackClick)
    }
}

sealed class Screen(val name: String) {
    object Detail : Screen("detail")
    object Receiver : Screen("receiver")
    object Service : Screen("service")
    object Activity : Screen("activity")
    object Provider : Screen("provider")
}
