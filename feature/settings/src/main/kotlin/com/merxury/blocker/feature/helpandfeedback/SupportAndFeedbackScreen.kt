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

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.icon.Icon.DrawableResourceIcon
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.BlockerSettingItem
import com.merxury.blocker.core.ui.DevicePreviews
import com.merxury.blocker.feature.settings.R.string
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SupportAndFeedbackRoute(
    onNavigationClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: SupportFeedbackViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    SupportAndFeedbackScreen(
        onNavigationClick = onNavigationClick,
        onProjectHomeClick = { viewModel.openProjectHomepage(context) },
        onRulesRepositoryClick = { viewModel.openRulesRepository(context) },
        onReportBugClick = { viewModel.openReportBugPage(context) },
        onExportLogClick = {
            scope.launch {
                viewModel.exportErrorLog()
                    .collect { file ->
                        shareFile(
                            context = context,
                            file = file,
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                        )
                    }
            }
        },
        onTelegramGroupLinkClick = { viewModel.openGroupLink(context) },
        onDesignLinkClick = { viewModel.openDesignLink(context) },
        onOpenSourceLicenseClick = { viewModel.openOpenSourceLicence(context) },
    )
}

@SuppressLint("WrongConstant", "QueryPermissionsNeeded")
private suspend fun shareFile(
    context: Context,
    file: File?,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    if (file == null) {
        snackbarHostState.showSnackbar(
            message = context.getString(string.feature_settings_failed_to_export_logs),
        )
        return
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.FileProvider",
        file,
    )
    val subject = context.getString(string.feature_settings_export_log_title)
    val text = context.getString(string.feature_settings_provide_additional_details)
    val receiver = arrayOf("mercuryleee@gmail.com")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        clipData = ClipData.newRawUri("", uri)
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_EMAIL, receiver)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooserIntent = Intent.createChooser(intent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val activityInfo = chooserIntent.resolveActivityInfo(context.packageManager, intent.flags)
    if (activityInfo == null) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(string.feature_settings_no_sharable_app_found),
            )
        }
        return
    }
    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooserIntent)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportAndFeedbackScreen(
    onNavigationClick: () -> Unit = {},
    onProjectHomeClick: () -> Unit = {},
    onRulesRepositoryClick: () -> Unit = {},
    onExportLogClick: () -> Unit = {},
    onReportBugClick: () -> Unit = {},
    onTelegramGroupLinkClick: () -> Unit = {},
    onDesignLinkClick: () -> Unit = {},
    onOpenSourceLicenseClick: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            BlockerTopAppBar(
                title = stringResource(id = string.feature_settings_support_and_feedback),
                hasNavigationIcon = true,
                onNavigationClick = onNavigationClick,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column {
                BlockerSettingItem(
                    icon = DrawableResourceIcon(BlockerIcons.GitHub),
                    title = stringResource(id = string.feature_settings_project_homepage),
                    onItemClick = { onProjectHomeClick() },
                )
                BlockerSettingItem(
                    icon = ImageVectorIcon(BlockerIcons.Rule),
                    title = stringResource(id = string.feature_settings_rule_repository),
                    onItemClick = { onRulesRepositoryClick() },
                )
                BlockerSettingItem(
                    icon = ImageVectorIcon(BlockerIcons.BugReport),
                    title = stringResource(id = string.feature_settings_report_bugs_or_submit_ideas),
                    onItemClick = { onReportBugClick() },
                )
                BlockerSettingItem(
                    icon = ImageVectorIcon(BlockerIcons.Log),
                    title = stringResource(id = string.feature_settings_export_error_log),
                    onItemClick = { onExportLogClick() },
                )
                BlockerSettingItem(
                    icon = DrawableResourceIcon(BlockerIcons.Telegram),
                    title = stringResource(id = string.feature_settings_telegram_group),
                    onItemClick = { onTelegramGroupLinkClick() },
                )
                BlockerSettingItem(
                    icon = ImageVectorIcon(BlockerIcons.DesignService),
                    title = stringResource(id = string.feature_settings_designers_homepage),
                    onItemClick = { onDesignLinkClick() },
                )
                BlockerSettingItem(
                    icon = ImageVectorIcon(BlockerIcons.DocumentScanner),
                    title = stringResource(id = string.feature_settings_open_source_licenses),
                    onItemClick = { onOpenSourceLicenseClick() },
                )
            }
        }
    }
}

@Composable
@DevicePreviews
fun SupportAndFeedbackScreenPreview() {
    BlockerTheme {
        Surface {
            SupportAndFeedbackScreen()
        }
    }
}
