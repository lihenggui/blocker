package com.merxury.blocker.feature.applist.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.merxury.blocker.core.designsystem.component.BlockerDropdownMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.icon.BlockerIcons

@Composable
fun AppMoreMenuList(
    expanded: Boolean = false,
    onDismissRequest: () -> Unit,
) {
    val items = listOf(
        DropDownMenuItem(
            "Show system app",
            BlockerIcons.CheckBox,
            {}
        ),
        DropDownMenuItem(
            "Show service info",
            BlockerIcons.CheckBox,
            {}
        ),
        DropDownMenuItem(
            "Order",
            BlockerIcons.CheckBox,
            {}
        )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopStart)
    ) {
        BlockerDropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            items = items
        )
    }
}

@Composable
fun AppListItemMenuList(
    expanded: Boolean = false,
    onDismissRequest: () -> Unit,
) {
    val items = listOf(
        DropDownMenuItem(
            text = "Clear cache",
            onClick = {}
        ),
        DropDownMenuItem(
            text = "Clear data",
            onClick = {}
        ),
        DropDownMenuItem(
            text = "Force stop",
            onClick = {}
        ),
        DropDownMenuItem(
            text = "Uninstall",
            onClick = {}
        ),
        DropDownMenuItem(
            text = "Enable",
            onClick = {}
        ),
        DropDownMenuItem(
            text = "Disable",
            onClick = {}
        )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopStart)
    ) {
        BlockerDropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            items = items
        )
    }
}
