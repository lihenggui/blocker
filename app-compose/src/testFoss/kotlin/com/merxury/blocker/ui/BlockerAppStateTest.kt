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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import com.merxury.blocker.core.data.util.PermissionStatus.NO_PERMISSION
import com.merxury.blocker.core.testing.util.TestNetworkMonitor
import com.merxury.blocker.core.testing.util.TestPermissionMonitor
import com.merxury.blocker.core.testing.util.TestTimeZoneMonitor
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
import kotlin.test.assertTrue

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

    @Test
    fun blockerAppState_currentDestination() = runTest {
        var currentDestination: String? = null

        composeTestRule.setContent {
            val navController = rememberTestNavController()
            state = remember(navController) {
                return@remember BlockerAppState(
                    navController = navController,
                    networkMonitor = networkMonitor,
                    permissionMonitor = permissionMonitor,
                    coroutineScope = backgroundScope,
                    timeZoneMonitor = timeZoneMonitor,
                )
            }

            // Update currentDestination whenever it changes
            currentDestination = state.currentDestination?.route

            // Navigate to destination b once
            LaunchedEffect(Unit) {
                navController.setCurrentDestination("b")
            }
        }

        assertEquals("b", currentDestination)
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

        assertEquals(3, state.topLevelDestinations.size)
        assertTrue(state.topLevelDestinations[0].name.contains("APP", true))
        assertTrue(state.topLevelDestinations[1].name.contains("RULE", true))
        assertTrue(state.topLevelDestinations[2].name.contains("SEARCH", true))
    }

    @Test
    fun blockerAppState_WhenNetworkMonitorIsOffline_StateIsOffline() =
        runTest(UnconfinedTestDispatcher()) {
            composeTestRule.setContent {
                state = BlockerAppState(
                    navController = NavHostController(LocalContext.current),
                    networkMonitor = networkMonitor,
                    permissionMonitor = permissionMonitor,
                    coroutineScope = backgroundScope,
                    timeZoneMonitor = timeZoneMonitor,
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
    fun blockerAppState_WhenPermissionMonitorCantGetPermission_StateIsNoPermission() =
        runTest(UnconfinedTestDispatcher()) {
            composeTestRule.setContent {
                state = BlockerAppState(
                    navController = NavHostController(LocalContext.current),
                    networkMonitor = networkMonitor,
                    permissionMonitor = permissionMonitor,
                    coroutineScope = backgroundScope,
                    timeZoneMonitor = timeZoneMonitor,
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
    fun blockerAppState_differentTZ_withTimeZoneMonitorChange() =
        runTest(UnconfinedTestDispatcher()) {
            composeTestRule.setContent {
                state = BlockerAppState(
                    navController = NavHostController(LocalContext.current),
                    coroutineScope = backgroundScope,
                    networkMonitor = networkMonitor,
                    permissionMonitor = permissionMonitor,
                    timeZoneMonitor = timeZoneMonitor,
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

@Composable
private fun rememberTestNavController(): TestNavHostController {
    val context = LocalContext.current
    return remember {
        TestNavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            graph = createGraph(startDestination = "a") {
                composable("a") { }
                composable("b") { }
                composable("c") { }
            }
        }
    }
}
