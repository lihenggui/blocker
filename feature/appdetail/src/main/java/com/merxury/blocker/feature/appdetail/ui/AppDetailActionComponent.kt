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
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.feature.appdetail.R.string

@Composable
fun SearchActionMenu(onSearchModeChange: (Boolean) -> Unit) {
    IconButton(
        onClick = { onSearchModeChange(true) },
    ) {
        BlockerActionIcon(
            imageVector = BlockerIcons.Search,
            contentDescription = null,
        )
    }
}

@Composable
fun MoreActionMenu(
    blockAllComponents: () -> Unit,
    enableAllComponents: () -> Unit,
) {
    val items = listOf(
        DropDownMenuItem(
            string.block_all_of_this_page,
            blockAllComponents,
        ),
        DropDownMenuItem(
            string.enable_all_of_this_page,
            enableAllComponents,
        ),
    )
    BlockerAppTopBarMenu(
        menuIcon = BlockerIcons.MoreVert,
        menuIconDesc = string.more_menu,
        menuList = items,
    )
}
