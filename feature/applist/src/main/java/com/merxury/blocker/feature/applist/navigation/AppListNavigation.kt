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

package com.merxury.blocker.feature.applist.navigation

import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.feature.applist.AppListRoute

const val packageNameArg = "packageName"
const val tabArg = "tab"
const val keywordArg = "keyword"
const val appListRoute =
    "app_list_route?packageName={$packageNameArg}?screen={$tabArg}?keyword={$keywordArg}"

internal class AppDetailArgs(
    val packageName: String = "",
    val tabs: AppDetailTabs = AppDetailTabs.Info,
    val searchKeyword: List<String> = listOf(),
) {
    constructor(savedStateHandle: SavedStateHandle, stringDecoder: StringDecoder) :
        this(
            stringDecoder.decodeString(checkNotNull(savedStateHandle[packageNameArg])),
            AppDetailTabs.fromName(savedStateHandle[tabArg]),
            stringDecoder.decodeString(checkNotNull(savedStateHandle[keywordArg])).split(","),
        )
}

fun NavController.navigateToAppList(
    packageName: String = "",
    tab: AppDetailTabs = AppDetailTabs.Info,
    searchKeyword: List<String> = listOf(),
    navOptions: NavOptions? = null,
) {
    val encodedId = Uri.encode(packageName)
    val keywords = searchKeyword.joinToString(",")
    navigate(
        "app_list_route?packageName=$encodedId?screen=${tab.name}?keyword=$keywords",
        navOptions,
    )
}

fun NavGraphBuilder.appListScreen(
    onBackClick: () -> Unit,
    shouldShowTwoPane: Boolean,
    snackbarHostState: SnackbarHostState,
    navigateToAppDetail: (String) -> Unit,
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    navigateToComponentDetail: (String) -> Unit,
) {
    composable(
        route = appListRoute,
        arguments = listOf(
            navArgument(packageNameArg) {
                defaultValue = ""
                type = NavType.StringType
            },
            navArgument(tabArg) {
                defaultValue = AppDetailTabs.Info.name
                type = NavType.StringType
            },
            navArgument(keywordArg) {
                defaultValue = ""
                type = NavType.StringType
            },
        ),
    ) {
        AppListRoute(
            onBackClick = onBackClick,
            shouldShowTwoPane = shouldShowTwoPane,
            navigateToAppDetail = navigateToAppDetail,
            navigateToSettings = navigateToSettings,
            navigateToSupportAndFeedback = navigateToSupportAndFeedback,
            navigateToComponentDetail = navigateToComponentDetail,
            snackbarHostState = snackbarHostState,
        )
    }
}
