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
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.testing.util.DefaultRoborazziOptions
import com.merxury.blocker.core.testing.util.captureMultiTheme
import com.merxury.blocker.core.ui.component.ComponentListItem
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider
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
class ComponentListItemScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val components = ComponentListPreviewParameterProvider().values.first()

    @Test
    fun componentListItem_multipleThemes() {
        composeTestRule.captureMultiTheme("ComponentListItem") {
            BlockerTheme {
                Surface {
                    ComponentListItemExample()
                }
            }
        }
    }

    @Test
    fun appListItem_selected_multipleThemes() {
        composeTestRule.captureMultiTheme("ComponentListItem", "ComponentListItemSelected") {
            BlockerTheme {
                Surface {
                    ComponentListItem(
                        item = components[1],
                        enabled = false,
                        type = RECEIVER,
                        isServiceRunning = true,
                        isSelectedMode = true,
                        isSelected = true,
                    )
                }
            }
        }
    }

    @Test
    fun componentListItem_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    ComponentListItemExample()
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/ComponentListItem" +
                    "/ComponentListItem_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Composable
    private fun ComponentListItemExample() {
        BlockerTheme {
            Surface {
                ComponentListItem(
                    item = components[0],
                    enabled = true,
                    type = ACTIVITY,
                    isServiceRunning = true,
                )
            }
        }
    }
}
