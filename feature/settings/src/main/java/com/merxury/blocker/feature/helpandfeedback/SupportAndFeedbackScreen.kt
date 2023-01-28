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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumedWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.merxury.blocker.core.designsystem.component.BlockerSettingItem
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.icon.Icon.DrawableResourceIcon
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.settings.R.string

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SupportAndFeedbackRoute(
    onNavigationClick: () -> Unit,
    viewModel: SupportFeedbackViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            BlockerTopAppBar(
                title = stringResource(id = string.support_and_feedback),
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = BlockerIcons.Back,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumedWindowInsets(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SupportAndFeedbackScreen(
                onProjectHomeClick = { viewModel.openProjectHomepage(context) },
                onRulesRepositoryClick = { viewModel.openRulesRepository(context) },
                onTelegramGroupLinkClick = { viewModel.openGroupLink(context) },
                onExportLogClick = { viewModel.exportErrorLog() },
            )
        }
    }
}

@Composable
fun SupportAndFeedbackScreen(
    onProjectHomeClick: () -> Unit,
    onRulesRepositoryClick: () -> Unit,
    onTelegramGroupLinkClick: () -> Unit,
    onExportLogClick: () -> Unit,
) {
    Column {
        BlockerSettingItem(
            icon = DrawableResourceIcon(BlockerIcons.GitHub),
            title = stringResource(id = string.project_homepage),
            onItemClick = { onProjectHomeClick() },
        )
        BlockerSettingItem(
            icon = ImageVectorIcon(BlockerIcons.Rule),
            title = stringResource(id = string.rule_repository),
            onItemClick = { onRulesRepositoryClick() },
        )
        BlockerSettingItem(
            icon = DrawableResourceIcon(BlockerIcons.Telegram),
            title = stringResource(id = string.telegram_group),
            onItemClick = { onTelegramGroupLinkClick() },
        )
        BlockerSettingItem(
            icon = ImageVectorIcon(BlockerIcons.BugReport),
            title = stringResource(id = string.export_error_log),
            onItemClick = { onExportLogClick() },
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
                onRulesRepositoryClick = {},
                onTelegramGroupLinkClick = {},
                onExportLogClick = {},
            )
        }
    }
}
