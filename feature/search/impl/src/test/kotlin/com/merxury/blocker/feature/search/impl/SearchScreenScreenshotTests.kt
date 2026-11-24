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

package com.merxury.blocker.feature.search.impl

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.feature.search.api.R
import com.merxury.blocker.feature.search.impl.component.SearchTopBar
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

/** The search on a phone, a foldable and a tablet, and in the dark on a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SearchScreenScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setTimeZone() {
        // Make time zone deterministic in tests
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h880dp")
    fun givenSelectionMode_whenPredictiveBackCancelsThenCommits_thenOnlyCommitExitsSelection() {
        val state = mutableStateOf(SearchStates.selecting())
        composeTestRule.setContent {
            BlockerTheme {
                SearchTopBar(
                    uiState = state.value,
                    query = "",
                    onQueryChange = {},
                    onExitSelection = { state.value = state.value.copy(isSelectionMode = false) },
                    onSelectAll = {},
                    onBlockSelection = {},
                    onEnableSelection = {},
                )
            }
        }
        val dispatcher = composeTestRule.activity.onBackPressedDispatcher
        val search = composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.feature_search_api_search_hint))
        search.assertDoesNotExist()

        fun swipeBack() {
            composeTestRule.runOnUiThread {
                dispatcher.dispatchOnBackStarted(BackEventCompat(1f, 400f, 0f, BackEventCompat.EDGE_LEFT))
                dispatcher.dispatchOnBackProgressed(BackEventCompat(160f, 400f, 0.65f, BackEventCompat.EDGE_LEFT))
            }
            composeTestRule.waitForIdle()
        }

        swipeBack()
        search.assertExists()
        composeTestRule.runOnIdle { check(state.value.isSelectionMode) }
        composeTestRule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        search.assertDoesNotExist()
        composeTestRule.runOnIdle { check(state.value.isSelectionMode) }

        swipeBack()
        composeTestRule.runOnUiThread { dispatcher.onBackPressed() }
        search.assertIsDisplayed()
        composeTestRule.runOnIdle { check(!state.value.isSelectionMode) }
    }

    @Test
    fun searchScreenEmpty() = capture("SearchScreenEmpty", SearchStates.idle())

    @Test
    fun searchScreenLoading() = capture("SearchScreenLoading", SearchStates.loading())

    @Test
    fun searchScreenNoResult() = capture("SearchScreenNoResult", SearchStates.noResult())

    @Test
    fun searchScreenSelectedApp() = capture("SearchScreenSelectedApp", SearchStates.shown(SearchTab.APPS))

    @Test
    fun searchScreenSelectedComponent() = capture("SearchScreenSelectedComponent", SearchStates.shown(SearchTab.COMPONENTS))

    @Test
    fun searchScreenSelectedRule() = capture("SearchScreenSelectedRule", SearchStates.shown(SearchTab.RULES))

    @Test
    fun searchScreenSelectedMode() = capture("SearchScreenSelectedMode", SearchStates.selecting())

    @Test
    fun searchScreenBatchRunning() = capture("SearchScreenBatchRunning", SearchStates.batchRunning())

    @Test
    fun searchScreenIndexing() = capture("SearchScreenIndexing", SearchStates.indexing())

    @Test
    fun searchScreenBanners() = capture("SearchScreenBanners", SearchStates.banners())

    @Test
    fun searchScreenNoAccess() = capture("SearchScreenNoAccess", SearchStates.noAccess())

    @Test
    fun searchScreenProfiles() = capture("SearchScreenProfiles", SearchStates.workProfile())

    private fun capture(name: String, state: SearchUiState) {
        composeTestRule.captureMultiDevice(name) { Screen(state) }
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = name,
            darkMode = true,
        ) { Screen(state) }
    }

    @Composable
    private fun Screen(state: SearchUiState) {
        BlockerTheme {
            Surface {
                SearchScreen(uiState = state)
            }
        }
    }
}
