package com.merxury.blocker.feature.applist.component

import androidx.compose.runtime.Composable
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.feature.applist.R

@Composable
fun TopAppBarSortMenu() {
    val items = listOf(
        DropDownMenuItem(
            R.string.name_asc,
            {}
        ),
        DropDownMenuItem(
            R.string.name_desc,
            {}
        ),
        DropDownMenuItem(
            R.string.installation_date,
            {}
        ),
        DropDownMenuItem(
            R.string.last_update_time,
            {}
        )
    )
    BlockerAppTopBarMenu(
        menuIcon = BlockerIcons.Sort,
        menuIconDesc = R.string.sort_menu,
        items = items
    )
}
