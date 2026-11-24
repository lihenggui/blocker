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

package com.merxury.blocker.core.ui.applist

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.scrollbar.DraggableScrollbar
import com.merxury.blocker.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.merxury.blocker.core.designsystem.component.scrollbar.scrollbarState
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.system.AppAction
import com.merxury.blocker.core.ui.TrackScrollJank

/**
 * The apps of one user, each with the menu of [AppAction]s. While [countServices] is set, each row
 * shown without service counts reads them through it, cancelled when the row leaves the screen.
 */
@Composable
fun AppRowList(
    apps: List<AppRow>,
    onAppClick: (AppRow) -> Unit,
    onAction: (AppRow, AppAction) -> Unit,
    modifier: Modifier = Modifier,
    countServices: (suspend (AppKey) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val scrollbarState = listState.scrollbarState(itemsAvailable = apps.size)
    TrackScrollJank(scrollableState = listState, stateName = "app:list")
    Box(modifier.fillMaxSize()) {
        LazyColumn(state = listState) {
            items(apps, key = { it.key.packageName }) { app ->
                if (countServices != null) {
                    // Counts dropped by a refresh or an action are read again while the row shows.
                    LaunchedEffect(app.key, app.versionCode, app.services == null) { countServices(app.key) }
                }
                AppListItem(
                    label = app.label,
                    packageName = app.key.packageName,
                    versionName = app.versionName,
                    versionCode = app.versionCode,
                    isAppEnabled = !app.isDisabled,
                    isAppRunning = app.isRunning == true,
                    icon = app.icon,
                    serviceCounts = app.services,
                    onClick = { onAppClick(app) },
                    onClearCacheClick = { onAction(app, AppAction.CLEAR_CACHE) },
                    onClearDataClick = { onAction(app, AppAction.CLEAR_DATA) },
                    onForceStopClick = { onAction(app, AppAction.FORCE_STOP) },
                    onUninstallClick = { onAction(app, AppAction.UNINSTALL) },
                    onEnableClick = { onAction(app, AppAction.ENABLE) },
                    onDisableClick = { onAction(app, AppAction.DISABLE) },
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
                .align(Alignment.CenterEnd)
                .testTag("appList:scrollbar"),
            state = scrollbarState,
            orientation = Orientation.Vertical,
            onThumbMove = listState.rememberDraggableScroller(itemsAvailable = apps.size),
        )
    }
}
