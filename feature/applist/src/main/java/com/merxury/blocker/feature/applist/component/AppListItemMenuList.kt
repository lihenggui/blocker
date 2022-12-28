package com.merxury.blocker.feature.applist.component

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.merxury.blocker.core.designsystem.component.BlockerDropdownMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.applist.R

@Composable
fun AppListItemMenuList(
    expanded: Boolean,
    onClearCacheClick: () -> Unit,
    onClearDataClick: () -> Unit,
    onForceStopClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onEnableClick: () -> Unit,
    onDisableClick: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val items = listOf(
        DropDownMenuItem(
            textRes = R.string.clear_cache,
            onClick = onClearCacheClick
        ),
        DropDownMenuItem(
            textRes = R.string.clear_data,
            onClick = onClearDataClick
        ),
        DropDownMenuItem(
            textRes = R.string.force_stop,
            onClick = onForceStopClick
        ),
        DropDownMenuItem(
            textRes = R.string.uninstall,
            onClick = onUninstallClick
        ),
        DropDownMenuItem(
            textRes = R.string.enable,
            onClick = onEnableClick
        ),
        DropDownMenuItem(
            textRes = R.string.disable,
            onClick = onDisableClick
        )
    )
    BlockerDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        items = items
    )
}

@Preview
@Composable
fun AppListItemMenuPreview() {
    BlockerTheme {
        Surface {
            AppListItemMenuList(
                expanded = true,
                onClearCacheClick = { },
                onClearDataClick = { },
                onForceStopClick = { },
                onUninstallClick = { },
                onEnableClick = { },
                onDisableClick = { },
                onDismissRequest = { },
            )
        }
    }
}
