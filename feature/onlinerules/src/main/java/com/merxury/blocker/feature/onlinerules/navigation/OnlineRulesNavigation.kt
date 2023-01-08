package com.merxury.blocker.feature.onlinerules.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import com.google.accompanist.navigation.animation.composable
import com.merxury.blocker.feature.onlinerules.OnlineRulesRoute

const val onlineRulesRoute = "online_rules_route"

fun NavController.navigateToOnlineRules(navOptions: NavOptions? = null) {
    this.navigate(onlineRulesRoute, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.onlineRulesScreen() {
    composable(route = onlineRulesRoute) {
        OnlineRulesRoute()
    }
}
