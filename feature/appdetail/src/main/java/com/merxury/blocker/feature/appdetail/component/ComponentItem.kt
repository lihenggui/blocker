/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.feature.appdetail.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.ComponentInfo

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ComponentTabContent(
    components: SnapshotStateList<ComponentInfo>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSwitchClick: (String, String, Boolean) -> Boolean,
    modifier: Modifier = Modifier
) {
    val listContent = remember { components }
    val listState = rememberLazyListState()
    val refreshing by remember { mutableStateOf(isRefreshing) }
    val refreshingState = rememberPullRefreshState(refreshing, onRefresh)
    Box(modifier.pullRefresh(refreshingState)) {
        LazyColumn(
            state = listState
        ) {
            items(listContent) {
                ComponentItem(
                    simpleName = it.simpleName,
                    name = it.name,
                    packageName = it.packageName,
                    enabled = it.enabled,
                    onSwitchClick = onSwitchClick
                )
            }
            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
        PullRefreshIndicator(
            refreshing = refreshing,
            state = refreshingState,
            modifier = Modifier.align(Alignment.TopCenter),
            scale = true
        )
    }
}

@Composable
fun ComponentItem(
    simpleName: String,
    name: String,
    packageName: String,
    enabled: Boolean,
    onSwitchClick: (String, String, Boolean) -> Boolean
) {
    Row(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
            Text(text = simpleName, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = enabled,
            onCheckedChange = {
                onSwitchClick(packageName, name, !enabled)
            }
        )
    }
}

@Composable
@Preview
fun ComponentItemPreview() {
    BlockerTheme {
        Surface {
            ComponentItem(
                simpleName = "AccountAuthActivity",
                name = "com.merxury.blocker.feature.appdetail.component.AccountAuthActivity",
                packageName = "com.merxury.blocker",
                enabled = false,
                onSwitchClick = { _, _, _ -> true }
            )
        }
    }
}
