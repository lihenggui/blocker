/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.feature.appdetail.impl.ui

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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.scrollbar.DraggableScrollbar
import com.merxury.blocker.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.merxury.blocker.core.designsystem.component.scrollbar.scrollbarState
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.ui.TrackScrollJank
import com.merxury.blocker.core.ui.component.NoComponentScreen
import com.merxury.blocker.core.ui.control.ModeBanner
import com.merxury.blocker.core.ui.control.ModeBanners
import com.merxury.blocker.feature.appdetail.impl.ComponentRow

/**
 * The rows of one component tab below the limits of the current mode. Pulling down refreshes the
 * app; the rows shown meanwhile are the cached ones.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun ComponentTabContent(
    rows: List<ComponentRow>,
    inFlight: Set<ComponentId>,
    banners: List<ModeBanner>,
    isRefreshing: Boolean,
    actions: ComponentRowActions,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selected: Set<ComponentId> = emptySet(),
    onRefresh: () -> Unit = {},
    onGrantAccess: () -> Unit = {},
) {
    val refreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = onRefresh)
    Box(modifier = modifier.fillMaxSize().pullRefresh(refreshState)) {
        if (rows.isEmpty()) {
            Column {
                ModeBanners(banners = banners, onGrantAccess = onGrantAccess)
                NoComponentScreen()
            }
        } else {
            ComponentRows(
                rows = rows,
                inFlight = inFlight,
                banners = banners,
                actions = actions,
                isSelectionMode = isSelectionMode,
                selected = selected,
                onGrantAccess = onGrantAccess,
            )
        }
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = refreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            scale = true,
        )
    }
}

@Composable
private fun ComponentRows(
    rows: List<ComponentRow>,
    inFlight: Set<ComponentId>,
    banners: List<ModeBanner>,
    actions: ComponentRowActions,
    isSelectionMode: Boolean,
    selected: Set<ComponentId>,
    onGrantAccess: () -> Unit,
) {
    val listState = rememberLazyListState()
    val itemCount = rows.size + if (banners.isEmpty()) 0 else 1
    val scrollbarState = listState.scrollbarState(itemsAvailable = itemCount)
    TrackScrollJank(scrollableState = listState, stateName = "component:list")
    Box(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.testTag("component:list"), state = listState) {
            if (banners.isNotEmpty()) {
                item(key = "banners") { ModeBanners(banners = banners, onGrantAccess = onGrantAccess) }
            }
            items(items = rows, key = { it.id.className }) { row ->
                ComponentRowItem(
                    row = row,
                    inFlight = row.id in inFlight,
                    actions = actions,
                    isSelectionMode = isSelectionMode,
                    isSelected = row.id in selected,
                )
            }
            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
        listState.DraggableScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd),
            state = scrollbarState,
            orientation = Vertical,
            onThumbMove = listState.rememberDraggableScroller(itemsAvailable = itemCount),
        )
    }
}
