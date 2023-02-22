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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerScrollableTabRow
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.AppDetailTabs.Activity
import com.merxury.blocker.core.ui.AppDetailTabs.Provider
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.applist.model.AppItem
import com.merxury.blocker.core.ui.bottomSheet.BottomSheetTopBar
import com.merxury.blocker.feature.search.model.BottomSheetViewModel

@Composable
fun BottomSheetRoute(
    modifier: Modifier = Modifier,
    app: AppItem,
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
    filterApp: AppItem,
    tabState: TabState<AppDetailTabs>,
    switchTab: (AppDetailTabs) -> Unit,
) {
    Column(modifier = modifier.defaultMinSize(1.dp)) {
        BottomSheetTopBar(
            title = filterApp.label,
            subTitle = filterApp.packageName,
            summary = filterApp.versionName,
            iconSource = filterApp.packageInfo,
        )
        BlockerScrollableTabRow(
            selectedTabIndex = tabState.currentIndex,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tabs = {
                tabState.items.forEachIndexed { index, item ->
                    BlockerTab(
                        selected = item == tabState.selectedItem,
                        onClick = { switchTab(item) },
                        text = { Text(text = stringResource(id = item.title)) },
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
@Preview
fun BottomSheetPreview() {
    val app = AppItem(
        packageName = "com.merxury.blocker",
        label = "Blocker test long name",
        versionName = "23.12.20",
        isSystem = false,
    )
    val tabState = TabState(
        items = listOf(
            Service,
            Activity,
            Provider,
        ),
        selectedItem = Service,
    )
    BlockerTheme {
        Surface {
            BottomSheet(filterApp = app, tabState = tabState, switchTab = {})
        }
    }
}
