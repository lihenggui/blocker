package com.merxury.blocker.feature.onlineRules.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.merxury.blocker.feature.onlineRules.OnlineRulesRoute

const val onlineRulesRoute = "online_rules_route"

fun NavController.navigateToOnlineRules(navOptions: NavOptions? = null) {
    this.navigate(onlineRulesRoute, navOptions)
}

fun NavGraphBuilder.onlineRulesScreen() {
    composable(route = onlineRulesRoute) {
        OnlineRulesRoute()
    }
}
