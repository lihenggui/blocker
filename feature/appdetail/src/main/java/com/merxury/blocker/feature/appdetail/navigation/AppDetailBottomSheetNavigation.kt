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
import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.feature.appdetail.AppDetailBottomSheetRoute

fun NavController.navigateToAppDetailBottomSheet(
    packageName: String,
    tab: AppDetailTabs = AppDetailTabs.Info,
    searchKeyword: List<String> = listOf(),
) {
    val encodedId = Uri.encode(packageName)
    val keywords = searchKeyword.joinToString(",")
    this.navigate("app_detail_bottom_sheet_route/$encodedId?screen=${tab.name}?keyword=$keywords") {
        // Avoid multiple copies of the same destination when
        // reselecting the same item
        launchSingleTop = true
    }
}

fun NavGraphBuilder.appDetailBottomSheetScreen(
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    navigateToComponentDetail: (String) -> Unit,
) {
    dialog(
        route = "app_detail_bottom_sheet_route/{$packageNameArg}?screen={$tabArg}?keyword={$keywordArg}",
        arguments = listOf(
            navArgument(packageNameArg) { type = NavType.StringType },
            navArgument(tabArg) { type = NavType.StringType },
            navArgument(keywordArg) { type = NavType.StringType },
        ),
    ) {
        AppDetailBottomSheetRoute(
            dismissHandler = onBackClick,
            snackbarHostState = snackbarHostState,
            navigateToComponentDetail = navigateToComponentDetail,
        )
    }
}
