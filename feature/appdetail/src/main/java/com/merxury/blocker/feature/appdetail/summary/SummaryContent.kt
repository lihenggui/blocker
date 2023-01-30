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

package com.merxury.blocker.feature.appdetail.summary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.merxury.blocker.core.designsystem.component.BlockerSettingItem
import com.merxury.blocker.core.designsystem.component.ItemHeader
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.utils.AndroidCodeName
import com.merxury.blocker.feature.appdetail.R.string
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant

@Composable
fun SummaryContent(
    app: Application,
    modifier: Modifier = Modifier,
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(
                    rememberScrollState(),
                ),
        ) {
            AppSummary(
                targetSdkVersion = app.packageInfo?.applicationInfo?.targetSdkVersion ?: 0,
                minSdkVersion = app.minSdkVersion,
                lastUpdateTime = app.lastUpdateTime,
                dataDir = app.packageInfo?.applicationInfo?.dataDir,
                onExportRules = { viewModel.exportRule(app.packageName) },
                onImportRules = { viewModel.importRule(app.packageName) },
                onExportIfw = { viewModel.exportIfwRule(app.packageName) },
                onImportIfw = { viewModel.importIfwRule(app.packageName) },
                onResetIfw = { viewModel.resetIfw(app.packageName) },
            )
        }
    }
}

@Composable
fun AppSummary(
    targetSdkVersion: Int,
    minSdkVersion: Int,
    lastUpdateTime: Instant?,
    dataDir: String?,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
    onExportIfw: () -> Unit,
    onImportIfw: () -> Unit,
    onResetIfw: () -> Unit,
) {
    Column {
        BlockerSettingItem(
            title = stringResource(id = string.target_sdk_version),
            summary = stringResource(
                id = string.data_with_explanation,
                targetSdkVersion,
                AndroidCodeName.getCodeName(targetSdkVersion),
            ),
        )
        BlockerSettingItem(
            title = stringResource(id = string.minimum_sdk_version),
            summary = stringResource(
                id = string.data_with_explanation,
                minSdkVersion,
                AndroidCodeName.getCodeName(minSdkVersion),
            ),
        )
        BlockerSettingItem(
            title = stringResource(id = string.last_update_time),
            summary = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault())
                .format(lastUpdateTime?.toJavaInstant()),
        )
        if (dataDir != null) {
            BlockerSettingItem(
                title = stringResource(id = string.data_dir),
                summary = dataDir,
            )
        }
        Divider()
        BlockerRuleSection(onExportRules = onExportRules, onImportRules = onImportRules)
        Divider()
        IfwRuleSection(
            onExportIfw = onExportIfw,
            onImportIfw = onImportIfw,
            onResetIfw = onResetIfw,
        )
    }
}

@Composable
fun BlockerRuleSection(
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        ItemHeader(title = stringResource(id = string.blocker_rules))
        BlockerSettingItem(
            title = stringResource(id = string.export_rules),
            onItemClick = onExportRules,
        )
        BlockerSettingItem(
            title = stringResource(id = string.import_rules),
            onItemClick = onImportRules,
        )
    }
}

@Composable
fun IfwRuleSection(
    onExportIfw: () -> Unit,
    onImportIfw: () -> Unit,
    onResetIfw: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        ItemHeader(title = stringResource(id = string.ifw_rules))
        BlockerSettingItem(
            title = stringResource(id = string.export_ifw_rules),
            onItemClick = onExportIfw,
        )
        BlockerSettingItem(
            title = stringResource(id = string.import_ifw_rules),
            onItemClick = onImportIfw,
        )
        BlockerSettingItem(
            title = stringResource(id = string.reset_ifw),
            onItemClick = onResetIfw,
        )
    }
}

@Composable
@Preview
fun PreviewAppInfoTabContent() {
    val app = Application(
        label = "Blocker",
        packageName = "com.mercury.blocker",
        versionName = "1.2.69-alpha",
        isEnabled = false,
        firstInstallTime = Clock.System.now(),
        lastUpdateTime = Clock.System.now(),
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
