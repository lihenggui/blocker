package com.merxury.blocker.feature.globalsearch.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import com.google.accompanist.navigation.animation.composable
import com.merxury.blocker.feature.globalsearch.GlobalSearchRoute

const val globalSearchRoute = "global_search_route"

fun NavController.navigateToGlobalSearch(navOptions: NavOptions? = null) {
    this.navigate(globalSearchRoute, navOptions)
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.globalSearchScreen() {
    composable(route = globalSearchRoute) {
        GlobalSearchRoute()
    }
}
