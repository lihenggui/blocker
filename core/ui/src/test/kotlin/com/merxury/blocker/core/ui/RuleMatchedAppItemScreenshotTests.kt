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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.accompanist.testharness.TestHarness
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.ComponentItem
import com.merxury.blocker.core.testing.util.DefaultRoborazziOptions
import com.merxury.blocker.core.testing.util.captureMultiTheme
import com.merxury.blocker.core.ui.rule.MatchedAppItemHeader
import com.merxury.blocker.core.ui.rule.RuleMatchedApp
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
class RuleMatchedAppItemScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ruleMatchedAppItem_multipleThemes() {
        composeTestRule.captureMultiTheme("RuleMatchedAppItem") {
            Surface {
                RuleMatchedAppItemExample()
            }
        }
    }

    @Test
    fun ruleMatchedAppItem_longName_multipleThemes() {
        composeTestRule.captureMultiTheme("RuleMatchedAppItem", "RuleMatchedAppItemLongName") {
            Surface {
                RuleMatchedAppItemExample(isLongName = true)
            }
        }
    }

    @Test
    fun ruleMatchedAppItem_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        RuleMatchedAppItemExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/RuleMatchedAppItem" +
                    "/RuleMatchedAppItem_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Composable
    private fun RuleMatchedAppItemExample(isLongName: Boolean = false) {
        val label = if (isLongName) {
            "This is a very long long long long long long name "
        } else {
            "Blocker"
        }
        val componentList = remember {
            mutableStateListOf(
                ComponentItem(
                    packageName = "com.merxury.example",
                    name = "com.merxury.example.MainActivity",
                    simpleName = "MainActivity",
                    pmBlocked = true,
                    type = ACTIVITY,
                ),
                ComponentItem(
                    packageName = "com.merxury.example",
                    name = "com.merxury.example.provider",
                    simpleName = "example",
                    type = PROVIDER,
                    pmBlocked = false,
                ),
            )
        }
        val ruleMatchedApp = RuleMatchedApp(
            app = AppItem(
                packageName = "com.merxury.example",
                label = label,
                packageInfo = null,
            ),
            componentList = componentList,
        )
        MatchedAppItemHeader(
            ruleMatchedApp = ruleMatchedApp,
            expanded = isLongName,
        )
    }
}
