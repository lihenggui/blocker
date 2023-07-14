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

import android.content.res.Configuration
import android.view.MotionEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation.Vertical
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.BlockerLabelSmallText
import com.merxury.blocker.core.designsystem.component.scrollbar.FastScrollbar
import com.merxury.blocker.core.designsystem.component.scrollbar.rememberFastScroller
import com.merxury.blocker.core.designsystem.component.scrollbar.scrollbarState
import com.merxury.blocker.core.designsystem.icon.BlockerDisplayIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.designsystem.theme.condensedRegular
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.ui.R.string
import com.merxury.blocker.core.ui.TrackScrollJank

@Composable
fun ComponentList(
    components: List<ComponentItem>,
    modifier: Modifier = Modifier,
    navigateToComponentDetail: (String) -> Unit = { _ -> },
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
    onSwitchClick: (String, String, Boolean) -> Unit = { _, _, _ -> },
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
            modifier = modifier,
            state = listState,
        ) {
            itemsIndexed(
                items = components,
                key = { _, item -> item.name },
            ) { _, item ->
                ComponentListItem(
                    simpleName = item.simpleName,
                    name = item.name,
                    description = item.description,
                    packageName = item.packageName,
                    enabled = item.enabled(),
                    type = item.type,
                    isServiceRunning = item.isRunning,
                    navigateToComponentDetail = navigateToComponentDetail,
                    onStopServiceClick = { onStopServiceClick(item.packageName, item.name) },
                    onLaunchActivityClick = { onLaunchActivityClick(item.packageName, item.name) },
                    onCopyNameClick = { onCopyNameClick(item.simpleName) },
                    onCopyFullNameClick = { onCopyFullNameClick(item.name) },
                    onSwitchClick = onSwitchClick,
                )
            }
        }
        listState.FastScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd),
            state = scrollbarState,
            orientation = Vertical,
            onThumbDisplaced = listState.rememberFastScroller(
                itemsAvailable = components.size,
            ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ComponentListItem(
    simpleName: String,
    name: String,
    packageName: String,
    description: String? = null,
    enabled: Boolean,
    type: ComponentType,
    isServiceRunning: Boolean,
    navigateToComponentDetail: (String) -> Unit = { },
    onStopServiceClick: () -> Unit = { },
    onLaunchActivityClick: () -> Unit = { },
    onCopyNameClick: () -> Unit = { },
    onCopyFullNameClick: () -> Unit = { },
    onSwitchClick: (String, String, Boolean) -> Unit = { _, _, _ -> },
) {
    var expanded by remember { mutableStateOf(false) }
    var touchPoint: Offset by remember { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    navigateToComponentDetail(name)
                },
                onLongClick = {
                    expanded = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            )
            .pointerInteropFilter {
                if (it.action == MotionEvent.ACTION_DOWN) {
                    touchPoint = Offset(it.x, it.y)
                }
                false
            }
            .padding(start = 16.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BlockerBodyLargeText(
                    modifier = Modifier.weight(1F),
                    text = simpleName,
                )
                if (isServiceRunning) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val indicatorColor = MaterialTheme.colorScheme.tertiary
                    BlockerLabelSmallText(
                        modifier = Modifier
                            .drawBehind {
                                drawRoundRect(
                                    color = indicatorColor,
                                    cornerRadius = CornerRadius(x = 4.dp.toPx(), y = 4.dp.toPx()),
                                )
                            }
                            .padding(horizontal = 2.dp, vertical = 1.dp),
                        text = stringResource(id = string.running),
                        color = MaterialTheme.colorScheme.onTertiary,
                    )
                }
            }
            BlockerBodyMediumText(text = name)
            description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                BlockerBodyMediumText(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium.condensedRegular(),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = enabled,
            onCheckedChange = {
                onSwitchClick(packageName, name, !enabled)
            },
        )
        val offset = with(density) {
            DpOffset(touchPoint.x.toDp(), -touchPoint.y.toDp())
        }
        ComponentItemMenu(
            expanded = expanded,
            type = type,
            offset = offset,
            isServiceRunning = isServiceRunning,
            onStopServiceClick = onStopServiceClick,
            onLaunchActivityClick = onLaunchActivityClick,
            onCopyNameClick = onCopyNameClick,
            onCopyPackageNameClick = onCopyFullNameClick,
            onDismissRequest = { expanded = false },
        )
    }
}

@Composable
fun NoComponentScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BlockerDisplayIcon(
            imageVector = BlockerIcons.Deselect,
            contentDescription = null,
        )
        BlockerBodyLargeText(
            text = stringResource(id = string.no_components),
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun ComponentItemPreview() {
    BlockerTheme {
        Surface {
            ComponentListItem(
                simpleName = "ExampleActivity",
                name = "com.merxury.blocker.feature.appdetail.component.ExampleActivity",
                packageName = "com.merxury.blocker",
                enabled = false,
                description = "An example activity",
                type = SERVICE,
                isServiceRunning = true,
            )
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun NoComponentScreenPreview() {
    BlockerTheme {
        Surface {
            NoComponentScreen()
        }
    }
}
