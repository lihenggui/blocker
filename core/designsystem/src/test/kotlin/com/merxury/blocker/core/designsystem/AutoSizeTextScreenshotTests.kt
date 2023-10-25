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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.AutoSizeText
import com.merxury.blocker.core.designsystem.component.BlockerBackground
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
@Config(application = HiltTestApplication::class, sdk = [33], qualifiers = "480dpi")
@LooperMode(LooperMode.Mode.PAUSED)
class AutoSizeTextScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun blockerAutoSizeText_small_multipleThemes() {
        composeTestRule.captureMultiTheme("AutoSizeText", "Small") { description ->
            BlockerBackground(Modifier.size(width = 60.dp, height = 30.dp)) {
                AutoSizeText("AutoSizeText $description")
            }
        }
    }

    @Test
    fun blockerAutoSizeText_medium_multipleThemes() {
        composeTestRule.captureMultiTheme("AutoSizeText", "Medium") { description ->
            BlockerBackground(Modifier.size(width = 200.dp, height = 30.dp)) {
                AutoSizeText("AutoSizeText $description")
            }
        }
    }

    @Test
    fun blockerAutoSizeText_large_multipleThemes() {
        composeTestRule.captureMultiTheme("AutoSizeText", "Large") { description ->
            BlockerBackground(Modifier.size(width = 200.dp, height = 100.dp)) {
                AutoSizeText("AutoSizeText $description")
            }
        }
    }
}
