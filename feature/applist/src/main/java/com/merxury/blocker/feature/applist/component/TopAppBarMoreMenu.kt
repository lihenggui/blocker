package com.merxury.blocker.feature.applist.component

import androidx.compose.runtime.Composable
import com.merxury.blocker.core.designsystem.R.string
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.feature.applist.R

@Composable
fun TopAppBarMoreMenu() {
    val items = listOf(
        DropDownMenuItem(
            R.string.loading,
            BlockerIcons.CheckBox,
            {}
        ),
        DropDownMenuItem(
            R.string.loading,
            BlockerIcons.CheckBox,
            {}
        ),
        DropDownMenuItem(
            R.string.loading,
            BlockerIcons.CheckBox,
            {}
        ),
        DropDownMenuItem(
            R.string.loading,
            BlockerIcons.CheckBox,
            {}
        ),
        DropDownMenuItem(
            R.string.loading,
            BlockerIcons.CheckBox,
            {}
        )
    )
    BlockerAppTopBarMenu(
        menuIcon = BlockerIcons.MoreVert,
        menuIconDesc = string.more_menu,
        items = items
    )
}
