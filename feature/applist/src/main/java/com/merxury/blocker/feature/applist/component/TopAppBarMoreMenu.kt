package com.merxury.blocker.feature.applist.component

import androidx.compose.runtime.Composable
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.feature.applist.R

@Composable
fun TopAppBarMoreMenu() {
    val items = listOf(
        DropDownMenuItem(
            R.string.settings,
            {}
        ),
        DropDownMenuItem(
            R.string.support_and_feedback,
            {}
        )
    )
    BlockerAppTopBarMenu(
        menuIcon = BlockerIcons.MoreVert,
        menuIconDesc = R.string.more_menu,
        items = items
    )
}
