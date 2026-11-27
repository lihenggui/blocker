/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.feature.appdetail.impl

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.core.testing.util.viewportEdgeClippedScrollItemSuppression
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.profile.ProfileLabel
import com.merxury.blocker.feature.appdetail.api.navigation.AppDetailTabs
import com.merxury.blocker.feature.appdetail.impl.ui.ComponentRowActions
import com.merxury.blocker.feature.appdetail.impl.ui.ComponentRowItem
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

/** The detail screen in each state, on a phone, a foldable and a tablet, and on a phone in the dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AppDetailScreenScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val allTabs = listOf(AppDetailTabs.Info, AppDetailTabs.Service, AppDetailTabs.Activity, AppDetailTabs.Sdk)
    private val counts = mapOf(AppDetailTabs.Service to previewServiceRows.size, AppDetailTabs.Activity to previewActivityRows.size, AppDetailTabs.Sdk to 1)
    private val header = AppHeaderUiState(app = previewHeaderApp, launch = LaunchMode.OWN_USER, backgroundActivityAvailable = true)

    private fun list(
        tab: AppDetailTabs,
        components: ComponentsUiState = ComponentsUiState.Loaded(previewComponents),
        matchedRules: MatchedRulesUiState = MatchedRulesUiState.Loaded(previewMatchedRules),
    ) = ComponentListUiState(
        tabs = TabState(allTabs, tab, counts),
        components = components,
        matchedRules = matchedRules,
    )

    @Before
    fun setTimeZone() {
        // Make time zone deterministic in tests
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun appInfoTab() = capture("AppDetailScreenAppInfoTab", header, list(AppDetailTabs.Info))

    @Test
    fun appInfoTabWithLibChecker() = capture(
        "AppDetailWithLibCheckerScreen",
        header.copy(showLibChecker = true, hasCustomizedRules = true),
        list(AppDetailTabs.Info),
    )

    @Test
    fun otherProfile() = capture(
        "AppDetailScreenOtherProfile",
        header.copy(profile = ProfileLabel.Work, launch = LaunchMode.PROFILE),
        list(AppDetailTabs.Activity),
    )

    @Test
    fun componentTab() = capture("AppDetailScreenComponentTab", header, list(AppDetailTabs.Activity))

    @Test
    fun componentInFlight() = capture(
        "AppDetailScreenComponentInFlight",
        header,
        list(AppDetailTabs.Activity),
        ControlUiState(inFlight = setOf(previewActivityRows[0].id, previewActivityRows[1].id)),
    )

    @Test
    fun batchProgress() = capture(
        "AppDetailScreenBatchProgress",
        header,
        list(AppDetailTabs.Service),
        ControlUiState(inFlight = previewServiceRows.map { it.id }.toSet(), batch = BatchProgress(ControlGoal.BLOCK, done = 10, total = 25)),
    )

    @Test
    fun componentLoading() = capture("AppDetailScreenComponentLoading", header, list(AppDetailTabs.Activity, components = ComponentsUiState.Loading))

    @Test
    fun componentRefreshing() = capture("AppDetailScreenComponentRefreshing", header, list(AppDetailTabs.Activity).copy(isRefreshing = true))

    @Test
    fun componentEmpty() = capture(
        "AppDetailScreenComponentEmpty",
        header,
        list(AppDetailTabs.Activity, components = ComponentsUiState.Loaded(AppComponents())),
    )

    @Test
    fun sdkTab() = capture("AppDetailScreenSdkTab", header, list(AppDetailTabs.Sdk))

    @Test
    fun sdkLoading() = capture("AppDetailScreenSdkLoading", header, list(AppDetailTabs.Sdk, matchedRules = MatchedRulesUiState.Loading))

    @Test
    fun searchMode() = capture(
        "AppDetailScreenSearchMode",
        header,
        list(AppDetailTabs.Activity),
        searchQuery = "Activity",
    )

    @Test
    fun selectionMode() = capture(
        "AppDetailScreenSelectedMode",
        header,
        list(AppDetailTabs.Activity).copy(isSelectionMode = true, selected = setOf(previewActivityRows[1].id)),
    )

    @Test
    fun notInstalled() = capture(
        "AppDetailScreenNotInstalled",
        AppHeaderUiState(),
        list(AppDetailTabs.Info, components = ComponentsUiState.Loaded(AppComponents())).copy(refreshFailure = ReadFailure.PackageNotFound(AppKey(previewHeaderApp.packageName, UserId(0)))),
    )

    @Test
    fun rowStates() {
        // Rows on and off, a running service, and the last one with its change in flight.
        val rows = previewActivityRows.drop(1) + previewServiceRows.first() + previewActivityRows.first()
        val content = @Composable {
            BlockerTheme {
                Surface {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        rows.forEachIndexed { index, row ->
                            ComponentRowItem(row = row, inFlight = index == rows.lastIndex, actions = ComponentRowActions())
                        }
                    }
                }
            }
        }
        composeTestRule.captureMultiDevice(
            screenshotName = "AppDetailComponentRowStates",
            accessibilitySuppressions = viewportEdgeClippedScrollItemSuppression(),
        ) { content() }
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppDetailComponentRowStates",
            accessibilitySuppressions = viewportEdgeClippedScrollItemSuppression(),
            darkMode = true,
        ) { content() }
    }

    private fun capture(
        name: String,
        header: AppHeaderUiState,
        list: ComponentListUiState,
        control: ControlUiState = ControlUiState(),
        searchQuery: String? = null,
    ) {
        composeTestRule.captureMultiDevice(
            screenshotName = name,
            accessibilitySuppressions = viewportEdgeClippedScrollItemSuppression(),
        ) { Screen(header, list, control, searchQuery) }
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = name,
            accessibilitySuppressions = viewportEdgeClippedScrollItemSuppression(),
            darkMode = true,
        ) { Screen(header, list, control, searchQuery) }
    }

    @Composable
    private fun Screen(header: AppHeaderUiState, list: ComponentListUiState, control: ControlUiState, searchQuery: String?) {
        BlockerTheme {
            Surface {
                AppDetailContent(header = header, list = list, control = control, searchQuery = searchQuery)
            }
        }
    }
}
