/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.feature.applist

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.previewparameter.AppListPreviewParameterProvider
import com.merxury.blocker.feature.applist.AppListUiState.Error
import com.merxury.blocker.feature.applist.AppListUiState.Initializing
import com.merxury.blocker.feature.applist.AppListUiState.Success
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
@Config(application = HiltTestApplication::class, sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class AppListScreenScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setTimeZone() {
        // Make time zone deterministic in tests
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun appListScreen() {
        composeTestRule.captureMultiDevice("AppListScreen") {
            AppListScreen()
        }
    }

    @Test
    fun appListScreen_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppListScreen",
            darkMode = true,
        ) {
            AppListScreen()
        }
    }

    @Test
    fun appListScreen_initializing() {
        composeTestRule.captureMultiDevice("AppListScreenInitializing") {
            AppListInitialScreen()
        }
    }

    @Test
    fun appListScreen_initializing_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppListScreenInitializing",
            darkMode = true,
        ) {
            AppListInitialScreen()
        }
    }

    @Test
    fun appListScreen_error() {
        composeTestRule.captureMultiDevice("AppListScreenError") {
            AppListErrorScreen()
        }
    }

    @Test
    fun appListScreen_error_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppListScreenError",
            darkMode = true,
        ) {
            AppListErrorScreen()
        }
    }

    @Test
    fun appListScreen_empty() {
        composeTestRule.captureMultiDevice("AppListScreenEmpty") {
            AppListEmptyScreen()
        }
    }

    @Test
    fun appListScreen_empty_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppListScreenEmpty",
            darkMode = true,
        ) {
            AppListEmptyScreen()
        }
    }

    @Composable
    private fun AppListScreen() {
        val appList = AppListPreviewParameterProvider().values.first()
        BlockerTheme {
            Surface {
                AppListScreen(uiState = Success, appList = appList)
            }
        }
    }

    @Composable
    private fun AppListInitialScreen() {
        BlockerTheme {
            Surface {
                AppListScreen(uiState = Initializing("Blocker"), appList = listOf())
            }
        }
    }

    @Composable
    private fun AppListErrorScreen() {
        BlockerTheme {
            Surface {
                AppListScreen(uiState = Error(UiMessage("Error")), appList = listOf())
            }
        }
    }

    @Composable
    private fun AppListEmptyScreen() {
        BlockerTheme {
            Surface {
                AppListScreen(uiState = Success, appList = listOf())
            }
        }
    }
}
