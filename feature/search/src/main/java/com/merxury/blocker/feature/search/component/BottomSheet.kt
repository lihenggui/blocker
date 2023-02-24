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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.applist.model.AppItem
import com.merxury.blocker.core.ui.bottomSheet.BottomSheetTopBar
import com.merxury.blocker.feature.search.R
import com.merxury.blocker.feature.search.model.FilteredComponentItem
import com.merxury.blocker.feature.search.model.SearchTabItem
import com.merxury.blocker.feature.search.screen.SearchResultTabRow

@Composable
fun BottomSheet(
    modifier: Modifier = Modifier,
    filterApp: FilteredComponentItem,
    tabState: TabState<SearchTabItem>,
    switchTab: (SearchTabItem) -> Unit,
) {
    Column(modifier = modifier.defaultMinSize(1.dp)) {
        BottomSheetTopBar(
            title = filterApp.app.label,
            subTitle = filterApp.app.packageName,
            summary = filterApp.app.versionName,
            iconSource = filterApp.app.packageInfo,
        )
        SearchResultTabRow(tabState = tabState, switchTab = switchTab)
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
    val filterAppItem = FilteredComponentItem(
        app = app,
    )
    val bottomSheetTabState = TabState(
        items = listOf(
            SearchTabItem(R.string.receiver, 1),
        ),
        selectedItem = SearchTabItem(R.string.receiver),
    )
    BlockerTheme {
        Surface {
            BottomSheet(filterApp = filterAppItem, tabState = bottomSheetTabState, switchTab = {})
        }
    }
}
