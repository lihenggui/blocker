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

package com.merxury.blocker.feature.applist

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.AppSortInfo
import com.merxury.blocker.core.testing.util.DefaultTestDevices.PHONE
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.feature.applist.AppSortInfoUiState.Loading
import com.merxury.blocker.feature.applist.AppSortInfoUiState.Success
import com.merxury.blocker.feature.applist.component.ComponentSortBottomSheet
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.GraphicsMode.Mode.NATIVE
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.LooperMode.Mode.PAUSED
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(NATIVE)
@Config(application = HiltTestApplication::class)
@LooperMode(PAUSED)
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
            deviceSpec = PHONE.spec,
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
            deviceSpec = PHONE.spec,
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