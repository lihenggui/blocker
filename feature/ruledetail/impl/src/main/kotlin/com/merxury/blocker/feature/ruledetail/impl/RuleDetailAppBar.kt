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

package com.merxury.blocker.feature.ruledetail.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.BlockerDropdownMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SORT
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.feature.ruledetail.RuleDetailSortType
import com.merxury.blocker.core.ui.R.string as uistring
import com.merxury.blocker.feature.ruledetail.api.R.string as ruledetailString

@Composable
fun RuleDetailAppBarActions(
    modifier: Modifier = Modifier,
    appBarUiState: AppBarUiState = AppBarUiState(),
    sortType: RuleDetailSortType = RuleDetailSortType.NAME,
    onSortTypeChange: (RuleDetailSortType) -> Unit = {},
    blockAllComponents: () -> Unit = {},
    enableAllComponents: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val actions = appBarUiState.actions
        if (actions.isEmpty()) return
        if (actions.contains(SORT)) {
            SortActionMenu(
                sortType = sortType,
                onSortTypeChange = onSortTypeChange,
            )
        }
        if (actions.contains(MORE)) {
            MoreActionMenu(
                blockAllComponents = blockAllComponents,
                enableAllComponents = enableAllComponents,
            )
        }
    }
}

@Composable
fun MoreActionMenu(
    modifier: Modifier = Modifier,
    blockAllComponents: () -> Unit = {},
    enableAllComponents: () -> Unit = {},
) {
    val items = listOf(
        DropDownMenuItem(
            ruledetailString.feature_ruledetail_api_block_all_of_this_page,
            blockAllComponents,
        ),
        DropDownMenuItem(
            ruledetailString.feature_ruledetail_api_enable_all_of_this_page,
            enableAllComponents,
        ),
    )
    BlockerAppTopBarMenu(
        modifier = modifier,
        menuIcon = BlockerIcons.MoreVert,
        menuIconDesc = uistring.core_ui_more_menu,
        menuList = items,
    )
}

@Composable
fun SortActionMenu(
    sortType: RuleDetailSortType,
    onSortTypeChange: (RuleDetailSortType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = BlockerIcons.Sort,
                contentDescription = stringResource(ruledetailString.feature_ruledetail_api_sort_menu),
            )
        }
        BlockerDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = RuleDetailSortType.entries,
            onItemClick = { entry ->
                onSortTypeChange(entry)
            },
            itemText = { entry -> Text(text = stringResource(entry.labelRes)) },
            itemTrailingIcon = { entry ->
                if (entry == sortType) {
                    Icon(
                        imageVector = BlockerIcons.CheckSmall,
                        contentDescription = null,
                    )
                }
            },
        )
    }
}
