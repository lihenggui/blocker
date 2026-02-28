/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.feature.impl.helpandfeedback

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.icon.Icon.DrawableResourceIcon
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.BlockerSettingItem
import com.merxury.blocker.core.ui.PreviewDevices
import com.merxury.blocker.feature.settings.api.R.string
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SupportAndFeedbackScreen(
    onNavigationClick: () -> Unit,
    navigateToLicenses: () -> Unit,
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
        onOpenSourceLicenseClick = navigateToLicenses,
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
            message = context.getString(string.feature_settings_api_failed_to_export_logs),
        )
        return
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.FileProvider",
        file,
    )
    val subject = context.getString(string.feature_settings_api_export_log_title)
    val text = context.getString(string.feature_settings_api_provide_additional_details)
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
                message = context.getString(string.feature_settings_api_no_sharable_app_found),
            )
        }
        return
    }
    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooserIntent)
}

@Composable
fun SupportAndFeedbackScreen(
    modifier: Modifier = Modifier,
    onNavigationClick: () -> Unit = {},
    onProjectHomeClick: () -> Unit = {},
    onRulesRepositoryClick: () -> Unit = {},
    onExportLogClick: () -> Unit = {},
    onReportBugClick: () -> Unit = {},
    onTelegramGroupLinkClick: () -> Unit = {},
    onDesignLinkClick: () -> Unit = {},
    onOpenSourceLicenseClick: () -> Unit = {},
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BlockerTopAppBar(
            title = stringResource(id = string.feature_settings_api_support_and_feedback),
            hasNavigationIcon = true,
            onNavigationClick = onNavigationClick,
        )
        BlockerSettingItem(
            icon = DrawableResourceIcon(BlockerIcons.GitHub),
            title = stringResource(id = string.feature_settings_api_project_homepage),
            onItemClick = { onProjectHomeClick() },
        )
        BlockerSettingItem(
            icon = ImageVectorIcon(BlockerIcons.Rule),
            title = stringResource(id = string.feature_settings_api_rule_repository),
            onItemClick = { onRulesRepositoryClick() },
        )
        BlockerSettingItem(
            icon = ImageVectorIcon(BlockerIcons.BugReport),
            title = stringResource(id = string.feature_settings_api_report_bugs_or_submit_ideas),
            onItemClick = { onReportBugClick() },
        )
        BlockerSettingItem(
            icon = ImageVectorIcon(BlockerIcons.Log),
            title = stringResource(id = string.feature_settings_api_export_error_log),
            onItemClick = { onExportLogClick() },
        )
        BlockerSettingItem(
            icon = DrawableResourceIcon(BlockerIcons.Telegram),
            title = stringResource(id = string.feature_settings_api_telegram_group),
            onItemClick = { onTelegramGroupLinkClick() },
        )
        BlockerSettingItem(
            icon = ImageVectorIcon(BlockerIcons.DesignService),
            title = stringResource(id = string.feature_settings_api_designers_homepage),
            onItemClick = { onDesignLinkClick() },
        )
        BlockerSettingItem(
            icon = ImageVectorIcon(BlockerIcons.DocumentScanner),
            title = stringResource(id = string.feature_settings_api_open_source_licenses),
            onItemClick = { onOpenSourceLicenseClick() },
        )
    }
}

@Composable
@PreviewDevices
private fun SupportAndFeedbackScreenPreview() {
    BlockerTheme {
        Surface {
            SupportAndFeedbackScreen()
        }
    }
}
