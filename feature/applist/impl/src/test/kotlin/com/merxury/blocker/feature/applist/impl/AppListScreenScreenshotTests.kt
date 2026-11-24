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

package com.merxury.blocker.feature.applist.impl

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.core.ui.applist.previewAppRows
import com.merxury.blocker.core.ui.profile.ProfileLabel
import com.merxury.blocker.core.ui.profile.ProfileTab
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AppListScreenScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val ownApps = AppListUiState(ownUser = OWNER, selectedUser = OWNER, apps = AppsUiState.Loaded(previewAppRows()))
    private val profiles = listOf(
        ProfileTab(OWNER, ProfileLabel.Personal, running = true),
        ProfileTab(WORK, ProfileLabel.Work, running = true),
    )

    @Before
    fun setTimeZone() {
        // Make time zone deterministic in tests
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun appListScreen() = captureWithDark("AppListScreen", ownApps)

    @Test
    fun appListScreen_profiles() = captureWithDark(
        "AppListScreenProfiles",
        ownApps.copy(selectedUser = WORK, profiles = profiles, apps = AppsUiState.Loaded(previewAppRows(WORK))),
    )

    @Test
    fun appListScreen_stoppedProfile() = captureWithDark(
        "AppListScreenStoppedProfile",
        ownApps.copy(profiles = listOf(profiles[0], profiles[1].copy(running = false))),
    )

    @Test
    fun appListScreen_loading() = captureWithDark("AppListScreenLoading", ownApps.copy(apps = AppsUiState.Loading))

    @Test
    fun appListScreen_error() = captureWithDark(
        "AppListScreenError",
        ownApps.copy(apps = AppsUiState.Error(ReadFailure.ChannelUnavailable)),
    )

    @Test
    fun appListScreen_refreshFailed() = captureWithDark(
        "AppListScreenRefreshFailed",
        ownApps.copy(refreshFailure = ReadFailure.ChannelUnavailable),
    )

    @Test
    fun appListScreen_empty() = captureWithDark("AppListScreenEmpty", ownApps.copy(apps = AppsUiState.Loaded(emptyList())))

    private fun captureWithDark(name: String, uiState: AppListUiState) {
        composeTestRule.captureMultiDevice(name) { Screen(uiState) }
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = name,
            darkMode = true,
        ) { Screen(uiState) }
    }

    @Composable
    private fun Screen(uiState: AppListUiState) {
        BlockerTheme {
            Surface {
                AppListScreen(uiState = uiState)
            }
        }
    }
}
