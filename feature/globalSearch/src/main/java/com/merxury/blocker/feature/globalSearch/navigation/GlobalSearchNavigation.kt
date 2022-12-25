package com.merxury.blocker.feature.globalSearch.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.merxury.blocker.feature.globalSearch.GlobalSearchRoute

const val globalSearchRoute = "global_search_route"

fun NavController.navigateToGlobalSearch(navOptions: NavOptions? = null) {
    this.navigate(globalSearchRoute, navOptions)
}

fun NavGraphBuilder.globalSearchScreen() {
    composable(route = globalSearchRoute) {
        GlobalSearchRoute()
    }
}
