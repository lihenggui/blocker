/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.ui

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.get
import androidx.navigation.navOptions
import androidx.tracing.trace
import com.google.accompanist.navigation.material.BottomSheetNavigator
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.google.accompanist.navigation.material.rememberBottomSheetNavigator
import com.merxury.blocker.core.data.util.NetworkMonitor
import com.merxury.blocker.core.ui.TrackDisposableJank
import com.merxury.blocker.feature.applist.navigation.navigateToAppList
import com.merxury.blocker.feature.generalrules.navigation.navigateToGeneralRule
import com.merxury.blocker.feature.search.navigation.navigateToSearch
import com.merxury.blocker.navigation.TopLevelDestination
import com.merxury.blocker.navigation.TopLevelDestination.APP
import com.merxury.blocker.navigation.TopLevelDestination.RULE
import com.merxury.blocker.navigation.TopLevelDestination.SEARCH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalMaterialNavigationApi::class)
@Composable
fun rememberBlockerAppState(
    windowSizeClass: WindowSizeClass,
    networkMonitor: NetworkMonitor,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    bottomSheetNavigator: BottomSheetNavigator = rememberBottomSheetNavigator(),
    navController: NavHostController = rememberNavController(bottomSheetNavigator),
): BlockerAppState {
    NavigationTrackingSideEffect(navController)
    return remember(bottomSheetNavigator, navController, coroutineScope, windowSizeClass, networkMonitor) {
        BlockerAppState(bottomSheetNavigator, navController, coroutineScope, windowSizeClass, networkMonitor)
    }
}

@Stable
@OptIn(ExperimentalMaterialNavigationApi::class)
class BlockerAppState(
    val bottomSheetNavigator: BottomSheetNavigator,
    val navController: NavHostController,
    val coroutineScope: CoroutineScope,
    val windowSizeClass: WindowSizeClass,
    networkMonitor: NetworkMonitor,
) {
    val currentDestination: NavDestination?
        @Composable get() = navController
            .currentBackStackEntryAsState().value?.destination

    val currentTopLevelDestination: TopLevelDestination?
        @Composable get() {
            // TODO: Read backStack directly from the navController when
            //  https://issuetracker.google.com/issues/295553995 is resolved.
            // Get compose navigator so backstack can be read
            val composeNavigator = remember {
                navController.navigatorProvider[ComposeNavigator::class]
            }
            // The navigator needs to be attached before the backstack can be read
            var navigatorAttached by remember { mutableStateOf(false) }
            // When the current destination has changed, the navigator
            // is guaranteed to be attached
            DisposableEffect(navController) {
                val onDestinationChangedListener =
                    NavController.OnDestinationChangedListener { _, _, _ ->
                        navigatorAttached = true
                    }
                navController.addOnDestinationChangedListener(onDestinationChangedListener)
                onDispose {
                    navController.removeOnDestinationChangedListener(onDestinationChangedListener)
                }
            }
            return when (navigatorAttached) {
                false -> null
                true ->
                    composeNavigator
                        .backStack
                        .collectAsStateWithLifecycle()
                        .value
                        .currentTopLevelDestination(topLevelDestinations)
            }
        }

    val shouldShowBottomBar: Boolean
        get() = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    val shouldShowNavRail: Boolean
        get() = !shouldShowBottomBar

    val isOffline = networkMonitor.isOnline
        .map(Boolean::not)
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /**
     * Map of top level destinations to be used in the TopBar, BottomBar and NavRail. The key is the
     * route.
     */
    val topLevelDestinations: List<TopLevelDestination> = TopLevelDestination.values().asList()

    /**
     * UI logic for navigating to a top level destination in the app. Top level destinations have
     * only one copy of the destination of the back stack, and save and restore state whenever you
     * navigate to and from it.
     *
     * @param topLevelDestination: The destination the app needs to navigate to.
     */
    fun navigateToTopLevelDestination(topLevelDestination: TopLevelDestination) {
        trace("Navigation: ${topLevelDestination.name}") {
            val topLevelNavOptions = navOptions {
                // Pop up to the start destination of the graph to
                // avoid building up a large stack of destinations
                // on the back stack as users select items
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                // Avoid multiple copies of the same destination when
                // reselecting the same item
                launchSingleTop = true
                // Restore state when reselecting a previously selected item
                restoreState = true
            }

            when (topLevelDestination) {
                APP -> navController.navigateToAppList(topLevelNavOptions)
                RULE -> navController.navigateToGeneralRule(topLevelNavOptions)
                SEARCH -> navController.navigateToSearch(topLevelNavOptions)
            }
        }
    }

    fun onBackClick() {
        navController.popBackStack()
    }
}

/**
 * Stores information about navigation events to be used with JankStats
 */
@Composable
private fun NavigationTrackingSideEffect(navController: NavHostController) {
    TrackDisposableJank(navController) { metricsHolder ->
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            metricsHolder.state?.putState("Navigation", destination.route.toString())
        }

        navController.addOnDestinationChangedListener(listener)

        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }
}

/**
 * Walks the backstack to determine the current [TopLevelDestination] in focus.
 */
private fun List<NavBackStackEntry>.currentTopLevelDestination(
    topLevelDestinations: List<TopLevelDestination>,
): TopLevelDestination? {
    // Walk the back stack from the top to find the first entry that matches a
    // top level destination
    for (index in lastIndex downTo 0) {
        val firstMatch = topLevelDestinations.firstOrNull(this[index]::matches)
        if (firstMatch != null) return firstMatch
    }
    return null
}

/**
 * Checks if a [NavBackStackEntry] matches a [TopLevelDestination]
 */
private fun NavBackStackEntry.matches(
    topLevelDestination: TopLevelDestination,
) = destination.route?.contains(
    other = topLevelDestination.name,
    ignoreCase = true,
) ?: false
