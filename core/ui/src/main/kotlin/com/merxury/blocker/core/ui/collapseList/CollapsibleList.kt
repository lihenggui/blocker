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

package com.merxury.blocker.core.ui.collapseList

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.Orientation.Vertical
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import com.merxury.blocker.core.designsystem.component.scrollbar.DraggableScrollbar
import com.merxury.blocker.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.merxury.blocker.core.designsystem.component.scrollbar.scrollbarState
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.domain.model.MatchedHeaderData
import com.merxury.blocker.core.domain.model.MatchedItem
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.ui.R.string
import com.merxury.blocker.core.ui.component.ComponentListItem
import timber.log.Timber

@Composable
fun CollapsibleList(
    list: List<MatchedItem>,
    modifier: Modifier = Modifier,
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
    @StringRes navigationMenuItemDesc: Int = string.core_ui_open_app_detail,
    navigateToDetail: (String) -> Unit = { _ -> },
    onBlockAllInItemClick: (List<ComponentInfo>) -> Unit = { _ -> },
    onEnableAllInItemClick: (List<ComponentInfo>) -> Unit = { _ -> },
    onSwitch: (ComponentInfo, Boolean) -> Unit = { _, _ -> },
) {
    val listState = rememberLazyListState()
    val isExpandedMap = rememberSavableSnapshotStateMap {
        List(list.size) { index: Int -> index to false }
            .toMutableStateMap()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.testTag("rule:matchedAppList"),
            state = listState,
        ) {
            list.forEachIndexed { index, ruleMatchedApp ->
                val expanded = isExpandedMap[index] ?: false
                item(key = ruleMatchedApp.header.uniqueId) {
                    Column(
                        modifier = Modifier.animateItem(),
                    ) {
                        CollapsibleItem(
                            matchedItem = ruleMatchedApp,
                            navigationMenuItemDesc = navigationMenuItemDesc,
                            navigation = {
                                navigateToDetail(ruleMatchedApp.header.uniqueId)
                            },
                            onBlockAllInItemClick = onBlockAllInItemClick,
                            onEnableAllInItemClick = onEnableAllInItemClick,
                            expanded = expanded,
                            onCardArrowClick = {
                                isExpandedMap[index] = !(isExpandedMap[index] ?: false)
                            },
                        )
                        HorizontalDivider()
                    }
                }
                if (expanded) {
                    items(
                        items = ruleMatchedApp.componentList,
                        key = { item -> ruleMatchedApp.header.uniqueId + "/" + item.name },
                    ) {
                        Column(
                            modifier = Modifier.animateItem(),
                        ) {
                            ComponentListItem(
                                item = it,
                                enabled = it.enabled(),
                                type = it.type,
                                isServiceRunning = it.isRunning,
                                onStopServiceClick = {
                                    onStopServiceClick(
                                        it.packageName,
                                        it.name,
                                    )
                                },
                                onLaunchActivityClick = {
                                    onLaunchActivityClick(
                                        it.packageName,
                                        it.name,
                                    )
                                },
                                onCopyNameClick = { onCopyNameClick(it.simpleName) },
                                onCopyFullNameClick = { onCopyFullNameClick(it.name) },
                                onSwitchClick = onSwitch,
                            )
                            // Add horizontal divider after last item
                            if (ruleMatchedApp.componentList.last() == it) {
                                HorizontalDivider()
                            }
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
            .fastSumBy { it.componentList.size }
        val totalItems = list.size + expandItemCount
        val scrollbarState = listState.scrollbarState(
            itemsAvailable = totalItems,
        )
        listState.DraggableScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd),
            state = scrollbarState,
            orientation = Vertical,
            onThumbMove = listState.rememberDraggableScroller(
                itemsAvailable = totalItems,
            ),
        )
    }
}

fun <K, V> snapshotStateMapSaver() = Saver<SnapshotStateMap<K, V>, Any>(
    save = { state -> state.toList() },
    restore = { value ->
        @Suppress("UNCHECKED_CAST")
        (value as? List<Pair<K, V>>)?.toMutableStateMap() ?: mutableStateMapOf()
    },
)

@Composable
fun <K, V> rememberSavableSnapshotStateMap(init: () -> SnapshotStateMap<K, V>): SnapshotStateMap<K, V> = rememberSaveable(saver = snapshotStateMapSaver(), init = init)

@Composable
@Preview
private fun RuleMatchedAppListPreview() {
    val componentInfo = ComponentInfo(
        name = ".ui.component.ComponentListActivity",
        simpleName = "ComponentListItem",
        packageName = "com.merxury.blocker.test1",
        type = ACTIVITY,
    )
    val matchedItem = MatchedItem(
        header = MatchedHeaderData(
            title = "Blocker",
            uniqueId = "com.merxury.blocker.test1",
        ),
        componentList = remember {
            mutableStateListOf(componentInfo)
        },
    )
    val matchedItem2 = MatchedItem(
        header = MatchedHeaderData(
            title = "Test long long long long long name",
            uniqueId = "com.merxury.blocker.test2",
        ),
        componentList = remember {
            mutableStateListOf()
        },
    )
    BlockerTheme {
        Surface {
            CollapsibleList(
                list = listOf(matchedItem, matchedItem2),
            )
        }
    }
}
