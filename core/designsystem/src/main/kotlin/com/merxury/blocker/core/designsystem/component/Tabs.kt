/*
 * Copyright 2024 Blocker
 * Copyright 2022 The Android Open Source Project
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

package com.merxury.blocker.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.PrimaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.theme.BlockerTheme

/**
 * Blocker tab. Wraps Material 3 [Tab] and shifts text label down.
 *
 * @param selected Whether this tab is selected or not.
 * @param onClick The callback to be invoked when this tab is selected.
 * @param modifier Modifier to be applied to the tab.
 * @param enabled Controls the enabled state of the tab. When `false`, this tab will not be
 * clickable and will appear disabled to accessibility services.
 * @param text The text label content.
 */
@Composable
fun BlockerTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: @Composable () -> Unit,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        text = {
            val style = MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center)
            ProvideTextStyle(
                value = style,
                content = {
                    Box(modifier = Modifier.padding(top = BlockerTabDefaults.TabTopPadding)) {
                        text()
                    }
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerScrollableTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = TabRowDefaults.primaryContainerColor,
    contentColor: Color = TabRowDefaults.primaryContentColor,
    tabs: @Composable () -> Unit,
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        edgePadding = 16.dp,
        modifier = modifier.wrapContentWidth(),
        tabs = tabs,
        containerColor = containerColor,
        contentColor = contentColor,
        indicator = {
            PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selectedTabIndex),
                height = 2.dp,
                color = contentColor,
            )
        },
    )
}

/**
 * Blocker tab row. Wraps Material 3 [TabRow].
 *
 * @param selectedTabIndex The index of the currently selected tab.
 * @param modifier Modifier to be applied to the tab row.
 * @param tabs The tabs inside this tab row. Typically this will be multiple [BlockerTab]s. Each element
 * inside this lambda will be measured and placed evenly across the row, each taking up equal space.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = TabRowDefaults.primaryContainerColor,
    contentColor: Color = TabRowDefaults.primaryContentColor,
    tabs: @Composable () -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        tabs = tabs,
    )
}

object BlockerTabDefaults {
    val TabTopPadding = 2.dp
}

@PreviewThemes
@Composable
private fun TabsPreview() {
    BlockerTheme {
        Surface {
            val titles = listOf("App info", "Activity")
            BlockerTabRow(selectedTabIndex = 0) {
                titles.forEachIndexed { index, title ->
                    BlockerTab(
                        selected = index == 0,
                        onClick = { },
                        text = { Text(text = title) },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun BlockerTabRowPreview() {
    BlockerTheme {
        Surface {
            val titles = listOf("Tab 1", "Tab 2", "Tab 3")
            var selectedTabIndex by remember { mutableIntStateOf(0) }
            Column {
                BlockerTabRow(selectedTabIndex = selectedTabIndex) {
                    titles.forEachIndexed { index, title ->
                        BlockerTab(
                            selected = index == selectedTabIndex,
                            onClick = { selectedTabIndex = index },
                            text = { Text(text = title) },
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun BlockerScrollableTabRowPreview() {
    BlockerTheme {
        Surface {
            val titles = listOf("Tab 1", "Tab 2", "Tab 3", "Tab 4", "Tab 5", "Tab 6")
            var selectedTabIndex by remember { mutableIntStateOf(0) }
            Column {
                BlockerScrollableTabRow(selectedTabIndex = selectedTabIndex) {
                    titles.forEachIndexed { index, title ->
                        BlockerTab(
                            selected = index == selectedTabIndex,
                            onClick = { selectedTabIndex = index },
                            text = { Text(text = title) },
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun BlockerTabRowWithCustomColorsPreview() {
    BlockerTheme {
        Surface {
            val titles = listOf("Tab 1", "Tab 2", "Tab 3")
            var selectedTabIndex by remember { mutableIntStateOf(0) }
            Column {
                BlockerTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Gray,
                    contentColor = Color.White,
                ) {
                    titles.forEachIndexed { index, title ->
                        BlockerTab(
                            selected = index == selectedTabIndex,
                            onClick = { selectedTabIndex = index },
                            text = { Text(text = title) },
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun BlockerScrollableTabRowWithCustomColorsPreview() {
    BlockerTheme {
        Surface {
            val titles = listOf("Tab 1", "Tab 2", "Tab 3", "Tab 4", "Tab 5", "Tab 6")
            var selectedTabIndex by remember { mutableIntStateOf(0) }
            Column {
                BlockerScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Gray,
                    contentColor = Color.White,
                ) {
                    titles.forEachIndexed { index, title ->
                        BlockerTab(
                            selected = index == selectedTabIndex,
                            onClick = { selectedTabIndex = index },
                            text = { Text(text = title) },
                        )
                    }
                }
            }
        }
    }
}
