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

package com.merxury.blocker.feature.appdetail.summary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.ui.BlockerSettingItem
import com.merxury.blocker.core.ui.ItemHeader
import com.merxury.blocker.feature.appdetail.R.string
import kotlinx.datetime.Clock.System
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle.LONG
import java.util.Locale

@Composable
fun SummaryContent(
    app: AppItem,
    modifier: Modifier = Modifier,
    isLibCheckerInstalled: Boolean = false,
    onShowAppInfoClick: () -> Unit = {},
    onExportRules: (String) -> Unit = {},
    onImportRules: (String) -> Unit = {},
    onExportIfw: (String) -> Unit = {},
    onImportIfw: (String) -> Unit = {},
    onResetIfw: (String) -> Unit = {},
) {
    val listState: LazyListState = rememberLazyListState()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("appDetail:summaryContent"),
            state = listState,
        ) {
            item {
                AppSummary(
                    targetSdkVersion = app.packageInfo?.applicationInfo?.targetSdkVersion ?: 0,
                    minSdkVersion = app.minSdkVersion,
                    lastUpdateTime = app.lastUpdateTime,
                    dataDir = app.packageInfo?.applicationInfo?.dataDir,
                    isLibCheckerInstalled = isLibCheckerInstalled,
                    onShowAppInfoClick = onShowAppInfoClick,
                    onExportRules = { onExportRules(app.packageName) },
                    onImportRules = { onImportRules(app.packageName) },
                    onExportIfw = { onExportIfw(app.packageName) },
                    onImportIfw = { onImportIfw(app.packageName) },
                    onResetIfw = { onResetIfw(app.packageName) },
                )
            }
        }
    }
}

@Composable
fun AppSummary(
    targetSdkVersion: Int,
    minSdkVersion: Int,
    lastUpdateTime: Instant?,
    dataDir: String?,
    modifier: Modifier = Modifier,
    isLibCheckerInstalled: Boolean = false,
    onShowAppInfoClick: () -> Unit = {},
    onExportRules: () -> Unit = {},
    onImportRules: () -> Unit = {},
    onExportIfw: () -> Unit = {},
    onImportIfw: () -> Unit = {},
    onResetIfw: () -> Unit = {},
) {
    Column(modifier = modifier) {
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_target_sdk_version),
            summary = stringResource(
                id = string.feature_appdetail_data_with_explanation,
                targetSdkVersion,
                AndroidCodeName.getCodeName(targetSdkVersion),
            ),
        )
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_minimum_sdk_version),
            summary = stringResource(
                id = string.feature_appdetail_data_with_explanation,
                minSdkVersion,
                AndroidCodeName.getCodeName(minSdkVersion),
            ),
        )
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_last_update_time),
            summary = DateTimeFormatter.ofLocalizedDateTime(LONG)
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault())
                .format((lastUpdateTime ?: Instant.DISTANT_PAST).toJavaInstant()),
        )
        if (dataDir != null) {
            BlockerSettingItem(
                title = stringResource(id = string.feature_appdetail_data_dir),
                summary = dataDir,
            )
        }
        if (isLibCheckerInstalled) {
            BlockerSettingItem(
                title = stringResource(id = string.feature_appdetail_app_info),
                summary = stringResource(id = string.feature_appdetail_app_info_with_libchecker_summary),
                onItemClick = onShowAppInfoClick,
            )
        }
        HorizontalDivider()
        BlockerRuleSection(onExportRules = onExportRules, onImportRules = onImportRules)
        HorizontalDivider()
        IfwRuleSection(
            onExportIfw = onExportIfw,
            onImportIfw = onImportIfw,
            onResetIfw = onResetIfw,
        )
    }
}

@Composable
fun BlockerRuleSection(
    modifier: Modifier = Modifier,
    onExportRules: () -> Unit = {},
    onImportRules: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ItemHeader(title = stringResource(id = string.feature_appdetail_blocker_rules))
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_export_rules),
            onItemClick = onExportRules,
        )
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_import_rules),
            onItemClick = onImportRules,
        )
    }
}

@Composable
fun IfwRuleSection(
    modifier: Modifier = Modifier,
    onExportIfw: () -> Unit = {},
    onImportIfw: () -> Unit = {},
    onResetIfw: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ItemHeader(title = stringResource(id = string.feature_appdetail_ifw_rules))
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_export_ifw_rules),
            onItemClick = onExportIfw,
        )
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_import_ifw_rules),
            onItemClick = onImportIfw,
        )
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_reset_ifw),
            onItemClick = onResetIfw,
        )
    }
}

@Composable
@Preview
private fun PreviewAppInfoTabContent() {
    val app = AppItem(
        label = "Blocker",
        packageName = "com.mercury.blocker",
        versionName = "1.2.69-alpha",
        isEnabled = false,
        firstInstallTime = System.now(),
        lastUpdateTime = System.now(),
        packageInfo = null,
    )
    BlockerTheme {
        Surface {
            SummaryContent(
                app = app,
            )
        }
    }
}
