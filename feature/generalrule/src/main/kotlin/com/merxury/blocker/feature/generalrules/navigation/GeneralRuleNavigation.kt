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

package com.merxury.blocker.feature.generalrules.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.merxury.blocker.feature.generalrules.GeneralRulesRoute

const val GENERAL_RULE_ROUTE = "rule_list_route"
const val RULE_ID_ARG = "ruleId"

fun NavController.navigateToGeneralRule(
    ruleId: Int? = null,
    navOptions: NavOptions? = null,
) {
    val route = if (ruleId != null) {
        "$GENERAL_RULE_ROUTE?${RULE_ID_ARG}=$ruleId"
    } else {
        GENERAL_RULE_ROUTE
    }
    navigate(route, navOptions)
}

fun NavGraphBuilder.generalRuleScreen(
    highlightSelectedRule: Boolean = false,
    navigateToRuleDetail: (String) -> Unit,
) {
    composable(
        route = GENERAL_RULE_ROUTE,
        arguments = listOf(
            navArgument(RULE_ID_ARG) {
                defaultValue = null
                nullable = true
                type = NavType.IntType
            },
        ),
    ) {
        GeneralRulesRoute(
            highlightSelectedRule = highlightSelectedRule,
            navigateToRuleDetail = navigateToRuleDetail,
        )
    }
}
