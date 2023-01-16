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

package com.merxury.blocker.feature.applist.component

import androidx.compose.runtime.Composable
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.AppSorting.FIRST_INSTALL_TIME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.FIRST_INSTALL_TIME_DESCENDING
import com.merxury.blocker.core.model.preference.AppSorting.LAST_UPDATE_TIME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.LAST_UPDATE_TIME_DESCENDING
import com.merxury.blocker.core.model.preference.AppSorting.NAME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.NAME_DESCENDING
import com.merxury.blocker.feature.applist.R

@Composable
fun TopAppBarSortMenu(onSortingUpdate: (AppSorting) -> Unit) {
    val items = listOf(
        DropDownMenuItem(
            textRes = R.string.name_asc,
            onClick = { onSortingUpdate(NAME_ASCENDING) }
        ),
        DropDownMenuItem(
            textRes = R.string.name_desc,
            onClick = { onSortingUpdate(NAME_DESCENDING) }
        ),
        DropDownMenuItem(
            textRes = R.string.installation_date,
            onClick = { onSortingUpdate(FIRST_INSTALL_TIME_ASCENDING) }
        ),
        DropDownMenuItem(
            textRes = R.string.installation_date,
            onClick = { onSortingUpdate(FIRST_INSTALL_TIME_DESCENDING) }
        ),
        DropDownMenuItem(
            textRes = R.string.last_update_time,
            onClick = { onSortingUpdate(LAST_UPDATE_TIME_ASCENDING) }
        ),
        DropDownMenuItem(
            textRes = R.string.last_update_time,
            onClick = { onSortingUpdate(LAST_UPDATE_TIME_DESCENDING) }
        ),
    )
    BlockerAppTopBarMenu(
        menuIcon = BlockerIcons.Sort,
        menuIconDesc = R.string.sort_menu,
        menuList = items
    )
}
