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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.ui.TrackScrollJank
import com.merxury.blocker.core.ui.previewparameter.AppListPreviewParameterProvider

@Composable
fun AppList(
    appList: List<AppItem>,
    modifier: Modifier = Modifier,
    onAppItemClick: (String) -> Unit = {},
    onClearCacheClick: (String) -> Unit = {},
    onClearDataClick: (String) -> Unit = {},
    onForceStopClick: (String) -> Unit = {},
    onUninstallClick: (String) -> Unit = {},
    onEnableClick: (String) -> Unit = {},
    onDisableClick: (String) -> Unit = {},
    onServiceStateUpdate: (String, Int) -> Unit = { _, _ -> },
) {
    val listState = rememberLazyListState()
    val scrollbarState = listState.scrollbarState(
        itemsAvailable = appList.size,
    )
    TrackScrollJank(scrollableState = listState, stateName = "app:list")
    Box(
        modifier
            .fillMaxSize(),
    ) {
        LazyColumn(
            modifier = modifier,
            state = listState,
        ) {
            itemsIndexed(appList, key = { _, item -> item.packageName }) { index, item ->
                AppListItem(
                    label = item.label,
                    packageName = item.packageName,
                    versionName = item.versionName,
                    versionCode = item.versionCode,
                    packageInfo = item.packageInfo,
                    isAppEnabled = item.isEnabled,
                    isAppRunning = item.isRunning,
                    appServiceStatus = item.appServiceStatus,
                    onClick = onAppItemClick,
                    onClearCacheClick = onClearCacheClick,
                    onClearDataClick = onClearDataClick,
                    onForceStopClick = onForceStopClick,
                    onUninstallClick = onUninstallClick,
                    onEnableClick = onEnableClick,
                    onDisableClick = onDisableClick,
                )
                LaunchedEffect(true) {
                    onServiceStateUpdate(item.packageName, index)
                }
            }
            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
        listState.FastScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd)
                .testTag("appList:scrollbar"),
            state = scrollbarState,
            orientation = Orientation.Vertical,
            onThumbMoved = listState.rememberDraggableScroller(
                itemsAvailable = appList.size,
            ),
        )
    }
}

@Composable
@ThemePreviews
fun AppListPreview(
    @PreviewParameter(AppListPreviewParameterProvider::class)
    appList: List<AppItem>,
) {
    BlockerTheme {
        Surface {
            AppList(
                appList = appList,
            )
        }
    }
}
