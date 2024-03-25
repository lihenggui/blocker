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

package com.merxury.blocker.feature.appdetail.navigation

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.theme.IconThemingState
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

const val APP_DETAIL_ROUTE = "app_detail_route"

internal class AppDetailArgs(
    val packageName: String,
    val tabs: String = AppDetailTabs.Info.name,
    val searchKeyword: List<String> = listOf(),
) {
    constructor(savedStateHandle: SavedStateHandle) :
        this(
            URLDecoder.decode(
                checkNotNull(savedStateHandle[PACKAGE_NAME_ARG]),
                URL_CHARACTER_ENCODING,
            ),
            savedStateHandle[TAB_ARG] ?: AppDetailTabs.Info.name,
            URLDecoder.decode(checkNotNull(savedStateHandle[KEYWORD_ARG]), URL_CHARACTER_ENCODING)
                .split(","),
        )
}

fun NavController.navigateToAppDetail(
    packageName: String,
    tab: AppDetailTabs = AppDetailTabs.Info,
    searchKeyword: List<String> = listOf(),
    navOptions: NavOptionsBuilder.() -> Unit = {},
) {
    val encodedId = URLEncoder.encode(packageName, URL_CHARACTER_ENCODING)
    val keywords = URLEncoder.encode(searchKeyword.joinToString(","), URL_CHARACTER_ENCODING)
    val newRoute = "$APP_DETAIL_ROUTE/$encodedId?$TAB_ARG=${tab.name}?$KEYWORD_ARG=$keywords"
    navigate(newRoute) {
        navOptions()
    }
}

fun NavGraphBuilder.appDetailScreen(
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    updateIconBasedThemingState: (IconThemingState) -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToComponentSortScreen: () -> Unit,
    navigateToRuleDetail: (String) -> Unit,
    showBackButton: Boolean,
) {
    composable(
        route = "$APP_DETAIL_ROUTE/{$PACKAGE_NAME_ARG}?$TAB_ARG={$TAB_ARG}?$KEYWORD_ARG={$KEYWORD_ARG}",
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
            navigateToComponentSortScreen = navigateToComponentSortScreen,
            navigateToRuleDetail = navigateToRuleDetail,
            updateIconBasedThemingState = updateIconBasedThemingState,
            showBackButton = showBackButton,
        )
    }
}
