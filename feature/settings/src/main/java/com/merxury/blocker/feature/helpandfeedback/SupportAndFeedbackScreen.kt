/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.feature.helpandfeedback

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.icon.Icon.DrawableResourceIcon
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.helpandfeedback.item.Item
import com.merxury.blocker.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportAndFeedbackRoute(
    onNavigationClick: () -> Unit,
    viewModel: SupportFeedbackViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    Column {
        BlockerTopAppBar(
            titleRes = R.string.support_and_feedback,
            onNavigationClick = onNavigationClick,
        )
        SupportAndFeedbackScreen(
            onProjectHomeClick = { viewModel.openProjectHomepage(context) },
            onTelegramGroupLinkClick = { viewModel.openGroupLink(context) },
            onExportLogClick = { viewModel.exportErrorLog() },
        )
    }
}

@Composable
fun SupportAndFeedbackScreen(
    onProjectHomeClick: () -> Unit,
    onTelegramGroupLinkClick: () -> Unit,
    onExportLogClick: () -> Unit,
) {
    Column {
        Item(
            icon = DrawableResourceIcon(BlockerIcons.GitHub),
            titleRes = R.string.project_homepage,
            onClick = { onProjectHomeClick() },
        )
        Item(
            icon = DrawableResourceIcon(BlockerIcons.Telegram),
            titleRes = R.string.telegram_group,
            onClick = { onTelegramGroupLinkClick() },
        )
        Item(
            icon = ImageVectorIcon(BlockerIcons.BugReport),
            titleRes = R.string.export_error_log,
            onClick = { onExportLogClick() },
        )
    }
}

@Composable
@Preview
fun SupportAndFeedbackScreenPreview() {
    BlockerTheme {
        Surface {
            SupportAndFeedbackScreen(
                onProjectHomeClick = {},
                onTelegramGroupLinkClick = {},
                onExportLogClick = {},
            )
        }
    }
}
