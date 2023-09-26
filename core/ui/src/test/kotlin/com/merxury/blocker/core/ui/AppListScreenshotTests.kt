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
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.AppServiceStatus
import com.merxury.blocker.core.testing.util.DefaultRoborazziOptions
import com.merxury.blocker.core.testing.util.captureMultiTheme
import com.merxury.blocker.core.ui.applist.AppList
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
class AppListScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun appList_multipleThemes() {
        composeTestRule.captureMultiTheme("AppList") {
            Surface {
                AppListExample()
            }
        }
    }

    @Test
    fun appList_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        AppListExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/AppList" +
                    "/AppList_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Composable
    private fun AppListExample() {
        val appList: List<AppItem> = listOf(
            AppItem(
                label = "Blocker",
                packageName = "com.merxury.blocker",
                versionName = "1.0.0",
                versionCode = 1,
                isEnabled = false,
                isRunning = true,
                appServiceStatus = AppServiceStatus(
                    packageName = "com.merxury.blocker",
                    running = 1,
                    blocked = 2,
                    total = 3,
                ),
            ),
            AppItem(
                label = "Blocker Test",
                packageName = "com.test.blocker",
                versionName = "11.0.0(1.1)",
                versionCode = 11,
                isEnabled = false,
                isRunning = false,
            ),
            AppItem(
                label = "Blocker Test test long long long long name",
                packageName = "com.test",
                versionName = "0.1.1",
                versionCode = 11,
                isEnabled = true,
                isRunning = true,
            ),
        )
        AppList(appList = appList)
    }
}
