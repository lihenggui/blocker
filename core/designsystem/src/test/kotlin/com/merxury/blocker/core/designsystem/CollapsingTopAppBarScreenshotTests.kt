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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.accompanist.testharness.TestHarness
import com.merxury.blocker.core.designsystem.R.drawable
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.BlockerCollapsingTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerSearchTextField
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
@Config(application = HiltTestApplication::class, qualifiers = "480dpi")
@LooperMode(LooperMode.Mode.PAUSED)
class CollapsingTopAppBarScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun collapsingTopAppBar_collapsed_multipleThemes() {
        composeTestRule.captureMultiTheme("CollapsingTopAppBar", "CollapsingTopAppBarCollapsed") {
            Surface {
                CollapsingToolbarCollapsedExample()
            }
        }
    }

    @Test
    fun collapsingTopAppBar_halfway_multipleThemes() {
        composeTestRule.captureMultiTheme("CollapsingTopAppBar", "CollapsingTopAppBarHalfway") {
            Surface {
                CollapsingToolbarHalfwayExample()
            }
        }
    }

    @Test
    fun collapsingTopAppBar_expanded_multipleThemes() {
        composeTestRule.captureMultiTheme("CollapsingTopAppBar", "CollapsingTopAppBarExpanded") {
            Surface {
                CollapsingToolbarExpandedExample()
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
                        CollapsingToolbarCollapsedExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/CollapsingTopAppBar/CollapsingTopAppBarCollapsed_fontScale2.png",
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
                        CollapsingToolbarHalfwayExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/CollapsingTopAppBar/CollapsingTopAppBarHalfway_fontScale2.png",
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
                        CollapsingToolbarExpandedExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/CollapsingTopAppBar/CollapsingTopAppBarExpanded_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Composable
    private fun CollapsingToolbarCollapsedExample() {
        BlockerCollapsingTopAppBar(
            progress = 0f,
            title = "Title",
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { },
                    ) {
                        BlockerActionIcon(
                            imageVector = BlockerIcons.Search,
                            contentDescription = stringResource(id = R.string.core_designsystem_search_icon),
                        )
                    }
                    BlockerAppTopBarMenu(
                        menuIcon = BlockerIcons.MoreVert,
                        menuIconDesc = R.string.core_designsystem_more_icon,
                        menuList = listOf(),
                    )
                }
            },
            subtitle = "packageName",
            summary = "versionCode",
            iconSource = drawable.core_designsystem_ic_android,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        )
    }

    @Composable
    private fun CollapsingToolbarHalfwayExample() {
        BlockerCollapsingTopAppBar(
            progress = 0.5f,
            title = "Title with long name 0123456789",
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { },
                    ) {
                        BlockerActionIcon(
                            imageVector = BlockerIcons.Search,
                            contentDescription = stringResource(id = R.string.core_designsystem_search_icon),
                        )
                    }
                    BlockerAppTopBarMenu(
                        menuIcon = BlockerIcons.MoreVert,
                        menuIconDesc = R.string.core_designsystem_more_icon,
                        menuList = listOf(),
                    )
                }
            },
            subtitle = "packageName",
            summary = "versionCode",
            iconSource = drawable.core_designsystem_ic_android,
            modifier = Modifier
                .fillMaxWidth()
                .height(94.dp),
        )
    }

    @Composable
    private fun CollapsingToolbarExpandedExample() {
        BlockerCollapsingTopAppBar(
            progress = 1f,
            title = "Title with long name 0123456789",
            actions = {
                BlockerSearchTextField(
                    searchQuery = "blocker",
                    onSearchQueryChanged = { },
                    onSearchTriggered = { },
                    modifier = Modifier.weight(1f),
                )
                BlockerAppTopBarMenu(
                    menuIcon = BlockerIcons.MoreVert,
                    menuIconDesc = R.string.core_designsystem_more_icon,
                    menuList = listOf(),
                )
            },
            subtitle = "packageName with long long long name 0123456789",
            summary = "versionCode with long long long name 0123456789",
            iconSource = drawable.core_designsystem_ic_android,
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp),
        )
    }
}
