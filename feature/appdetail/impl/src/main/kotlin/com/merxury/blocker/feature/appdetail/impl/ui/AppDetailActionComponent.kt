/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.feature.appdetail.impl.ui

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.feature.appdetail.api.R.string
import com.merxury.blocker.core.ui.R as UiR

@Composable
fun SearchActionMenu(
    modifier: Modifier = Modifier,
    onSearchModeChange: (Boolean) -> Unit = {},
) {
    IconButton(
        modifier = modifier,
        onClick = { onSearchModeChange(true) },
    ) {
        BlockerActionIcon(
            imageVector = BlockerIcons.Search,
            contentDescription = stringResource(id = UiR.string.core_ui_search_icon),
        )
    }
}

/**
 * The menu of a tab that shows components.
 *
 * @param listActions whether to offer selection mode and sorting, which only a tab of rows has
 * @param batchActionsEnabled whether block all and enable all can be chosen, false while a batch runs
 */
@Composable
fun MoreActionMenu(
    modifier: Modifier = Modifier,
    blockAllComponents: () -> Unit = {},
    enableAllComponents: () -> Unit = {},
    onAdvanceSortClick: () -> Unit = {},
    switchSelectedMode: (Boolean) -> Unit = {},
    listActions: Boolean = true,
    batchActionsEnabled: Boolean = true,
) {
    val items = buildList {
        if (listActions) add(DropDownMenuItem(string.feature_appdetail_api_select_mutiple, { switchSelectedMode(true) }))
        add(DropDownMenuItem(string.feature_appdetail_api_block_all_of_this_page, blockAllComponents, enabled = batchActionsEnabled))
        add(DropDownMenuItem(string.feature_appdetail_api_enable_all_of_this_page, enableAllComponents, enabled = batchActionsEnabled))
        if (listActions) add(DropDownMenuItem(UiR.string.core_ui_sort_options, onAdvanceSortClick))
    }
    BlockerAppTopBarMenu(
        modifier = modifier,
        menuIcon = BlockerIcons.MoreVert,
        menuIconDesc = UiR.string.core_ui_more_menu,
        menuList = items,
    )
}
