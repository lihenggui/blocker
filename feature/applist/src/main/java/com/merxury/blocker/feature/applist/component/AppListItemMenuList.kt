package com.merxury.blocker.feature.applist.component

import androidx.compose.runtime.Composable
import com.merxury.blocker.core.designsystem.component.BlockerDropdownMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.feature.applist.R

@Composable
fun AppListItemMenuList(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    val items = listOf(
        DropDownMenuItem(
            textRes = R.string.clear_cache,
            onClick = {}
        ),
        DropDownMenuItem(
            textRes = R.string.clear_data,
            onClick = {}
        ),
        DropDownMenuItem(
            textRes = R.string.force_stop,
            onClick = {}
        ),
        DropDownMenuItem(
            textRes = R.string.uninstall,
            onClick = {}
        ),
        DropDownMenuItem(
            textRes = R.string.enable,
            onClick = {}
        ),
        DropDownMenuItem(
            textRes = R.string.disable,
            onClick = {}
        )
    )
    BlockerDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        items = items
    )
}
