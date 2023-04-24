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

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.merxury.blocker.core.ui.rule.RuleDetailTabs
import com.merxury.blocker.core.ui.rule.RuleDetailTabs.Description
import com.merxury.blocker.feature.ruledetail.AppDetailBottomSheetRoute

fun NavController.navigateToRuleDetailBottomSheet(ruleId: Int, tab: RuleDetailTabs = Description) {
    this.navigate("rule_detail_bottom_sheet_route/$ruleId?screen=${tab.name}") {
        // Avoid multiple copies of the same destination when
        // reselecting the same item
        launchSingleTop = true
    }
}

fun NavGraphBuilder.ruleDetailBottomSheet(
    onBackClick: () -> Unit,
    navigateToAppDetail: (String) -> Unit,
    navigateToAppDetailBottomSheet: (String) -> Unit,
    useBottomSheetStyleInDetail: Boolean,
) {
    dialog(
        route = "rule_detail_bottom_sheet_route/{$ruleIdArg}?screen={$tabArg}",
        arguments = listOf(
            navArgument(ruleIdArg) { type = NavType.IntType },
            navArgument(tabArg) { type = NavType.StringType },
        ),
    ) {
        AppDetailBottomSheetRoute(
            dismissHandler = onBackClick,
            navigateToAppDetail = navigateToAppDetail,
            navigateToAppDetailBottomSheet = navigateToAppDetailBottomSheet,
            useBottomSheetStyleInDetail = useBottomSheetStyleInDetail,
        )
    }
}
