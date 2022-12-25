package com.merxury.blocker.feature.applist.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            textRes = R.string.loading,
            onClick = {}
        ),
        DropDownMenuItem(
            textRes = R.string.loading,
            onClick = {}
        ),
        DropDownMenuItem(
            textRes = R.string.loading,
            onClick = {}
        ),
        DropDownMenuItem(
            textRes = R.string.loading,
            onClick = {}
        ),
        DropDownMenuItem(
            textRes = R.string.loading,
            onClick = {}
        ),
        DropDownMenuItem(
            textRes = R.string.loading,
            onClick = {}
        )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    ) {
        BlockerDropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            items = items
        )
    }
}
