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

package com.merxury.blocker.feature.appdetail.impl.summary

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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.BlockerSettingItem
import com.merxury.blocker.core.ui.ItemHeader
import com.merxury.blocker.feature.appdetail.api.R.string
import com.merxury.blocker.feature.appdetail.impl.HeaderApp
import com.merxury.blocker.feature.appdetail.impl.previewHeaderApp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle.LONG
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * What the app info tab shows about [app], and the rule works for it: the Blocker rules of the
 * screen's user and the Intent Firewall rules, which every user shares.
 */
@Composable
internal fun SummaryContent(
    app: HeaderApp,
    modifier: Modifier = Modifier,
    isLibCheckerInstalled: Boolean = false,
    onShowAppInfoClick: () -> Unit = {},
    onExportRules: () -> Unit = {},
    onImportRules: () -> Unit = {},
    onExportIfw: () -> Unit = {},
    onImportIfw: () -> Unit = {},
    onResetIfw: () -> Unit = {},
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
                    targetSdkVersion = app.targetSdk,
                    minSdkVersion = app.minSdk,
                    lastUpdateTime = app.lastUpdateTime,
                    isLibCheckerInstalled = isLibCheckerInstalled,
                    onShowAppInfoClick = onShowAppInfoClick,
                    onExportRules = onExportRules,
                    onImportRules = onImportRules,
                    onExportIfw = onExportIfw,
                    onImportIfw = onImportIfw,
                    onResetIfw = onResetIfw,
                )
            }
        }
    }
}

@Composable
private fun AppSummary(
    targetSdkVersion: Int,
    minSdkVersion: Int,
    lastUpdateTime: Instant,
    isLibCheckerInstalled: Boolean,
    onShowAppInfoClick: () -> Unit,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
    onExportIfw: () -> Unit,
    onImportIfw: () -> Unit,
    onResetIfw: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    Column(modifier = modifier) {
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_api_target_sdk_version),
            summary = stringResource(
                id = string.feature_appdetail_api_data_with_explanation,
                targetSdkVersion,
                AndroidCodeName.getCodeName(targetSdkVersion),
            ),
        )
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_api_minimum_sdk_version),
            summary = stringResource(
                id = string.feature_appdetail_api_data_with_explanation,
                minSdkVersion,
                AndroidCodeName.getCodeName(minSdkVersion),
            ),
        )
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_api_last_update_time),
            summary = DateTimeFormatter.ofLocalizedDateTime(LONG)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault())
                .format(lastUpdateTime.toJavaInstant()),
        )
        if (isLibCheckerInstalled) {
            BlockerSettingItem(
                title = stringResource(id = string.feature_appdetail_api_app_info),
                summary = stringResource(id = string.feature_appdetail_api_app_info_with_libchecker_summary),
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
private fun BlockerRuleSection(
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ItemHeader(title = stringResource(id = string.feature_appdetail_api_blocker_rules))
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_api_export_rules),
            onItemClick = onExportRules,
        )
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_api_import_rules),
            onItemClick = onImportRules,
        )
    }
}

@Composable
private fun IfwRuleSection(
    onExportIfw: () -> Unit,
    onImportIfw: () -> Unit,
    onResetIfw: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ItemHeader(title = stringResource(id = string.feature_appdetail_api_ifw_rules))
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_api_export_ifw_rules),
            onItemClick = onExportIfw,
        )
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_api_import_ifw_rules),
            onItemClick = onImportIfw,
        )
        BlockerSettingItem(
            title = stringResource(id = string.feature_appdetail_api_reset_ifw),
            onItemClick = onResetIfw,
        )
    }
}

@Composable
@Preview
private fun PreviewAppInfoTabContent() {
    BlockerTheme {
        Surface {
            SummaryContent(app = previewHeaderApp)
        }
    }
}
