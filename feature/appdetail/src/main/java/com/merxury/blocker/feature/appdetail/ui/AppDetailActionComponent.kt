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

package com.merxury.blocker.feature.appdetail.ui

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.feature.appdetail.R.string
import com.merxury.blocker.core.ui.R as UiR

@Composable
fun SearchActionMenu(onSearchModeChange: (Boolean) -> Unit) {
    IconButton(
        onClick = { onSearchModeChange(true) },
    ) {
        BlockerActionIcon(
            imageVector = BlockerIcons.Search,
            contentDescription = stringResource(id = UiR.string.core_ui_search_icon),
        )
    }
}

@Composable
fun MoreActionMenu(
    blockAllComponents: () -> Unit,
    enableAllComponents: () -> Unit,
    onAdvanceSortClick: () -> Unit = {},
    cancelSelection: () -> Unit = {},
    reverseSelection: () -> Unit = {},
    selectAll: () -> Unit = {},
    isSelectMode: Boolean = false,
) {
    val items = listOf(
        DropDownMenuItem(
            string.feature_appdetail_select_mutiple,
            blockAllComponents,
        ),
        DropDownMenuItem(
            string.feature_appdetail_block_all_of_this_page,
            blockAllComponents,
        ),
        DropDownMenuItem(
            string.feature_appdetail_enable_all_of_this_page,
            enableAllComponents,
        ),
        DropDownMenuItem(
            UiR.string.core_ui_sort_options,
            onAdvanceSortClick,
        ),
    )
    BlockerAppTopBarMenu(
        menuIcon = BlockerIcons.MoreVert,
        menuIconDesc = UiR.string.core_ui_more_menu,
        menuList = items,
    )
}
