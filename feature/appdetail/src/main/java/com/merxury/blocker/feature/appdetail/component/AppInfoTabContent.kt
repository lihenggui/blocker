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

package com.merxury.blocker.feature.appdetail.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerItem
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
fun AppInfoTabContent(
    app: Application,
    modifier: Modifier = Modifier,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
    onExportIfw: () -> Unit,
    onImportIfw: () -> Unit,
    onResetIfw: () -> Unit,
) {
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(
                    rememberScrollState(),
                ),
        ) {
            MoreInfo(
                targetSdkVersion = app.packageInfo?.applicationInfo?.targetSdkVersion ?: 0,
                // TODO add min SDK detection
                minSdkVersion = 23,
                lastUpdateTime = app.lastUpdateTime,
                dataDir = app.packageInfo?.applicationInfo?.dataDir,
                onExportRules = onExportRules,
                onImportRules = onImportRules,
                onExportIfw = onExportIfw,
                onImportIfw = onImportIfw,
                onResetIfw = onResetIfw,
            )
        }
    }
}

@Composable
fun MoreInfo(
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
        BlockerItem(
            titleRes = string.target_sdk_version,
            summary = stringResource(
                id = string.data_with_explanation,
                targetSdkVersion,
                AndroidCodeName.getCodeName(targetSdkVersion),
            ),
            paddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        )
        BlockerItem(
            titleRes = string.minimum_sdk_version,
            summary = stringResource(
                id = string.data_with_explanation,
                minSdkVersion,
                AndroidCodeName.getCodeName(minSdkVersion),
            ),
            paddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        )
        BlockerItem(
            titleRes = string.last_update_time,
            summary = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault())
                .format(lastUpdateTime?.toJavaInstant()),
            paddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        )
        if (dataDir != null) {
            BlockerItem(
                titleRes = string.data_dir,
                summary = dataDir,
                paddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        Divider()
        BlockerRuleItem(onExportRules = onExportRules, onImportRules = onImportRules)
        Divider()
        IfwRuleItem(onExportIfw = onExportIfw, onImportIfw = onImportIfw, onResetIfw = onResetIfw)
    }
}

@Composable
fun BlockerRuleItem(
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        ItemHeader(itemRes = string.blocker_rules, paddingValues = PaddingValues(16.dp))
        BlockerItem(
            titleRes = string.export_rules,
            onItemClick = onExportRules,
            paddingValues = PaddingValues(16.dp),
        )
        BlockerItem(
            titleRes = string.import_rules,
            onItemClick = onImportRules,
            paddingValues = PaddingValues(16.dp),
        )
    }
}

@Composable
fun IfwRuleItem(
    onExportIfw: () -> Unit,
    onImportIfw: () -> Unit,
    onResetIfw: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        ItemHeader(itemRes = string.ifw_rules, paddingValues = PaddingValues(16.dp))
        BlockerItem(
            titleRes = string.export_ifw_rules,
            onItemClick = onExportIfw,
            paddingValues = PaddingValues(16.dp),
        )
        BlockerItem(
            titleRes = string.import_ifw_rules,
            onItemClick = onImportIfw,
            paddingValues = PaddingValues(16.dp),
        )
        BlockerItem(
            titleRes = string.reset_ifw,
            onItemClick = onResetIfw,
            paddingValues = PaddingValues(16.dp),
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
            AppInfoTabContent(
                app = app,
                onExportRules = {},
                onImportRules = {},
                onExportIfw = {},
                onImportIfw = {},
                onResetIfw = {},
            )
        }
    }
}
