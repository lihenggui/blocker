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

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.model.data.IconBasedThemingState
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.feature.appdetail.AppDetailRoute
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

private val URL_CHARACTER_ENCODING = UTF_8.name()

@VisibleForTesting
internal const val PACKAGE_NAME_ARG = "packageName"

@VisibleForTesting
internal const val TAB_ARG = "tab"

@VisibleForTesting
internal const val KEYWORD_ARG = "keyword"

internal class AppDetailArgs(
    val packageName: String,
    val tabs: AppDetailTabs = AppDetailTabs.Info,
    val searchKeyword: List<String> = listOf(),
) {
    constructor(savedStateHandle: SavedStateHandle) :
        this(
            URLDecoder.decode(
                checkNotNull(savedStateHandle[PACKAGE_NAME_ARG]),
                URL_CHARACTER_ENCODING,
            ),
            AppDetailTabs.fromName(savedStateHandle[TAB_ARG]),
            URLDecoder.decode(checkNotNull(savedStateHandle[KEYWORD_ARG]), URL_CHARACTER_ENCODING)
                .split(","),
        )
}

fun NavController.navigateToAppDetail(
    packageName: String,
    tab: AppDetailTabs = AppDetailTabs.Info,
    searchKeyword: List<String> = listOf(),
) {
    val encodedId = URLEncoder.encode(packageName, URL_CHARACTER_ENCODING)
    val keywords = URLEncoder.encode(searchKeyword.joinToString(","), URL_CHARACTER_ENCODING)
    this.navigate("app_detail_route/$encodedId?screen=${tab.name}?keyword=$keywords") {
        // Avoid multiple copies of the same destination when
        // reselecting the same item
        launchSingleTop = true
    }
}

fun NavGraphBuilder.detailScreen(
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    updateIconBasedThemingState: (IconBasedThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigatedToComponentSortScreen: () -> Unit,
) {
    composable(
        route = "app_detail_route/{$PACKAGE_NAME_ARG}?screen={$TAB_ARG}?keyword={$KEYWORD_ARG}",
        arguments = listOf(
            navArgument(PACKAGE_NAME_ARG) { type = NavType.StringType },
            navArgument(TAB_ARG) { type = NavType.StringType },
            navArgument(KEYWORD_ARG) { type = NavType.StringType },
        ),
    ) {
        AppDetailRoute(
            onBackClick = onBackClick,
            snackbarHostState = snackbarHostState,
            navigateToComponentDetail = navigateToComponentDetail,
            navigatedToComponentSortScreen = navigatedToComponentSortScreen,
            updateIconBasedThemingState = updateIconBasedThemingState,
        )
    }
}
