package com.merxury.blocker.feature.applist.component

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.applist.R

@Composable
fun TopAppBarMoreMenu(
    navigateToSettings: () -> Unit,
    navigateToFeedback: () -> Unit,
) {
    val items = listOf(
        DropDownMenuItem(
            R.string.settings,
            navigateToSettings
        ),
        DropDownMenuItem(
            R.string.support_and_feedback,
            navigateToFeedback
        )
    )
    BlockerAppTopBarMenu(
        menuIcon = BlockerIcons.MoreVert,
        menuIconDesc = R.string.more_menu,
        items = items
    )
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MoreMenuPreview() {
    BlockerTheme {
        Surface {
            TopAppBarMoreMenu(navigateToSettings = {}, navigateToFeedback = {})
        }
    }
}
