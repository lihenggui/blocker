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

package com.merxury.blocker.core.ui.component

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.ThemePreviews
import com.merxury.blocker.core.designsystem.component.scrollbar.FastScrollbar
import com.merxury.blocker.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.merxury.blocker.core.designsystem.component.scrollbar.scrollbarState
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ComponentItem
import com.merxury.blocker.core.ui.TrackScrollJank
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ComponentList(
    components: List<ComponentItem>,
    modifier: Modifier = Modifier,
    selectedComponentList: List<ComponentInfo> = emptyList(),
    navigateToComponentDetail: (String) -> Unit = { _ -> },
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
    onSwitchClick: (String, String, Boolean) -> Unit = { _, _, _ -> },
    isSelectedMode: Boolean = false,
    onSelect: (ComponentInfo) -> Unit = {},
    onDeselect: (ComponentInfo) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    if (components.isEmpty()) {
        NoComponentScreen()
        return
    }
    val listState = rememberLazyListState()
    val scrollbarState = listState.scrollbarState(
        itemsAvailable = components.size,
    )
    TrackScrollJank(scrollableState = listState, stateName = "component:list")
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = modifier.testTag("component:list"),
            state = listState,
        ) {
            itemsIndexed(
                items = components,
                key = { _, item -> item.name },
            ) { _, item ->
                ComponentListItem(
                    item = item,
                    enabled = item.enabled(),
                    type = item.type,
                    isServiceRunning = item.isRunning,
                    navigateToComponentDetail = navigateToComponentDetail,
                    onStopServiceClick = { onStopServiceClick(item.packageName, item.name) },
                    onLaunchActivityClick = {
                        onLaunchActivityClick(
                            item.packageName,
                            item.name,
                        )
                    },
                    onCopyNameClick = { onCopyNameClick(item.simpleName) },
                    onCopyFullNameClick = { onCopyFullNameClick(item.name) },
                    onSwitchClick = onSwitchClick,
                    isSelected = selectedComponentList.contains(item.toComponentInfo()),
                    isSelectedMode = isSelectedMode,
                    onSelect = onSelect,
                    onDeselect = onDeselect,
                )
            }
            item {
                Spacer(modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
        listState.FastScrollbar(
            modifier = modifier
                .fillMaxHeight()
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd),
            state = scrollbarState,
            orientation = Vertical,
            onThumbMoved = listState.rememberDraggableScroller(
                itemsAvailable = components.size,
            ),
        )
    }
}

@Composable
@ThemePreviews
fun ComponentListPreview(
    @PreviewParameter(
        ComponentListPreviewParameterProvider::class,
    ) components: List<ComponentItem>,
) {
    BlockerTheme {
        Surface {
            ComponentList(
                components = components,
            )
        }
    }
}

@Composable
@ThemePreviews
fun ComponentListSelectedModePreview(
    @PreviewParameter(
        ComponentListPreviewParameterProvider::class,
    ) components: List<ComponentItem>,
) {
    BlockerTheme {
        Surface {
            ComponentList(
                components = components,
                selectedComponentList = listOf(
                    components[1].toComponentInfo(),
                ),
                isSelectedMode = true,
            )
        }
    }
}
