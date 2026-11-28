/*
 * Copyright 2025 Blocker
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

import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.core.data.util.PermissionStatus.NO_PERMISSION
import com.merxury.blocker.core.navigation.NavigationState
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.core.navigation.rememberNavigationState
import com.merxury.blocker.core.testing.util.TestPermissionMonitor
import com.merxury.blocker.feature.appdetail.api.navigation.AppDetailNavKey
import com.merxury.blocker.feature.applist.api.navigation.AppListNavKey
import com.merxury.blocker.feature.generalrule.api.navigation.GeneralRuleNavKey
import com.merxury.blocker.feature.search.api.navigation.SearchNavKey
import com.merxury.blocker.feature.wake.api.navigation.WakeOverviewNavKey
import com.merxury.blocker.feature.wake.api.navigation.navigateToWakeApp
import com.merxury.blocker.feature.wake.api.navigation.navigateToWakeOverview
import com.merxury.blocker.navigation.TOP_LEVEL_NAV_ITEMS
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Tests [BlockerAppState].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
@OptIn(ExperimentalCoroutinesApi::class)
class BlockerAppStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Create the test dependencies.

    private val permissionMonitor = TestPermissionMonitor()

    // Subject under test.
    private lateinit var state: BlockerAppState

    private fun testNavigationState() = NavigationState(
        startKey = AppListNavKey,
        topLevelStack = NavBackStack(AppListNavKey),
        subStacks = mapOf(
            AppListNavKey to NavBackStack(AppListNavKey),
            WakeOverviewNavKey to NavBackStack(WakeOverviewNavKey),
            GeneralRuleNavKey() to NavBackStack(GeneralRuleNavKey()),
            SearchNavKey to NavBackStack(SearchNavKey),
        ),
    )

    @Test
    fun givenAppListIsCurrent_whenNavigatingToGeneralRules_thenCurrentDestinationChanges() = runTest {
        val navigationState = testNavigationState()
        val navigator = Navigator(navigationState)

        composeTestRule.setContent {
            state = remember(navigationState) {
                return@remember BlockerAppState(
                    permissionMonitor = permissionMonitor,
                    coroutineScope = backgroundScope,
                    navigationState = navigationState,
                )
            }
        }

        assertEquals(AppListNavKey, state.navigationState.currentTopLevelKey)
        assertEquals(AppListNavKey, state.navigationState.currentKey)

        // Navigate to another destination once
        navigator.navigate(GeneralRuleNavKey())

        composeTestRule.waitForIdle()

        assertEquals(GeneralRuleNavKey(), state.navigationState.currentTopLevelKey)
        assertEquals(GeneralRuleNavKey(), state.navigationState.currentKey)
    }

    @Test
    fun whenCreatingAppState_thenItExposesTopLevelDestinationsInOrder() = runTest {
        composeTestRule.setContent {
            state = rememberBlockerAppState(permissionMonitor = permissionMonitor)
        }
        val navigationState = state.navigationState

        assertEquals(
            listOf(
                AppListNavKey,
                WakeOverviewNavKey,
                GeneralRuleNavKey(),
                SearchNavKey,
            ),
            navigationState.topLevelKeys.toList(),
        )
    }

    @Test
    fun givenSavedSearchDetail_whenBackgroundIsInserted_thenSearchRestoresItsOwnStack() {
        val restoration = StateRestorationTester(composeTestRule)
        var roots = setOf<NavKey>(AppListNavKey, GeneralRuleNavKey(), SearchNavKey)
        lateinit var navigationState: NavigationState
        val detail = AppDetailNavKey("com.example")
        restoration.setContent {
            navigationState = rememberNavigationState(startKey = AppListNavKey, topLevelKeys = roots)
        }

        composeTestRule.runOnIdle {
            val navigator = Navigator(navigationState)
            navigator.navigate(SearchNavKey)
            navigator.navigate(detail)
            roots = TOP_LEVEL_NAV_ITEMS.keys
        }
        restoration.emulateSavedInstanceStateRestore()

        composeTestRule.runOnIdle {
            assertEquals(SearchNavKey, navigationState.currentTopLevelKey)
            assertEquals(detail, navigationState.currentKey)
            Navigator(navigationState).navigateToWakeOverview()
            assertEquals(listOf(WakeOverviewNavKey), navigationState.currentSubStack.toList())
        }
    }

    @Test
    fun givenWakeDetailsForTheSameAppInDifferentStacks_whenGoingBack_thenEachStackKeepsItsOwnEntry() {
        val navigationState = testNavigationState()
        val navigator = Navigator(navigationState)
        val packageName = "com.example"
        navigator.navigateToWakeApp(packageName)
        val appsEntry = navigationState.currentKey
        navigator.navigateToWakeOverview()
        navigator.navigateToWakeApp(packageName)

        navigator.goBack()
        assertEquals(WakeOverviewNavKey, navigationState.currentKey)
        navigator.goBack()
        assertEquals(appsEntry, navigationState.currentKey)
        navigator.navigateToWakeOverview()
        assertEquals(listOf(WakeOverviewNavKey), navigationState.currentSubStack.toList())
    }

    @Test
    fun givenAccessIsUnavailable_whenObservingAppState_thenItReportsNoPermission() = runTest(UnconfinedTestDispatcher()) {
        composeTestRule.setContent {
            state = BlockerAppState(
                permissionMonitor = permissionMonitor,
                coroutineScope = backgroundScope,
                navigationState = testNavigationState(),
            )
        }

        backgroundScope.launch { state.currentPermission.collect() }
        permissionMonitor.setPermission(NO_PERMISSION)
        assertEquals(
            NO_PERMISSION,
            state.currentPermission.value,
        )
    }
}
