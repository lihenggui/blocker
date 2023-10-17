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
import com.merxury.blocker.core.testing.util.DefaultRoborazziOptions
import com.merxury.blocker.core.testing.util.captureMultiTheme
import com.merxury.blocker.core.ui.applist.AppListItem
import com.merxury.blocker.core.ui.previewparameter.AppListPreviewParameterProvider
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
                AppListItemExample(0)
            }
        }
    }

    @Test
    fun appListItem_simple_multipleThemes() {
        composeTestRule.captureMultiTheme("AppListItem", "AppListItemSimple") {
            Surface {
                AppListItemExample(1)
            }
        }
    }

    @Test
    fun appListItem_longName_multipleThemes() {
        composeTestRule.captureMultiTheme("AppListItem", "AppListItemLongName") {
            Surface {
                AppListItemExample(2)
            }
        }
    }

    @Test
    fun appListItem_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        AppListItemExample(0)
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
    private fun AppListItemExample(order: Int) {
        val appList = AppListPreviewParameterProvider().values.first()
        val appItem = when (order) {
            0 -> appList[0]
            1 -> appList[1]
            else -> appList[2]
        }
        AppListItem(
            label = appItem.label,
            packageName = appItem.packageName,
            versionName = appItem.versionName,
            versionCode = appItem.versionCode,
            isAppEnabled = appItem.isEnabled,
            isAppRunning = appItem.isRunning,
            appServiceStatus = appItem.appServiceStatus,
        )
    }
}
