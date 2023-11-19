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

package com.merxury.blocker.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.accompanist.testharness.TestHarness
import com.merxury.blocker.core.designsystem.component.BlockerBackground
import com.merxury.blocker.core.designsystem.component.BlockerFilterChip
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.testing.util.DefaultRoborazziOptions
import com.merxury.blocker.core.testing.util.captureMultiTheme
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "480dpi")
@LooperMode(LooperMode.Mode.PAUSED)
class FilterChipScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun filterChip_multipleThemes() {
        composeTestRule.captureMultiTheme("FilterChip") {
            Surface {
                BlockerFilterChip(selected = false, onSelectedChange = {}) {
                    Text("Unselected chip")
                }
            }
        }
    }

    @Test
    fun filterChip_multipleThemes_selected() {
        composeTestRule.captureMultiTheme("FilterChip", "FilterChipSelected") {
            Surface {
                BlockerFilterChip(selected = true, onSelectedChange = {}) {
                    Text("Selected Chip")
                }
            }
        }
    }

    @Test
    fun filterChip_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f, size = DpSize(80.dp, 40.dp)) {
                    BlockerTheme {
                        BlockerBackground {
                            BlockerFilterChip(selected = true, onSelectedChange = {}) {
                                Text("Chip")
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/FilterChip/FilterChip_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }
}
