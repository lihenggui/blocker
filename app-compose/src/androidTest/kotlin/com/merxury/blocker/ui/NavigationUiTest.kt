/*
 * Copyright 2024 Blocker
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

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.accompanist.testharness.TestHarness
import com.merxury.blocker.core.data.util.NetworkMonitor
import com.merxury.blocker.core.data.util.PermissionMonitor
import com.merxury.blocker.core.data.util.TimeZoneMonitor
import com.merxury.blocker.uitesthiltmanifest.HiltComponentActivity
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.inject.Inject

/**
 * Tests that the navigation UI is rendered correctly on different screen sizes.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@HiltAndroidTest
class NavigationUiTest {

    /**
     * Manages the components' state and is used to perform injection on your test
     */
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    /**
     * Create a temporary folder used to create a Data Store file. This guarantees that
     * the file is removed in between each test, preventing a crash.
     */
    @BindValue
    @get:Rule(order = 1)
    val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    /**
     * Use a test activity to set the content on.
     */
    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var permissionMonitor: PermissionMonitor

    @Inject
    lateinit var timeZoneMonitor: TimeZoneMonitor

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun compactWidth_compactHeight_showsNavigationBar() {
        composeTestRule.setContent {
            TestHarness(size = DpSize(400.dp, 400.dp)) {
                BoxWithConstraints {
                    BlockerApp(
                        fakeAppState(maxWidth = maxWidth, maxHeight = maxHeight),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("BlockerBottomBar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BlockerNavRail").assertDoesNotExist()
    }

    @Test
    fun mediumWidth_compactHeight_showsNavigationRail() {
        composeTestRule.setContent {
            TestHarness(size = DpSize(610.dp, 400.dp)) {
                BoxWithConstraints {
                    BlockerApp(
                        fakeAppState(maxWidth = maxWidth, maxHeight = maxHeight),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("BlockerNavRail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BlockerBottomBar").assertDoesNotExist()
    }

    @Test
    fun expandedWidth_compactHeight_showsNavigationRail() {
        composeTestRule.setContent {
            TestHarness(size = DpSize(900.dp, 400.dp)) {
                BoxWithConstraints {
                    BlockerApp(
                        fakeAppState(maxWidth = maxWidth, maxHeight = maxHeight),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("BlockerNavRail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BlockerBottomBar").assertDoesNotExist()
    }

    @Test
    fun compcatWidth_mediumHeight_showsNavigationBar() {
        composeTestRule.setContent {
            TestHarness(size = DpSize(400.dp, 500.dp)) {
                BoxWithConstraints {
                    BlockerApp(
                        fakeAppState(maxWidth = maxWidth, maxHeight = maxHeight),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("BlockerBottomBar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BlockerNavRail").assertDoesNotExist()
    }

    @Test
    fun mediumWidth_mediumHeight_showsNavigationRail() {
        composeTestRule.setContent {
            TestHarness(size = DpSize(610.dp, 500.dp)) {
                BoxWithConstraints {
                    BlockerApp(
                        fakeAppState(maxWidth = maxWidth, maxHeight = maxHeight),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("BlockerNavRail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BlockerBottomBar").assertDoesNotExist()
    }

    @Test
    fun expandedWidth_mediumHeight_showsNavigationRail() {
        composeTestRule.setContent {
            TestHarness(size = DpSize(900.dp, 500.dp)) {
                BoxWithConstraints {
                    BlockerApp(
                        fakeAppState(maxWidth = maxWidth, maxHeight = maxHeight),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("BlockerNavRail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BlockerBottomBar").assertDoesNotExist()
    }

    @Test
    fun compactWidth_expandedHeight_showsNavigationBar() {
        composeTestRule.setContent {
            TestHarness(size = DpSize(400.dp, 1000.dp)) {
                BoxWithConstraints {
                    BlockerApp(
                        fakeAppState(maxWidth = maxWidth, maxHeight = maxHeight),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("BlockerBottomBar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BlockerNavRail").assertDoesNotExist()
    }

    @Test
    fun mediumWidth_expandedHeight_showsNavigationRail() {
        composeTestRule.setContent {
            TestHarness(size = DpSize(610.dp, 1000.dp)) {
                BoxWithConstraints {
                    BlockerApp(
                        fakeAppState(maxWidth = maxWidth, maxHeight = maxHeight),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("BlockerNavRail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BlockerBottomBar").assertDoesNotExist()
    }

    @Test
    fun expandedWidth_expandedHeight_showsNavigationRail() {
        composeTestRule.setContent {
            TestHarness(size = DpSize(900.dp, 1000.dp)) {
                BoxWithConstraints {
                    BlockerApp(
                        fakeAppState(maxWidth = maxWidth, maxHeight = maxHeight),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("BlockerNavRail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BlockerBottomBar").assertDoesNotExist()
    }

    @Composable
    private fun fakeAppState(maxWidth: Dp, maxHeight: Dp) = rememberBlockerAppState(
        windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(maxWidth, maxHeight)),
        networkMonitor = networkMonitor,
        permissionMonitor = permissionMonitor,
        timeZoneMonitor = timeZoneMonitor,
    )
}
