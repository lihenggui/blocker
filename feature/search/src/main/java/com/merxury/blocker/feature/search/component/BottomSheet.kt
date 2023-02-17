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

package com.merxury.blocker.feature.search.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.merxury.blocker.core.designsystem.component.BlockerScrollableTabRow
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.feature.search.R.string
import com.merxury.blocker.feature.search.model.BottomSheetViewModel
import com.merxury.blocker.feature.search.model.InstalledAppItem

@Composable
fun BottomSheetRoute(
    modifier: Modifier = Modifier,
    app: InstalledAppItem,
    viewModel: BottomSheetViewModel = hiltViewModel(),
) {
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    BottomSheet(
        modifier = modifier,
        filterApp = app,
        tabState = tabState,
        switchTab = viewModel::switchTab,
    )
}

@Composable
fun BottomSheet(
    modifier: Modifier = Modifier,
    filterApp: InstalledAppItem,
    tabState: TabState,
    switchTab: (Int) -> Unit,
) {
    Column(modifier = modifier.defaultMinSize(1.dp)) {
        InfoSection(
            modifier = modifier,
            filterApp = filterApp,
        )
        BlockerScrollableTabRow(
            selectedTabIndex = tabState.currentIndex,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tabs = {
                tabState.titles.forEachIndexed { index, titleRes ->
                    BlockerTab(
                        selected = index == tabState.currentIndex,
                        onClick = { switchTab(index) },
                        text = { Text(text = stringResource(id = titleRes)) },
                    )
                }
            },
        )
        when (tabState.currentIndex) {
            0 -> {}
            1 -> {}
        }
    }
}

@Composable
fun InfoSection(
    modifier: Modifier = Modifier,
    filterApp: InstalledAppItem,
) {
    val versionName = filterApp.versionName
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.7f),
        ) {
            Text(
                text = filterApp.label,
                fontSize = 28.sp,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = filterApp.packageName,
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = versionName,
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        AsyncImage(
            modifier = Modifier
                .size(80.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = false),
                    onClick = {
                        // TODO
                    },
                ),
            model = Builder(LocalContext.current)
                .data(filterApp.packageInfo)
                .crossfade(true)
                .build(),
            contentDescription = null,
        )
    }
}

@Composable
@Preview
fun BottomSheetPreview() {
    val app = InstalledAppItem(
        packageName = "com.merxury.blocker",
        label = "Blocker test long name",
        versionName = "23.12.20",
        isSystem = false,
    )
    val tabState = TabState(
        titles = listOf(
            string.applicable_app,
            string.illustrate,
        ),
        currentIndex = 0,
    )
    BlockerTheme {
        Surface {
            BottomSheet(filterApp = app, tabState = tabState, switchTab = {})
        }
    }
}
