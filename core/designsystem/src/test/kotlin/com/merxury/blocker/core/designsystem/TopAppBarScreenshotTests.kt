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

import android.R.string
import androidx.activity.ComponentActivity
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.accompanist.testharness.TestHarness
import com.merxury.blocker.core.designsystem.component.BlockerMediumTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBarWithProgress
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
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
class TopAppBarScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun topAppBar_multipleThemes() {
        composeTestRule.captureMultiTheme("TopAppBar") {
            Surface {
                BlockerTopAppBarExample()
            }
        }
    }

    @Test
    fun topAppBar_navActions_multipleThemes() {
        composeTestRule.captureMultiTheme("TopAppBar", "TopAppBarWithNavActions") {
            Surface {
                BlockerTopAppBarWithNavActionsExample()
            }
        }
    }

    @Test
    fun mediumTopAppBar_multipleThemes() {
        composeTestRule.captureMultiTheme("TopAppBar", "MediumTopAppBar") {
            Surface {
                BlockerMediumTopAppBarExample()
            }
        }
    }

    @Test
    fun topAppBar_loading_start_multipleThemes() {
        composeTestRule.captureMultiTheme("TopAppBar", "TopAppBarWithLoadingStart") {
            Surface {
                BlockerTopAppBarWithLoadingExample(0F)
            }
        }
    }

    @Test
    fun topAppBar_loading_progress_multipleThemes() {
        composeTestRule.captureMultiTheme("TopAppBar", "TopAppBarWithLoadingProgress") {
            Surface {
                BlockerTopAppBarWithLoadingExample(0.5F)
            }
        }
    }

    @Test
    fun topAppBar_loading_complete_multipleThemes() {
        composeTestRule.captureMultiTheme("TopAppBar", "TopAppBarWithLoadingComplete") {
            Surface {
                BlockerTopAppBarWithLoadingExample(1F)
            }
        }
    }

    @Test
    fun topAppBar_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        BlockerTopAppBarExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/TopAppBar/TopAppBar_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Test
    fun topAppBar_navActions_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        BlockerTopAppBarWithNavActionsExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/TopAppBar/TopAppBar_navActions_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Test
    fun mediumTopAppBar_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        BlockerMediumTopAppBarExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/TopAppBar/MediumTopAppBar_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Test
    fun topAppBar_loading_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                TestHarness(fontScale = 2f) {
                    BlockerTheme {
                        BlockerTopAppBarWithLoadingExample(0.5F)
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/TopAppBar/TopAppBar_loading_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Composable
    private fun BlockerTopAppBarExample() {
        BlockerTopAppBarWithProgress(
            title = stringResource(id = string.untitled),
        )
    }

    @Composable
    private fun BlockerTopAppBarWithNavActionsExample() {
        BlockerTopAppBar(
            title = stringResource(id = string.untitled),
            hasNavigationIcon = true,
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = BlockerIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }

    @Composable
    private fun BlockerMediumTopAppBarExample() {
        BlockerMediumTopAppBar(
            title = stringResource(id = string.untitled),
            navigation = {
                IconButton(onClick = {}) {
                    BlockerActionIcon(
                        imageVector = BlockerIcons.Close,
                        contentDescription = null,
                    )
                }
            },
            actions = {
                IconButton(onClick = {}) {
                    BlockerActionIcon(
                        imageVector = BlockerIcons.SelectAll,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = {}) {
                    BlockerActionIcon(
                        imageVector = BlockerIcons.Block,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = {}) {
                    BlockerActionIcon(
                        imageVector = BlockerIcons.CheckCircle,
                        contentDescription = null,
                    )
                }
            },
        )
    }

    @Composable
    private fun BlockerTopAppBarWithLoadingExample(
        progress: Float,
    ) {
        BlockerTopAppBarWithProgress(
            title = stringResource(id = string.untitled),
            progress = progress,
        )
    }
}
