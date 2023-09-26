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

package com.merxury.blocker.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.accompanist.testharness.TestHarness
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.AppServiceStatus
import com.merxury.blocker.core.testing.util.DefaultRoborazziOptions
import com.merxury.blocker.core.testing.util.captureMultiTheme
import com.merxury.blocker.core.ui.applist.AppListItem
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, sdk = [33], qualifiers = "480dpi")
@LooperMode(LooperMode.Mode.PAUSED)
class AppListItemScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun appListItem_multipleThemes() {
        composeTestRule.captureMultiTheme("AppListItem") {
            Surface {
                AppListItemExample()
            }
        }
    }

    @Test
    fun appListItem_simple_multipleThemes() {
        composeTestRule.captureMultiTheme("AppListItem", "AppListItemSimple") {
            Surface {
                AppListItemExample(
                    isAppEnable = true,
                    isAppRunning = false,
                    showAppServiceStatus = false,
                    isLongName = false,
                )
            }
        }
    }

    @Test
    fun appListItem_longName_multipleThemes() {
        composeTestRule.captureMultiTheme("AppListItem", "AppListItemLongName") {
            Surface {
                AppListItemExample(
                    isAppEnable = true,
                    isAppRunning = true,
                    showAppServiceStatus = true,
                    isLongName = true,
                )
            }
        }
    }

    @Test
    fun navigation_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        AppListItemExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/AppListItem" +
                    "/AppListItem_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Composable
    private fun AppListItemExample(
        isAppEnable: Boolean = false,
        isAppRunning: Boolean = true,
        showAppServiceStatus: Boolean = true,
        isLongName: Boolean = false,
    ) {
        val appServiceStatus = if (showAppServiceStatus) {
            AppServiceStatus(
                running = 1,
                blocked = 2,
                total = 10,
                packageName = "com.merxury.blocker",
            )
        } else {
            null
        }
        val label = if (isLongName) {
            "App Name With Very Long Long Long Long Long Long Name"
        } else {
            "Blocker"
        }
        AppListItem(
            label = label,
            packageName = "com.merxury.blocker",
            versionName = "1.0.12",
            versionCode = 1012,
            isAppEnabled = isAppEnable,
            isAppRunning = isAppRunning,
            appServiceStatus = appServiceStatus,
        )
    }
}
