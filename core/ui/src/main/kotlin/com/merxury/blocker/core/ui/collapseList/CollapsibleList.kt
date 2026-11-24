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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.ui.R.string
import com.merxury.blocker.core.ui.component.ComponentListItem
import com.merxury.blocker.core.ui.component.ComponentRow

/**
 * One group of a [CollapsibleList]: a header with [title], [icon] and the counts of its [items],
 * over the items while it is expanded.
 *
 * @property key tells the group apart while the list changes, such as the package name of an
 * app; a group stays expanded by its key
 * @property icon Coil data for the icon; the Android icon when null or when it cannot be loaded
 * @property blockedCount the items shown blocked; with [enabledCount] it may be fewer than the
 * items when the state of some is not known
 */
data class CollapsibleGroup<T>(
    val key: String,
    val title: String,
    val icon: Any?,
    val items: List<T>,
    val blockedCount: Int,
    val enabledCount: Int,
)

/** The group of [rows], each counted as blocked or enabled by its switch. */
fun collapsibleGroup(key: String, title: String, icon: Any?, rows: List<ComponentRow>): CollapsibleGroup<ComponentRow> = CollapsibleGroup(
    key = key,
    title = title,
    icon = icon,
    items = rows,
    blockedCount = rows.count { !it.enabled },
    enabledCount = rows.count { it.enabled },
)

/**
 * Groups, each a header that expands to its items, with a scrollbar.
 *
 * @param itemKey tells an item apart within its group
 * @param itemContent one item, which is given the modifier that animates it
 */
@Composable
fun <T> CollapsibleList(
    groups: List<CollapsibleGroup<T>>,
    itemKey: (T) -> String,
    modifier: Modifier = Modifier,
    @StringRes navigationMenuItemDesc: Int = string.core_ui_open_app_detail,
    navigateToDetail: (CollapsibleGroup<T>) -> Unit = {},
    onBlockAllClick: (CollapsibleGroup<T>) -> Unit = {},
    onEnableAllClick: (CollapsibleGroup<T>) -> Unit = {},
    itemContent: @Composable LazyItemScope.(item: T, modifier: Modifier) -> Unit,
) {
    val listState = rememberLazyListState()
    var expandedKeys by rememberSaveable { mutableStateOf(emptySet<String>()) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.testTag("rule:matchedAppList"),
            state = listState,
        ) {
            groups.forEach { group ->
                val expanded = group.key in expandedKeys
                item(key = group.key) {
                    CollapsibleHeader(
                        title = group.title,
                        icon = group.icon,
                        matchedCount = group.items.size,
                        blockedCount = group.blockedCount,
                        enabledCount = group.enabledCount,
                        modifier = Modifier.animateItem(),
                        navigationMenuItemDesc = navigationMenuItemDesc,
                        navigation = { navigateToDetail(group) },
                        onBlockAllClick = { onBlockAllClick(group) },
                        onEnableAllClick = { onEnableAllClick(group) },
                        expanded = expanded,
                        onCardArrowClick = { expandedKeys = if (expanded) expandedKeys - group.key else expandedKeys + group.key },
                    )
                    HorizontalDivider()
                }
                if (expanded) {
                    items(
                        items = group.items,
                        key = { item -> group.key + "/" + itemKey(item) },
                    ) { item ->
                        itemContent(item, Modifier.animateItem())
                        // Add horizontal divider after last item
                        if (group.items.last() == item) {
                            HorizontalDivider()
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
        val expandItemCount = groups.filter { it.key in expandedKeys }.fastSumBy { it.items.size }
        val scrollbarState = listState.scrollbarState(
            itemsAvailable = groups.size + expandItemCount,
        )
        listState.DraggableScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd),
            state = scrollbarState,
            orientation = Vertical,
            onThumbMove = listState.rememberDraggableScroller(
                itemsAvailable = groups.size,
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
    val row = ComponentRow(
        id = ComponentId("com.merxury.blocker.test1", "com.merxury.blocker.test1.ui.component.ComponentListActivity"),
        type = ACTIVITY,
        enabled = true,
    )
    BlockerTheme {
        Surface {
            CollapsibleList(
                groups = listOf(
                    collapsibleGroup(key = "com.merxury.blocker.test1", title = "Blocker", icon = null, rows = listOf(row)),
                    collapsibleGroup(key = "com.merxury.blocker.test2", title = "Test long long long long long name", icon = null, rows = emptyList()),
                ),
                itemKey = { it.id.className },
            ) { item, itemModifier ->
                ComponentListItem(
                    simpleName = item.id.simpleName,
                    name = item.id.className,
                    enabled = item.enabled,
                    type = item.type,
                    isServiceRunning = item.isRunning,
                    modifier = itemModifier,
                )
            }
        }
    }
}
