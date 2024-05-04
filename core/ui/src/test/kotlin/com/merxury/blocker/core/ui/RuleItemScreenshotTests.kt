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
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.testing.util.DefaultRoborazziOptions
import com.merxury.blocker.core.testing.util.captureMultiTheme
import com.merxury.blocker.core.ui.rule.RuleItem
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
class RuleItemScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ruleItem_multipleThemes() {
        composeTestRule.captureMultiTheme("RuleItem") {
            Surface {
                RuleItemExample()
            }
        }
    }

    @Test
    fun ruleItem_simple_multipleThemes() {
        composeTestRule.captureMultiTheme("RuleItem", "RuleItemSimple") {
            Surface {
                RuleItemExample(isSimple = true)
            }
        }
    }

    @Test
    fun ruleItem_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        RuleItemExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/RuleItem" +
                    "/RuleItem_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Composable
    private fun RuleItemExample(isSimple: Boolean = false) {
        val item = if (!isSimple) {
            GeneralRule(
                id = 2,
                name = "Android WorkerManager",
                iconUrl = null,
                company = "Google",
                description = "WorkManager is the recommended solution for persistent work. " + "Work is persistent when it remains scheduled through app restarts and " + "system reboots. Because most background processing is best accomplished " + "through persistent work, WorkManager is the primary recommended API for " + "background processing.",
                sideEffect = "Background works won't be able to execute",
                safeToBlock = false,
                contributors = listOf("Google"),
                searchKeyword = listOf("androidx.work.", "androidx.work.impl"),
                matchedAppCount = 10,
            )
        } else {
            GeneralRule(
                id = 3,
                name = "Android WorkerManager Test",
            )
        }
        RuleItem(item = item)
    }
}
