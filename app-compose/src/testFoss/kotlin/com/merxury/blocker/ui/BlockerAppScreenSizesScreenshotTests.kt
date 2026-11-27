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

import android.util.Log
import androidx.activity.BackEventCompat
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.window.core.layout.WindowSizeClass.Companion.BREAKPOINTS_V1
import androidx.window.core.layout.computeWindowSizeClass
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.github.takahirom.roborazzi.captureRoboImage
import com.merxury.blocker.R
import com.merxury.blocker.core.data.test.repository.FakeOperationHistoryRepository
import com.merxury.blocker.core.data.util.NetworkMonitor
import com.merxury.blocker.core.data.util.PermissionMonitor
import com.merxury.blocker.core.data.util.TimeZoneMonitor
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.history.Operation
import com.merxury.blocker.core.model.history.OperationEntry
import com.merxury.blocker.core.model.history.OperationSource
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.core.testing.util.DefaultRoborazziOptions
import com.merxury.blocker.feature.history.api.navigation.navigateToHistoryOperation
import com.merxury.blocker.feature.settings.api.navigation.navigateToSettings
import com.merxury.blocker.navigation.TOP_LEVEL_NAV_ITEMS
import com.merxury.blocker.uitesthiltmanifest.HiltComponentActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.util.TimeZone
import javax.inject.Inject
import kotlin.time.Instant
import com.merxury.blocker.core.designsystem.R.string as designString
import com.merxury.blocker.core.ui.R.string as uiString
import com.merxury.blocker.feature.applist.api.R.string as appsString
import com.merxury.blocker.feature.globalifwrule.impl.R.string as firewallString
import com.merxury.blocker.feature.history.impl.R.string as historyString
import com.merxury.blocker.feature.settings.api.R.string as settingsString
import com.merxury.blocker.feature.wake.api.R.string as wakeString

/**
 * Tests that the navigation UI is rendered correctly on different screen sizes.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Configure Robolectric to use a very large screen size that can fit all of the test sizes.
// This allows enough room to render the content under test without clipping or scaling.
@Config(application = HiltTestApplication::class, qualifiers = "w1000dp-h1000dp-480dpi")
@LooperMode(LooperMode.Mode.PAUSED)
@HiltAndroidTest
class BlockerAppScreenSizesScreenshotTests {

    /**
     * Manages the components' state and is used to perform injection on your test
     */
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    /**
     * Use a test activity to set the content on.
     */
    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var timeZoneMonitor: TimeZoneMonitor

    @Inject
    lateinit var permissionMonitor: PermissionMonitor

    @Inject
    lateinit var operationHistory: FakeOperationHistoryRepository

    @Before
    fun setup() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()

        // Initialize WorkManager for instrumentation tests.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            InstrumentationRegistry.getInstrumentation().context,
            config,
        )

        hiltRule.inject()
    }

    @Before
    fun setTimeZone() {
        // Make time zone deterministic in tests
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private fun testBlockerAppScreenshotWithSize(width: Dp, height: Dp, screenshotName: String) {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                DeviceConfigurationOverride(
                    override = DeviceConfigurationOverride.ForcedSize(DpSize(width, height)),
                ) {
                    BlockerTheme {
                        val appState = rememberBlockerAppState(
                            networkMonitor = networkMonitor,
                            permissionMonitor = permissionMonitor,
                            timeZoneMonitor = timeZoneMonitor,
                        )
                        BlockerApp(
                            appState,
                            windowAdaptiveInfo = WindowAdaptiveInfo(
                                windowSizeClass = BREAKPOINTS_V1.computeWindowSizeClass(
                                    widthDp = width.value,
                                    heightDp = height.value,
                                ),
                                windowPosture = Posture(),
                            ),
                        )
                    }
                }
            }
        }

        TOP_LEVEL_NAV_ITEMS.values.forEach { destination(it.iconTextId).assertIsDisplayed() }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/testFoss/screenshots/$screenshotName.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    private fun showApp(widthDp: Float = 412f, startNavigation: (Navigator) -> Unit = {}) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(widthDp.dp, 880.dp))) {
                    BlockerTheme {
                        val appState = rememberBlockerAppState(networkMonitor, permissionMonitor, timeZoneMonitor)
                        LaunchedEffect(appState) {
                            startNavigation(Navigator(appState.navigationState))
                        }
                        BlockerApp(
                            appState = appState,
                            windowAdaptiveInfo = WindowAdaptiveInfo(
                                windowSizeClass = BREAKPOINTS_V1.computeWindowSizeClass(widthDp = widthDp, heightDp = 880f),
                                windowPosture = Posture(),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun text(id: Int) = composeTestRule.activity.getString(id)

    private fun destination(label: Int) = composeTestRule.onNode(hasTestTag("BlockerNavItem") and hasText(text(label)))

    @Test
    fun givenBackgroundTab_whenPredictiveBackCancelsThenCommits_thenNavigationShowsThePreviewedTab() {
        showApp()
        val appTab = destination(R.string.apps)
        val backgroundTab = destination(R.string.background)
        val searchTab = destination(R.string.search)
        val dispatcher = composeTestRule.activity.onBackPressedDispatcher
        backgroundTab.performClick()
        backgroundTab.assertIsSelected()
        composeTestRule.onNodeWithText(text(wakeString.feature_wake_api_title)).assertExists()
        composeTestRule.onNodeWithContentDescription(text(designString.core_designsystem_back)).assertDoesNotExist()

        fun swipeBack() {
            composeTestRule.runOnUiThread {
                dispatcher.dispatchOnBackStarted(BackEventCompat(411f, 400f, 0f, BackEventCompat.EDGE_RIGHT))
                dispatcher.dispatchOnBackProgressed(BackEventCompat(250f, 400f, 0.65f, BackEventCompat.EDGE_RIGHT))
            }
            composeTestRule.waitForIdle()
        }

        searchTab.performClick()
        searchTab.assertIsSelected()
        swipeBack()
        backgroundTab.assertIsSelected()
        composeTestRule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        searchTab.assertIsSelected()

        swipeBack()
        composeTestRule.runOnUiThread { dispatcher.onBackPressed() }
        backgroundTab.assertIsSelected()

        swipeBack()
        appTab.assertIsSelected()
        composeTestRule.runOnUiThread { dispatcher.onBackPressed() }
        appTab.assertIsSelected()
        composeTestRule.runOnIdle { check(!dispatcher.hasEnabledCallbacks()) }
    }

    @Test
    fun whenOpeningRules_thenOnlyTheGeneralRulesScreenAppears() {
        showApp()

        destination(R.string.rules).performClick()

        composeTestRule.onNode(hasText(text(R.string.rules)) and !hasTestTag("BlockerNavItem")).assertExists()
        composeTestRule.onNodeWithText(text(firewallString.feature_globalifwrule_impl_title)).assertDoesNotExist()
    }

    @Test
    fun givenAppsMenu_whenOpeningFirewallAndGoingBack_thenTheAppsListReturns() {
        showApp()
        val moreMenu = composeTestRule.onNodeWithContentDescription(text(uiString.core_ui_more_menu))
        moreMenu.performClick()

        composeTestRule.onNodeWithText(text(appsString.feature_applist_api_firewall_rules)).performClick()

        composeTestRule.onNodeWithText(text(firewallString.feature_globalifwrule_impl_title)).assertExists()
        destination(R.string.apps).assertIsSelected()
        composeTestRule.onNodeWithContentDescription(text(designString.core_designsystem_back)).performClick()
        moreMenu.assertExists()
        composeTestRule.onNodeWithText(text(firewallString.feature_globalifwrule_impl_title)).assertDoesNotExist()
    }

    @Test
    fun givenCompactLayout_whenOpeningAnOperationAndGoingBackTwice_thenSettingsReturns() {
        openHistory(widthDp = 412f)
        historyRow().performClick()

        historyTitle().assertDoesNotExist()
        composeTestRule.onNodeWithText("SyncService").assertIsDisplayed()

        operationBackButton().assertIsDisplayed().performClick()
        historyTitle().assertIsDisplayed()
        composeTestRule.onNodeWithText("SyncService").assertDoesNotExist()
        systemBack()
        assertSettingsDisplayed()
    }

    @Test
    fun givenExpandedLayout_whenOpeningAnOperation_thenDetailIsShownBesideTheSelectedRow() {
        openHistory(widthDp = 900f)
        historyRow().performClick()

        historyTitle().assertIsDisplayed()
        historyRow().assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithText("SyncService").assertIsDisplayed()
        operationBackButton().assertDoesNotExist()
        val listBackButton = composeTestRule.onNodeWithContentDescription(text(designString.core_designsystem_back))
        listBackButton.assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(
            "src/testFoss/screenshots/HistoryExpandedTwoPane.png",
            roborazziOptions = DefaultRoborazziOptions,
        )

        listBackButton.performClick()
        historyTitle().assertIsDisplayed()
        historyRow().assertIsNotSelected()
        composeTestRule.onNodeWithText("SyncService").assertDoesNotExist()
        listBackButton.performClick()
        assertSettingsDisplayed()
    }

    private fun openHistory(widthDp: Float) {
        seedHistoryOperation()
        showApp(widthDp)
        composeTestRule.onNodeWithContentDescription(text(uiString.core_ui_more_menu)).performClick()
        composeTestRule.onNodeWithText(text(appsString.feature_applist_api_settings)).performClick()
        composeTestRule.onNodeWithText(text(settingsString.feature_settings_api_operation_history)).performScrollTo().performClick()
        composeTestRule.onNode(hasTestTag("settings:content")).assertDoesNotExist()
        historyTitle().assertIsDisplayed()
    }

    private fun seedHistoryOperation(): Long {
        val operation = Operation(
            id = 1,
            timestamp = Instant.parse("2025-12-10T14:35:00Z"),
            source = OperationSource.SEARCH,
            entryCount = 1,
            blockedCount = 1,
        )
        val before = ControlState(PmSetting.ENABLED, IfwBlock.NOT_BLOCKED)
        operationHistory.entries[operation.id] = listOf(
            OperationEntry(
                operationId = operation.id,
                key = ComponentKey(ComponentId("com.example.notes", "com.example.notes.SyncService"), UserId.SYSTEM),
                type = ComponentType.SERVICE,
                before = before,
                after = before.copy(pm = PmSetting.DISABLED),
            ),
        )
        operationHistory.operations.value = listOf(operation)
        return operation.id
    }

    private fun operationBackButton() = composeTestRule.onNode(
        hasContentDescription(text(designString.core_designsystem_back)) and hasAnyAncestor(hasTestTag("history:operation")),
    )

    private fun systemBack() = composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }

    private fun historyTitle() = composeTestRule.onNodeWithText(text(historyString.feature_history_impl_title))

    private fun historyRow() = composeTestRule.onNode(hasClickAction() and hasText(text(historyString.feature_history_impl_source_search)) and !hasTestTag("BlockerNavItem"))

    private fun assertSettingsDisplayed() {
        composeTestRule.onNode(hasTestTag("settings:content")).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(settingsString.feature_settings_api_settings)).assertIsDisplayed()
    }

    @Test
    fun compactWidth_compactHeight_showsNavigationBar() {
        testBlockerAppScreenshotWithSize(
            400.dp,
            400.dp,
            "compactWidth_compactHeight_showsNavigationBar",
        )
    }

    @Test
    fun mediumWidth_compactHeight_showsNavigationRail() {
        testBlockerAppScreenshotWithSize(
            610.dp,
            400.dp,
            "mediumWidth_compactHeight_showsNavigationRail",
        )
    }

    @Test
    fun expandedWidth_compactHeight_showsNavigationRail() {
        testBlockerAppScreenshotWithSize(
            900.dp,
            400.dp,
            "expandedWidth_compactHeight_showsNavigationRail",
        )
    }

    @Test
    fun compactWidth_mediumHeight_showsNavigationBar() {
        testBlockerAppScreenshotWithSize(
            400.dp,
            500.dp,
            "compactWidth_mediumHeight_showsNavigationBar",
        )
    }

    @Test
    fun mediumWidth_mediumHeight_showsNavigationRail() {
        testBlockerAppScreenshotWithSize(
            610.dp,
            500.dp,
            "mediumWidth_mediumHeight_showsNavigationRail",
        )
    }

    @Test
    fun expandedWidth_mediumHeight_showsNavigationRail() {
        testBlockerAppScreenshotWithSize(
            900.dp,
            500.dp,
            "expandedWidth_mediumHeight_showsNavigationRail",
        )
    }

    @Test
    fun compactWidth_expandedHeight_showsNavigationBar() {
        testBlockerAppScreenshotWithSize(
            400.dp,
            1000.dp,
            "compactWidth_expandedHeight_showsNavigationBar",
        )
    }

    @Test
    fun mediumWidth_expandedHeight_showsNavigationRail() {
        testBlockerAppScreenshotWithSize(
            610.dp,
            1000.dp,
            "mediumWidth_expandedHeight_showsNavigationRail",
        )
    }

    @Test
    fun expandedWidth_expandedHeight_showsNavigationRail() {
        testBlockerAppScreenshotWithSize(
            900.dp,
            1000.dp,
            "expandedWidth_expandedHeight_showsNavigationRail",
        )
    }
}
