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

package com.merxury.blocker.feature.sort

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.AppSortInfo
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.feature.sort.AppSortInfoUiState.Loading
import com.merxury.blocker.feature.sort.AppSortInfoUiState.Success
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
class AppSortBottomSheetScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setTimeZone() {
        // Make time zone deterministic in tests
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun appSortBottomSheet() {
        composeTestRule.captureMultiDevice("AppSortBottomSheet") {
            AppSortBottomSheet()
        }
    }

    @Test
    fun appSortBottomSheet_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppSortBottomSheet",
            darkMode = true,
        ) {
            AppSortBottomSheet()
        }
    }

    @Test
    fun appSortBottomSheetLoading() {
        composeTestRule.captureMultiDevice("AppSortBottomSheetLoading") {
            AppSortBottomSheetLoading()
        }
    }

    @Test
    fun appSortBottomSheetLoading_dark() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "AppSortBottomSheetLoading",
            darkMode = true,
        ) {
            AppSortBottomSheetLoading()
        }
    }

    @Composable
    private fun AppSortBottomSheet() {
        BlockerTheme {
            Surface {
                ComponentSortBottomSheet(
                    uiState = Success(AppSortInfo()),
                )
            }
        }
    }

    @Composable
    private fun AppSortBottomSheetLoading() {
        BlockerTheme {
            Surface {
                ComponentSortBottomSheet(
                    uiState = Loading,
                )
            }
        }
    }
}
