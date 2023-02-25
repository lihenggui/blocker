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
import com.merxury.blocker.feature.search.SearchScreenTabs
import com.merxury.blocker.feature.search.SearchScreenTabs.Activity
import com.merxury.blocker.feature.search.SearchScreenTabs.Provider
import com.merxury.blocker.feature.search.SearchScreenTabs.Receiver
import com.merxury.blocker.feature.search.SearchScreenTabs.Service
import com.merxury.blocker.feature.search.model.FilteredComponent
import com.merxury.blocker.feature.search.screen.SearchResultTabRow

@Composable
fun ComponentSearchResultContent(
    modifier: Modifier = Modifier,
    result: FilteredComponent,
    tabState: TabState<SearchScreenTabs>,
    switchTab: (SearchScreenTabs) -> Unit,
) {
    Column(modifier = modifier.defaultMinSize(1.dp)) {
        BottomSheetTopBar(
            title = result.app.label,
            subTitle = result.app.packageName,
            summary = result.app.versionName,
            iconSource = result.app.packageInfo,
        )
        SearchResultTabRow(tabState = tabState, switchTab = switchTab)
        when (tabState.selectedItem) {
            is Receiver -> {}
            is Service -> {}
            is Activity -> {}
            is Provider -> {}
            else -> {}
        }
    }
}

@Composable
@Preview
fun ComponentSearchResultContentPreview() {
    val app = AppItem(
        packageName = "com.merxury.blocker",
        label = "Blocker test long name",
        versionName = "23.12.20",
        isSystem = false,
    )
    val filterAppItem = FilteredComponent(
        app = app,
    )
    val bottomSheetTabState = TabState(
        items = listOf(
            Receiver(1),
            Service(1),
        ),
        selectedItem = Receiver(1),
    )
    BlockerTheme {
        Surface {
            ComponentSearchResultContent(
                result = filterAppItem,
                tabState = bottomSheetTabState,
                switchTab = {},
            )
        }
    }
}
