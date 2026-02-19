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

package com.merxury.blocker.feature.debloater.impl

import androidx.compose.foundation.gestures.Orientation.Vertical
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.component.scrollbar.DraggableScrollbar
import com.merxury.blocker.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.merxury.blocker.core.designsystem.component.scrollbar.scrollbarState
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.collapseList.rememberSavableSnapshotStateMap
import timber.log.Timber

@Composable
fun DebloatableAppList(
    list: List<MatchedTarget>,
    modifier: Modifier = Modifier,
    onBlockAllInItemClick: (List<DebloatableComponentUiItem>) -> Unit = { _ -> },
    onEnableAllInItemClick: (List<DebloatableComponentUiItem>) -> Unit = { _ -> },
    onSwitch: (DebloatableComponentUiItem, Boolean) -> Unit = { _, _ -> },
) {
    val listState = rememberLazyListState()
    val isExpandedMap = rememberSavableSnapshotStateMap {
        List(list.size) { index: Int -> index to false }
            .toMutableStateMap()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.testTag("debloater:debloatableAppList"),
            state = listState,
        ) {
            list.forEachIndexed { index, debloatableApp ->
                val expanded = isExpandedMap[index] ?: false
                item(key = debloatableApp.header.uniqueId) {
                    DebloatCollapsibleItem(
                        modifier = Modifier.animateItem(),
                        matchedTarget = debloatableApp,
                        onBlockAllInItemClick = onBlockAllInItemClick,
                        onEnableAllInItemClick = onEnableAllInItemClick,
                        expanded = expanded,
                        onCardArrowClick = {
                            isExpandedMap[index] = !(isExpandedMap[index] ?: false)
                        },
                    )
                    HorizontalDivider()
                }
                if (expanded) {
                    items(
                        count = debloatableApp.targets.size,
                        key = { itemIndex ->
                            val uiItem = debloatableApp.targets[itemIndex]
                            "${debloatableApp.header.uniqueId}/${uiItem.entity.componentName}"
                        },
                    ) { itemIndex ->
                        val uiItem = debloatableApp.targets[itemIndex]
                        DebloatableAppSubItem(
                            modifier = Modifier.animateItem(),
                            item = uiItem,
                            enabled = !uiItem.entity.pmBlocked && !uiItem.entity.ifwBlocked,
                            onSwitchClick = onSwitch,
                        )
                        if (itemIndex == debloatableApp.targets.size - 1) {
                            HorizontalDivider()
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
        val expandItemCount = isExpandedMap.filterValues { it }
            .keys
            .mapNotNull {
                val item = list.getOrNull(it)
                if (item == null) {
                    Timber.e("Item not found for index $it, map = $isExpandedMap, list = $list")
                }
                item
            }
            .fastSumBy { it.targets.size }
        val scrollbarState = listState.scrollbarState(
            itemsAvailable = list.size + expandItemCount,
        )
        listState.DraggableScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd),
            state = scrollbarState,
            orientation = Vertical,
            onThumbMove = listState.rememberDraggableScroller(
                itemsAvailable = list.size,
            ),
        )
    }
}

@Composable
@PreviewThemes
private fun DebloatableAppListPreview(
    @PreviewParameter(DebloaterPreviewParameterProvider::class)
    list: List<MatchedTarget>,
) {
    BlockerTheme {
        Surface {
            DebloatableAppList(
                list = list,
            )
        }
    }
}

@Composable
@Preview
private fun DebloatableAppListEmptyPreview() {
    BlockerTheme {
        Surface {
            DebloatableAppList(
                list = emptyList(),
            )
        }
    }
}
