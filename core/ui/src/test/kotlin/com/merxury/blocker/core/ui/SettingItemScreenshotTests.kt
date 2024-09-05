/*
 * Copyright 2024 Blocker
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
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.testing.util.DefaultRoborazziOptions
import com.merxury.blocker.core.testing.util.captureMultiTheme
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
@Config(application = HiltTestApplication::class, qualifiers = "480dpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingItemScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun itemHeader_multipleThemes() {
        composeTestRule.captureMultiTheme("SettingItem", "ItemHeader") {
            Surface {
                ItemHeaderExample()
            }
        }
    }

    @Test
    fun itemHeader_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        ItemHeaderExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/SettingItem" +
                    "/ItemHeader_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Test
    fun headerWithPadding_multipleThemes() {
        composeTestRule.captureMultiTheme("SettingItem", "HeaderWithPadding") {
            Surface {
                ItemHeaderExample(extraIconPadding = true)
            }
        }
    }

    @Test
    fun headerWithPadding_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        ItemHeaderExample(extraIconPadding = true)
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/SettingItem" +
                    "/HeaderWithPadding_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Test
    fun settingItem_singleLine_multipleThemes() {
        composeTestRule.captureMultiTheme("SettingItem", "SingleLine") {
            Surface {
                SettingItemExample()
            }
        }
    }

    @Test
    fun settingItem_withSummary_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        SettingItemExample(hasSummary = true)
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/SettingItem" +
                    "/SettingItemWithSummary_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Test
    fun settingItem_withSummaryPadding_multipleThemes() {
        composeTestRule.captureMultiTheme("SettingItem", "WithSummaryPadding") {
            Surface {
                SettingItemExample(hasSummary = true, extraIconPadding = true)
            }
        }
    }

    @Test
    fun settingItem_withSummaryIcon_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        SettingItemExample(
                            hasSummary = true,
                            hasIcon = true,
                        )
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/SettingItem" +
                    "/SettingItemWithSummaryPaddingIcon_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Test
    fun settingItem_withDesc_multipleThemes() {
        composeTestRule.captureMultiTheme("SettingItem", "WithDesc") {
            Surface {
                SettingItemWithDescExample()
            }
        }
    }

    @Composable
    private fun ItemHeaderExample(extraIconPadding: Boolean = false) {
        BlockerTheme {
            Surface {
                ItemHeader(
                    title = "Item header title",
                    extraIconPadding = extraIconPadding,
                )
            }
        }
    }

    @Composable
    private fun SettingItemExample(
        hasSummary: Boolean = false,
        hasIcon: Boolean = false,
        extraIconPadding: Boolean = false,
    ) {
        BlockerTheme {
            Surface {
                BlockerSettingItem(
                    title = "setting item title",
                    extraIconPadding = extraIconPadding,
                    summary = if (hasSummary) "setting item summary" else null,
                    icon = if (hasIcon) ImageVectorIcon(BlockerIcons.Apps) else null,
                )
            }
        }
    }

    @Composable
    private fun SettingItemWithDescExample() {
        BlockerTheme {
            Surface {
                BlockerSettingItem(
                    title = "Setting item title",
                    itemDesc = "description",
                    itemDesc1 = "description1",
                )
            }
        }
    }
}
