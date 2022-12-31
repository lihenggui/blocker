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
        items = items
    )
}
