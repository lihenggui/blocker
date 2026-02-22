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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavBackStack
import com.merxury.blocker.core.data.util.PermissionStatus.NO_PERMISSION
import com.merxury.blocker.core.navigation.NavigationState
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.core.testing.util.TestNetworkMonitor
import com.merxury.blocker.core.testing.util.TestPermissionMonitor
import com.merxury.blocker.core.testing.util.TestTimeZoneMonitor
import com.merxury.blocker.feature.applist.api.navigation.AppListNavKey
import com.merxury.blocker.feature.debloator.api.navigation.DebloaterNavKey
import com.merxury.blocker.feature.generalrule.api.navigation.GeneralRuleNavKey
import com.merxury.blocker.feature.search.api.navigation.SearchNavKey
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
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
class BlockerAppStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Create the test dependencies.
    private val networkMonitor = TestNetworkMonitor()

    private val permissionMonitor = TestPermissionMonitor()

    private val timeZoneMonitor = TestTimeZoneMonitor()

    // Subject under test.
    private lateinit var state: BlockerAppState

    private fun testNavigationState() = NavigationState(
        startKey = AppListNavKey(),
        topLevelStack = NavBackStack(AppListNavKey()),
        subStacks = mapOf(
            AppListNavKey() to NavBackStack(AppListNavKey()),
            GeneralRuleNavKey() to NavBackStack(GeneralRuleNavKey()),
            DebloaterNavKey to NavBackStack(DebloaterNavKey),
            SearchNavKey() to NavBackStack(SearchNavKey()),
        ),
    )

    @Test
    fun blockerAppState_currentDestination() = runTest {
        val navigationState = testNavigationState()
        val navigator = Navigator(navigationState)

        composeTestRule.setContent {
            state = remember(navigationState) {
                return@remember BlockerAppState(
                    networkMonitor = networkMonitor,
                    permissionMonitor = permissionMonitor,
                    coroutineScope = backgroundScope,
                    timeZoneMonitor = timeZoneMonitor,
                    navigationState = navigationState,
                )
            }
        }

        assertEquals(AppListNavKey(), state.navigationState.currentTopLevelKey)
        assertEquals(AppListNavKey(), state.navigationState.currentKey)

        // Navigate to another destination once
        navigator.navigate(GeneralRuleNavKey())

        composeTestRule.waitForIdle()

        assertEquals(GeneralRuleNavKey(), state.navigationState.currentTopLevelKey)
        assertEquals(GeneralRuleNavKey(), state.navigationState.currentKey)
    }

    @Test
    fun blockerAppState_destinations() = runTest {
        composeTestRule.setContent {
            state = rememberBlockerAppState(
                networkMonitor = networkMonitor,
                permissionMonitor = permissionMonitor,
                timeZoneMonitor = timeZoneMonitor,
            )
        }
        val navigationState = state.navigationState

        assertEquals(4, navigationState.topLevelKeys.size)
        assertEquals(
            setOf(AppListNavKey(), GeneralRuleNavKey(), DebloaterNavKey, SearchNavKey()),
            navigationState.topLevelKeys,
        )
    }

    @Test
    fun blockerAppState_WhenNetworkMonitorIsOffline_StateIsOffline() = runTest(UnconfinedTestDispatcher()) {
        composeTestRule.setContent {
            state = BlockerAppState(
                networkMonitor = networkMonitor,
                permissionMonitor = permissionMonitor,
                coroutineScope = backgroundScope,
                timeZoneMonitor = timeZoneMonitor,
                navigationState = testNavigationState(),
            )
        }

        backgroundScope.launch { state.isOffline.collect() }
        networkMonitor.setConnected(false)
        assertEquals(
            true,
            state.isOffline.value,
        )
    }

    @Test
    fun blockerAppState_WhenPermissionMonitorCantGetPermission_StateIsNoPermission() = runTest(UnconfinedTestDispatcher()) {
        composeTestRule.setContent {
            state = BlockerAppState(
                networkMonitor = networkMonitor,
                permissionMonitor = permissionMonitor,
                coroutineScope = backgroundScope,
                timeZoneMonitor = timeZoneMonitor,
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

    @Test
    fun blockerAppState_differentTZ_withTimeZoneMonitorChange() = runTest(UnconfinedTestDispatcher()) {
        composeTestRule.setContent {
            state = BlockerAppState(
                coroutineScope = backgroundScope,
                networkMonitor = networkMonitor,
                permissionMonitor = permissionMonitor,
                timeZoneMonitor = timeZoneMonitor,
                navigationState = testNavigationState(),
            )
        }
        val changedTz = TimeZone.of("Europe/Prague")
        backgroundScope.launch { state.currentTimeZone.collect() }
        timeZoneMonitor.setTimeZone(changedTz)
        assertEquals(
            changedTz,
            state.currentTimeZone.value,
        )
    }
}
