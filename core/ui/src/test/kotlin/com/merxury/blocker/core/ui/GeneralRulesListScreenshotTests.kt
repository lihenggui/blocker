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
import com.merxury.blocker.core.ui.rule.GeneralRulesList
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
class GeneralRulesListScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun generalRulesListWithMatchedAndUnmatched_multipleThemes() {
        composeTestRule.captureMultiTheme("GeneralRulesList") {
            Surface {
                GeneralRulesListWithMatchedAndUnmatchedExample()
            }
        }
    }

    @Test
    fun generalRulesListWithMatchedApp_multipleThemes() {
        composeTestRule.captureMultiTheme("GeneralRulesList", "OnlyWithMatchedApp") {
            Surface {
                GeneralRulesListWithMatchedExample()
            }
        }
    }

    @Test
    fun generalRulesListWithUnmatchedApp_multipleThemes() {
        composeTestRule.captureMultiTheme("GeneralRulesList", "OnlyWithUnmatchedApp") {
            Surface {
                GeneralRulesListWithUnmatchedExample()
            }
        }
    }

    @Test
    fun generalRulesList_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        GeneralRulesListWithMatchedAndUnmatchedExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/GeneralRulesList" +
                    "/GeneralRulesList_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Composable
    private fun GeneralRulesListWithMatchedAndUnmatchedExample() {
        val matchedRules = listOf(
            GeneralRule(
                id = 1,
                name = "AWS SDK for Kotlin (Developer Preview)",
                iconUrl = null,
                company = "Amazon",
                description = "The AWS SDK for Kotlin simplifies the use of AWS services by " +
                    "providing a set of libraries that are consistent and familiar for " +
                    "Kotlin developers. All AWS SDKs support API lifecycle considerations " +
                    "such as credential management, retries, data marshaling, and serialization.",
                sideEffect = "Unknown",
                safeToBlock = true,
                contributors = listOf("Online contributor"),
                searchKeyword = listOf("androidx.google.example1"),
                matchedAppCount = 5,
            ),

        )
        val unmatchedRules = listOf(
            GeneralRule(
                id = 2,
                name = "Android WorkerManager",
                iconUrl = null,
                company = "Google",
                description = "WorkManager is the recommended solution for persistent work. " +
                    "Work is persistent when it remains scheduled through app restarts and " +
                    "system reboots. Because most background processing is best accomplished " +
                    "through persistent work, WorkManager is the primary recommended API for " +
                    "background processing.",
                sideEffect = "Background works won't be able to execute",
                safeToBlock = false,
                contributors = listOf("Google"),
                searchKeyword = listOf(
                    "androidx.google.example1",
                    "androidx.google.example2",
                    "androidx.google.example3",
                    "androidx.google.example4",
                ),
            ),
        )
        GeneralRulesList(matchedRules = matchedRules, unmatchedRules = unmatchedRules)
    }

    @Composable
    private fun GeneralRulesListWithMatchedExample() {
        val matchedRules = listOf(
            GeneralRule(
                id = 1,
                name = "AWS SDK for Kotlin (Developer Preview)",
                iconUrl = null,
                company = "Amazon",
                description = "The AWS SDK for Kotlin simplifies the use of AWS services by " +
                    "providing a set of libraries that are consistent and familiar for " +
                    "Kotlin developers. All AWS SDKs support API lifecycle considerations " +
                    "such as credential management, retries, data marshaling, and serialization.",
                sideEffect = "Unknown",
                safeToBlock = true,
                contributors = listOf("Online contributor"),
                searchKeyword = listOf("androidx.google.example1"),
                matchedAppCount = 5,
            ),
            GeneralRule(
                id = 2,
                name = "Android WorkerManager",
                iconUrl = null,
                company = "Google",
                description = "WorkManager is the recommended solution for persistent work. " +
                    "Work is persistent when it remains scheduled through app restarts and " +
                    "system reboots. Because most background processing is best accomplished " +
                    "through persistent work, WorkManager is the primary recommended API for " +
                    "background processing.",
                sideEffect = "Background works won't be able to execute",
                safeToBlock = false,
                contributors = listOf("Google"),
                searchKeyword = listOf(
                    "androidx.google.example1",
                    "androidx.google.example2",
                    "androidx.google.example3",
                    "androidx.google.example4",
                ),
                matchedAppCount = 13,
            ),
        )
        GeneralRulesList(matchedRules = matchedRules, unmatchedRules = listOf())
    }

    @Composable
    private fun GeneralRulesListWithUnmatchedExample() {
        val unmatchedRules = listOf(
            GeneralRule(
                id = 1,
                name = "AWS SDK for Kotlin (Developer Preview)",
                iconUrl = null,
                company = "Amazon",
                description = "The AWS SDK for Kotlin simplifies the use of AWS services by " +
                    "providing a set of libraries that are consistent and familiar for " +
                    "Kotlin developers. All AWS SDKs support API lifecycle considerations " +
                    "such as credential management, retries, data marshaling, and serialization.",
                sideEffect = "Unknown",
                safeToBlock = true,
                contributors = listOf("Online contributor"),
                searchKeyword = listOf("androidx.google.example1"),
            ),
            GeneralRule(
                id = 2,
                name = "Android WorkerManager",
                iconUrl = null,
                company = "Google",
                description = "WorkManager is the recommended solution for persistent work. " +
                    "Work is persistent when it remains scheduled through app restarts and " +
                    "system reboots. Because most background processing is best accomplished " +
                    "through persistent work, WorkManager is the primary recommended API for " +
                    "background processing.",
                sideEffect = "Background works won't be able to execute",
                safeToBlock = false,
                contributors = listOf("Google"),
                searchKeyword = listOf(
                    "androidx.google.example1",
                    "androidx.google.example2",
                    "androidx.google.example3",
                    "androidx.google.example4",
                ),
            ),
        )
        GeneralRulesList(matchedRules = listOf(), unmatchedRules = unmatchedRules)
    }
}
