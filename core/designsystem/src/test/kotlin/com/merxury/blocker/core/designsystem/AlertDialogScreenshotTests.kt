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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.accompanist.testharness.TestHarness
import com.merxury.blocker.core.designsystem.component.BlockerConfirmAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
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
@Config(application = HiltTestApplication::class, sdk = [33], qualifiers = "480dpi")
@LooperMode(LooperMode.Mode.PAUSED)
class AlertDialogScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun confirmAlertDialog_multipleThemes() {
        composeTestRule.captureMultiTheme("AlertDialog", "ConfirmAlertDialog") {
            Surface {
                BlockerConfirmAlertDialogExample()
            }
        }
    }

    @Test
    fun warningAlertDialog_multipleThemes() {
        composeTestRule.captureMultiTheme("AlertDialog", "WarningAlertDialog") {
            Surface {
                BlockerWarningAlertDialogExample()
            }
        }
    }

    @Test
    fun errorAlertDialog_multipleThemes() {
        composeTestRule.captureMultiTheme("AlertDialog", "ErrorAlertDialog") {
            Surface {
                BlockerErrorAlertDialogExample()
            }
        }
    }

    @Test
    fun confirmAlertDialog_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        BlockerConfirmAlertDialogExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/AlertDialog/ConfirmAlertDialog_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Test
    fun warningAlertDialog_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        BlockerWarningAlertDialogExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/AlertDialog/WarningAlertDialog_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Test
    fun errorAlertDialog_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        BlockerErrorAlertDialogExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/AlertDialog/ErrorAlertDialog_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Composable
    private fun BlockerConfirmAlertDialogExample() {
        BlockerConfirmAlertDialog(
            text = "This operation will block 4 components, do you want to continue?",
            onDismissRequest = {},
            onConfirmRequest = {},
        )
    }

    @Composable
    private fun BlockerWarningAlertDialogExample() {
        BlockerWarningAlertDialog(
            title = "Warning",
            text = "Warning message",
            onDismissRequest = {},
            onConfirmRequest = {},
        )
    }

    @Composable
    private fun BlockerErrorAlertDialogExample() {
        BlockerErrorAlertDialog(
            title = "Error",
            text = "Error message",
            onDismissRequest = {},
        )
    }
}
