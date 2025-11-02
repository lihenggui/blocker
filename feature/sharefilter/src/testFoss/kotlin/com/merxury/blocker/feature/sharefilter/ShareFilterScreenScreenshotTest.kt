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

package com.merxury.blocker.feature.sharefilter

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
@HiltAndroidTest
class ShareFilterScreenScreenshotTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun givenShareFilterScreen_thenCaptureScreenshot() {
        composeTestRule.captureMultiDevice("ShareFilterScreen") {
            ShareFilterScreenContent()
        }
    }

    @Test
    fun givenShareFilterScreenDark_thenCaptureScreenshot() {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = "ShareFilterScreen",
            darkMode = true,
        ) {
            ShareFilterScreenContent()
        }
    }

    @Composable
    private fun ShareFilterScreenContent() {
        BlockerTheme {
            Surface {
                ShareFilterScreen(
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
    }
}
