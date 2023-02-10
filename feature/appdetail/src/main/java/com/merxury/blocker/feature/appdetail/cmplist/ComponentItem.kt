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

package com.merxury.blocker.feature.appdetail.cmplist

import android.content.res.Configuration
import android.view.MotionEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.ui.TrackScrollJank
import com.merxury.blocker.feature.appdetail.R

@Composable
fun ComponentTabContent(
    components: SnapshotStateList<ComponentInfo>,
    onStopServiceClick: (String, String) -> Unit,
    onLaunchActivityClick: (String, String) -> Unit,
    onCopyNameClick: (String) -> Unit,
    onCopyFullNameClick: (String) -> Unit,
    onSwitchClick: (String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    if (components.isEmpty()) {
        NoComponentScreen()
        return
    }
    val listContent = remember { components }
    TrackScrollJank(scrollableState = listState, stateName = "component:list")
    LazyColumn(
        modifier = modifier,
        state = listState,
    ) {
        items(
            items = listContent,
            key = { it.name },
        ) {
            ComponentItem(
                simpleName = it.simpleName,
                name = it.name,
                packageName = it.packageName,
                enabled = it.enabled(),
                type = it.type,
                isServiceRunning = false,
                onStopServiceClick = { onStopServiceClick(it.packageName, it.name) },
                onLaunchActivityClick = { onLaunchActivityClick(it.packageName, it.name) },
                onCopyNameClick = { onCopyNameClick(it.simpleName) },
                onCopyFullNameClick = { onCopyFullNameClick(it.name) },
                onSwitchClick = onSwitchClick,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ComponentItem(
    simpleName: String,
    name: String,
    packageName: String,
    enabled: Boolean,
    type: ComponentType,
    isServiceRunning: Boolean,
    onStopServiceClick: () -> Unit,
    onLaunchActivityClick: () -> Unit,
    onCopyNameClick: () -> Unit,
    onCopyFullNameClick: () -> Unit,
    onSwitchClick: (String, String, Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var touchPoint: Offset by remember { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
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
            Text(
                text = simpleName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        Icon(
            imageVector = BlockerIcons.Deselect,
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .padding(8.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = stringResource(id = R.string.no_components),
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun ComponentItemPreview() {
    BlockerTheme {
        Surface {
            ComponentItem(
                simpleName = "AccountAuthActivity",
                name = "com.merxury.blocker.feature.appdetail.component.AccountAuthActivity",
                packageName = "com.merxury.blocker",
                enabled = false,
                type = SERVICE,
                isServiceRunning = true,
                onStopServiceClick = { },
                onCopyFullNameClick = { },
                onCopyNameClick = { },
                onLaunchActivityClick = { },
                onSwitchClick = { _, _, _ -> },
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
