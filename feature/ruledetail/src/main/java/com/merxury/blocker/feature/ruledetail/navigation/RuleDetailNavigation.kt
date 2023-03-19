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

package com.merxury.blocker.feature.ruledetail.navigation

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.merxury.blocker.core.ui.rule.RuleDetailTabs
import com.merxury.blocker.core.ui.rule.RuleDetailTabs.Description
import com.merxury.blocker.feature.ruledetail.RuleDetailRoute

@VisibleForTesting
internal const val ruleIdArg = "ruleId"

@VisibleForTesting
internal const val tabArg = "tab"

internal class RuleIdArgs(val ruleId: Int, val tabs: RuleDetailTabs = Description) {
    constructor(savedStateHandle: SavedStateHandle) :
        this(
            checkNotNull(savedStateHandle[ruleIdArg]),
            RuleDetailTabs.fromName(savedStateHandle[tabArg]),
        )
}

fun NavController.navigateToRuleDetail(ruleId: Int, tab: RuleDetailTabs = Description) {
    this.navigate("rule_detail_route/$ruleId?screen=${tab.name}") {
        // Avoid multiple copies of the same destination when
        // reselecting the same item
        launchSingleTop = true
    }
}

fun NavGraphBuilder.ruleDetailScreen(
    onBackClick: () -> Unit,
    navigateToAppDetail: (String) -> Unit,
) {
    composable(
        route = "rule_detail_route/{$ruleIdArg}?screen={$tabArg}",
        arguments = listOf(
            navArgument(ruleIdArg) { type = NavType.IntType },
            navArgument(tabArg) { type = NavType.StringType },
        ),
    ) {
        RuleDetailRoute(onBackClick, navigateToAppDetail)
    }
}
