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
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.merxury.blocker.core.designsystem.component.BlockerViewToggleButton
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
class ViewToggleScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun iconButton_multipleThemes() {
        composeTestRule.captureMultiTheme("ViewToggle") {
            Surface {
                BlockerViewToggleExample(false)
            }
        }
    }

    @Test
    fun iconButton_unchecked_multipleThemes() {
        composeTestRule.captureMultiTheme("ViewToggle", "ViewToggleExpanded") {
            Surface {
                BlockerViewToggleExample(true)
            }
        }
    }

    @Composable
    private fun BlockerViewToggleExample(checked: Boolean) {
        BlockerViewToggleButton(
            expanded = checked,
            onExpandedChange = { },
            compactText = { Text(text = "Compact view") },
            expandedText = { Text(text = "Expanded view") },
        )
    }
}
