/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.feature.ruledetail.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.core.ui.rule.RuleDetailTabs.Applicable
import com.merxury.blocker.feature.ruledetail.RuleDetailScreen
import kotlinx.serialization.Serializable

@Serializable
data class RuleDetailRoute(val ruleId: String, val tab: String = Applicable.name)

fun NavController.navigateToRuleDetail(
    ruleId: String,
    tab: String = Applicable.name,
    navOptions: NavOptionsBuilder.() -> Unit = {},
) {
    navigate(route = RuleDetailRoute(ruleId, tab)) {
        navOptions()
    }
}

fun NavGraphBuilder.ruleDetailScreen(
    showBackButton: Boolean = true,
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    navigateToAppDetail: (String) -> Unit,
    updateIconThemingState: (IconThemingState) -> Unit,
) {
    composable<RuleDetailRoute> {
        RuleDetailScreen(
            showBackButton = showBackButton,
            onBackClick = onBackClick,
            snackbarHostState = snackbarHostState,
            navigateToAppDetail = navigateToAppDetail,
            updateIconThemingState = updateIconThemingState,
        )
    }
}
