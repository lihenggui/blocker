/*
 * Copyright 2024 Blocker
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

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.applist.R
import com.merxury.blocker.core.ui.R as UiR

@Composable
fun TopAppBarMoreMenu(
    navigateToSettings: () -> Unit,
    navigateToFeedback: () -> Unit,
) {
    val items = listOf(
        DropDownMenuItem(
            R.string.feature_applist_settings,
            navigateToSettings,
        ),
        DropDownMenuItem(
            R.string.feature_applist_support_and_feedback,
            navigateToFeedback,
        ),
    )
    BlockerAppTopBarMenu(
        menuIcon = BlockerIcons.MoreVert,
        menuIconDesc = UiR.string.core_ui_more_menu,
        menuList = items,
    )
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MoreMenuPreview() {
    BlockerTheme {
        Surface {
            TopAppBarMoreMenu(navigateToSettings = {}, navigateToFeedback = {})
        }
    }
}
