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

package com.merxury.blocker.feature.helpandfeedback.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import com.google.accompanist.navigation.animation.composable
import com.merxury.blocker.feature.helpandfeedback.SupportAndFeedbackRoute

const val supportAndFeedbackRoute = "support_and_feedback_route"

fun NavController.navigateToSupportAndFeedback(navOptions: NavOptions? = null) {
    this.navigate(supportAndFeedbackRoute, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.supportAndFeedbackScreen(onBackClick: () -> Unit) {
    composable(route = supportAndFeedbackRoute) {
        SupportAndFeedbackRoute(
            onNavigationClick = onBackClick,
        )
    }
}
