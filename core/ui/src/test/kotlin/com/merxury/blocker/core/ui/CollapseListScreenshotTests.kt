/*
 * Copyright 2025 Blocker
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
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.testing.util.DefaultRoborazziOptions
import com.merxury.blocker.core.testing.util.captureMultiTheme
import com.merxury.blocker.core.ui.collapseList.CollapsibleList
import com.merxury.blocker.core.ui.collapseList.collapsibleGroup
import com.merxury.blocker.core.ui.component.ComponentListItem
import com.merxury.blocker.core.ui.previewparameter.previewComponentRows
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
class CollapseListScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun collapseList_multipleThemes() {
        composeTestRule.captureMultiTheme("CollapseList") {
            Surface {
                CollapseListExample()
            }
        }
    }

    @Test
    fun collapseList_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.FontScale(2f),
                ) {
                    BlockerTheme {
                        CollapseListExample()
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/CollapseList" +
                    "/CollapseList_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Composable
    private fun CollapseListExample() {
        val rows = previewComponentRows()
        BlockerTheme {
            Surface {
                CollapsibleList(
                    groups = listOf(
                        collapsibleGroup(key = "com.merxury.blocker", title = "Blocker", icon = null, rows = rows),
                        collapsibleGroup(key = "com.test.blocker", title = "Blocker Test", icon = null, rows = emptyList()),
                        collapsibleGroup(key = "com.test", title = "Blocker Test test long long long long name", icon = null, rows = emptyList()),
                    ),
                    itemKey = { it.id.className },
                ) { row, modifier ->
                    ComponentListItem(
                        simpleName = row.id.simpleName,
                        name = row.id.className,
                        enabled = row.enabled,
                        type = row.type,
                        isServiceRunning = row.isRunning,
                        modifier = modifier,
                    )
                }
            }
        }
    }
}
