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

package com.merxury.blocker.feature.appdetail.sdk

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.scrollbar.FastScrollbar
import com.merxury.blocker.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.merxury.blocker.core.designsystem.component.scrollbar.scrollbarState
import com.merxury.blocker.core.model.data.ComponentItem
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.TrackScrollJank
import com.merxury.blocker.core.ui.component.NoComponentScreen
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.rule.MatchedAppItemHeader
import com.merxury.blocker.core.ui.rule.MatchedHeaderData
import com.merxury.blocker.core.ui.rule.MatchedItem
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen

@Composable
fun SdkContent(
    data: Result<Map<GeneralRule, SnapshotStateList<ComponentItem>>>,
    modifier: Modifier = Modifier,
) {
    when (data) {
        is Result.Success -> {
            val sdks = data.data
            if (sdks.isEmpty()) {
                NoComponentScreen()
                return
            }
            val listState = rememberLazyListState()
            val scrollbarState = listState.scrollbarState(
                itemsAvailable = sdks.size,
            )
            TrackScrollJank(scrollableState = listState, stateName = "component:sdk")
            Box(modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = modifier.testTag("component:list"),
                    state = listState,
                ) {
                    itemsIndexed(
                        items = sdks.entries.toList(),
                        key = { index, item -> index + item.key.id },
                    ) { _, item ->
                        val rule = item.key
                        val components = item.value
                        MatchedAppItemHeader(
                            matchedItem = MatchedItem(
                                header = MatchedHeaderData(
                                    title = rule.name,
                                    uniqueId = rule.id.toString(),
                                    icon = rule.iconUrl,
                                ),
                                componentList = components,
                            ),
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
                        itemsAvailable = sdks.size,
                    ),
                )
            }
        }

        is Result.Error -> ErrorScreen(error = UiMessage(title = data.exception.message.orEmpty()))

        is Result.Loading -> LoadingScreen()
    }
}
