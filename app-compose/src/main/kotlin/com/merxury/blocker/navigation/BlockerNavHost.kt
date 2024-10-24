/*
 * Copyright 2024 Blocker
 * Copyright 2022 The Android Open Source Project
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

package com.merxury.blocker.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.navigation.BottomSheetNavigator
import androidx.compose.material.navigation.ModalBottomSheetLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.feature.appdetail.navigation.appDetailScreen
import com.merxury.blocker.feature.appdetail.navigation.componentDetailScreen
import com.merxury.blocker.feature.appdetail.navigation.navigateToAppDetail
import com.merxury.blocker.feature.appdetail.navigation.navigateToComponentDetail
import com.merxury.blocker.feature.applist.navigation.AppListRoute
import com.merxury.blocker.feature.helpandfeedback.navigation.navigateToSupportAndFeedback
import com.merxury.blocker.feature.helpandfeedback.navigation.supportAndFeedbackScreen
import com.merxury.blocker.feature.licenses.navigation.licensesScreen
import com.merxury.blocker.feature.licenses.navigation.navigateToLicenses
import com.merxury.blocker.feature.ruledetail.navigation.navigateToRuleDetail
import com.merxury.blocker.feature.ruledetail.navigation.ruleDetailScreen
import com.merxury.blocker.feature.search.navigation.searchScreen
import com.merxury.blocker.feature.settings.navigation.navigateToSettings
import com.merxury.blocker.feature.settings.navigation.settingsScreen
import com.merxury.blocker.feature.sort.navigation.appSortScreen
import com.merxury.blocker.feature.sort.navigation.componentSortScreen
import com.merxury.blocker.feature.sort.navigation.navigateToAppSortScreen
import com.merxury.blocker.feature.sort.navigation.navigateToComponentSortScreen
import com.merxury.blocker.ui.BlockerAppState
import com.merxury.blocker.ui.twopane.applist.appListDetailScreen
import com.merxury.blocker.ui.twopane.rule.ruleListDetailScreen

/**
 * Top-level navigation graph. Navigation is organized as explained at
 * https://d.android.com/jetpack/compose/nav-adaptive
 *
 * The navigation graph defined in this file defines the different top level routes. Navigation
 * within each route is handled using state and Back Handlers.
 */

@Composable
fun BlockerNavHost(
    bottomSheetNavigator: BottomSheetNavigator,
    appState: BlockerAppState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    dismissBottomSheet: () -> Unit,
    modifier: Modifier = Modifier,
    updateIconThemingState: (IconThemingState) -> Unit = {},
) {
    val navController = appState.navController
    ModalBottomSheetLayout(bottomSheetNavigator) {
        NavHost(
            navController = navController,
            startDestination = AppListRoute,
            modifier = modifier,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) },
        ) {
            appListDetailScreen(
                navigateToSettings = navController::navigateToSettings,
                navigateToSupportAndFeedback = navController::navigateToSupportAndFeedback,
                navigateTooAppSortScreen = navController::navigateToAppSortScreen,
                snackbarHostState = snackbarHostState,
                updateIconThemingState = updateIconThemingState,
                navigateToComponentDetail = navController::navigateToComponentDetail,
                navigateToComponentSortScreen = navController::navigateToComponentSortScreen,
                navigateToRuleDetail = navController::navigateToRuleDetail,
            )
            appDetailScreen(
                onBackClick = onBackClick,
                showBackButton = true,
                snackbarHostState = snackbarHostState,
                updateIconThemingState = updateIconThemingState,
                navigateToComponentDetail = navController::navigateToComponentDetail,
                navigateToComponentSortScreen = navController::navigateToComponentSortScreen,
                navigateToRuleDetail = navController::navigateToRuleDetail,
            )
            ruleListDetailScreen(
                snackbarHostState = snackbarHostState,
                navigateToAppDetail = navController::navigateToAppDetail,
                updateIconThemingState = updateIconThemingState,
            )
            searchScreen(
                snackbarHostState = snackbarHostState,
                navigateToAppDetail = navController::navigateToAppDetail,
                navigateToRuleDetail = navController::navigateToRuleDetail,
            )
            ruleDetailScreen(
                onBackClick = onBackClick,
                snackbarHostState = snackbarHostState,
                navigateToAppDetail = navController::navigateToAppDetail,
                updateIconThemingState = updateIconThemingState,
            )
            settingsScreen(
                onBackClick,
                snackbarHostState = snackbarHostState,
            )
            supportAndFeedbackScreen(
                onBackClick = onBackClick,
                navigateToLicenses = navController::navigateToLicenses,
                snackbarHostState = snackbarHostState,
            )
            componentDetailScreen(
                dismissHandler = onBackClick,
            )
            componentSortScreen(
                dismissHandler = dismissBottomSheet,
            )
            appSortScreen(
                dismissHandler = dismissBottomSheet,
            )
            licensesScreen(
                onBackClick = onBackClick,
            )
        }
    }
}
