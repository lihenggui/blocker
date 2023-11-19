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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.merxury.blocker.core.designsystem.segmentedbuttons.SegmentedButtons
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
class SegmentedButtonsScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun blockerSegmentedButtons_multipleThemes() {
        composeTestRule.captureMultiTheme("SegmentedButtons", "ThreeItems") {
            Surface {
                val list = listOf(
                    0 to R.string.core_designsystem_back,
                    1 to R.string.core_designsystem_clear_search_text_content_desc,
                    2 to R.string.core_designsystem_search_icon,
                )
                SegmentedButtons(
                    selectedValue = 0,
                    items = list,
                )
            }
        }
    }

    @Test
    fun blockerOutlineButton_multipleThemes() {
        composeTestRule.captureMultiTheme("SegmentedButtons", "TwoItems") {
            Surface {
                val list = listOf(
                    0 to R.string.core_designsystem_back,
                    1 to R.string.core_designsystem_clear_search_text_content_desc,
                )
                SegmentedButtons(
                    selectedValue = 0,
                    items = list,
                )
            }
        }
    }
}
