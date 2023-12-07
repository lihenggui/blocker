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

package com.merxury.blocker.core.ui.rule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation.Vertical
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.scrollbar.FastScrollbar
import com.merxury.blocker.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.merxury.blocker.core.designsystem.component.scrollbar.scrollbarState
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.ComponentItem
import com.merxury.blocker.core.ui.R.string
import com.merxury.blocker.core.ui.component.ComponentListItem
import com.merxury.blocker.core.ui.screen.LoadingScreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RuleMatchedAppList(
    modifier: Modifier = Modifier,
    ruleMatchedAppListUiState: RuleMatchedAppListUiState,
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
    navigateToAppDetail: (String) -> Unit = { _ -> },
    onBlockAllClick: (List<ComponentItem>) -> Unit = { _ -> },
    onEnableAllClick: (List<ComponentItem>) -> Unit = { _ -> },
    onSwitch: (String, String, Boolean) -> Unit = { _, _, _ -> },
) {
    when (ruleMatchedAppListUiState) {
        RuleMatchedAppListUiState.Loading -> LoadingScreen()
        is RuleMatchedAppListUiState.Success -> {
            if (ruleMatchedAppListUiState.list.isEmpty()) {
                NoApplicableAppScreen()
                return
            }
            val listState = rememberLazyListState()
            val scrollbarState = listState.scrollbarState(
                itemsAvailable = ruleMatchedAppListUiState.list.size,
            )
            val isExpandedMap = rememberSavableSnapshotStateMap {
                List(ruleMatchedAppListUiState.list.size) { index: Int -> index to false }
                    .toMutableStateMap()
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = modifier.testTag("rule:matchedAppList"),
                    state = listState,
                ) {
                    ruleMatchedAppListUiState.list.forEachIndexed { index, ruleMatchedApp ->
                        val expanded = isExpandedMap[index] ?: false
                        item(key = ruleMatchedApp.app.packageName) {
                            MatchedAppItemHeader(
                                modifier = Modifier.animateItemPlacement(),
                                iconModifier = Modifier,
                                ruleMatchedApp = ruleMatchedApp,
                                navigateToAppDetail = navigateToAppDetail,
                                onBlockAllClick = onBlockAllClick,
                                onEnableAllClick = onEnableAllClick,
                                expanded = expanded,
                                onCardArrowClicked = {
                                    isExpandedMap[index] = !(isExpandedMap[index] ?: false)
                                },
                            )
                        }
                        if (expanded) {
                            items(
                                items = ruleMatchedApp.componentList,
                                key = { item -> ruleMatchedApp.app.packageName + "/" + item.name },
                            ) {
                                ComponentListItem(
                                    modifier = modifier.animateItemPlacement(),
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
                                // add horizontal divider after last item
                                if (ruleMatchedApp.componentList.last() == it) {
                                    Divider(
                                        modifier = modifier,
                                    )
                                }
                            }
                        }
                    }
                }
                listState.FastScrollbar(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        .align(Alignment.CenterEnd),
                    state = scrollbarState,
                    orientation = Vertical,
                    onThumbMoved = listState.rememberDraggableScroller(
                        itemsAvailable = ruleMatchedAppListUiState.list.size,
                    ),
                )
            }
        }
    }
}

@Composable
fun NoApplicableAppScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BlockerBodyLargeText(
            text = stringResource(id = string.core_ui_no_applicable_app),
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

sealed interface RuleMatchedAppListUiState {
    data object Loading : RuleMatchedAppListUiState
    data class Success(
        val list: SnapshotStateList<RuleMatchedApp>,
    ) : RuleMatchedAppListUiState
}

fun <K, V> snapshotStateMapSaver() = Saver<SnapshotStateMap<K, V>, Any>(
    save = { state -> state.toList() },
    restore = { value ->
        @Suppress("UNCHECKED_CAST")
        (value as? List<Pair<K, V>>)?.toMutableStateMap() ?: mutableStateMapOf()
    },
)

@Composable
fun <K, V> rememberSavableSnapshotStateMap(init: () -> SnapshotStateMap<K, V>): SnapshotStateMap<K, V> =
    rememberSaveable(saver = snapshotStateMapSaver(), init = init)

@Composable
@Preview
fun RuleMatchedAppListPreview() {
    val componentInfo = ComponentItem(
        name = ".ui.component.ComponentListActivity",
        simpleName = "ComponentListItem",
        packageName = "com.merxury.blocker.test1",
        type = ACTIVITY,
        pmBlocked = false,
    )
    val ruleMatchedApp = RuleMatchedApp(
        app = AppItem(
            packageName = "com.merxury.blocker.test1",
            label = "Blocker",
            isSystem = false,
        ),
        componentList = remember {
            mutableStateListOf(componentInfo)
        },
    )
    val ruleMatchedApp2 = RuleMatchedApp(
        app = AppItem(
            packageName = "com.merxury.blocker.test2",
            label = "Test long long long long long name",
            isSystem = false,
        ),
        componentList = remember {
            mutableStateListOf()
        },
    )
    val uiState = RuleMatchedAppListUiState.Success(
        list = remember {
            mutableStateListOf(ruleMatchedApp, ruleMatchedApp2)
        },
    )
    BlockerTheme {
        Surface {
            RuleMatchedAppList(
                ruleMatchedAppListUiState = uiState,
            )
        }
    }
}
