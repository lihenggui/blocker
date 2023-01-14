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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.feature.appdetail.R.string
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Composable
fun AppInfoTabContent(
    app: Application,
    modifier: Modifier = Modifier,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
    onExportIfw: () -> Unit,
    onImportIfw: () -> Unit,
    onResetIfw: () -> Unit
) {
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(
                    rememberScrollState()
                )
        ) {
            MoreInfo(
                targetSdkVersion = app.packageInfo?.applicationInfo?.targetSdkVersion ?: 0,
                // TODO add min SDK detection
                miniSdkVersion = 23,
                lastUpdateTime = app.lastUpdateTime,
                dataDir = app.packageInfo?.applicationInfo?.dataDir,
                onExportRules = onExportRules,
                onImportRules = onImportRules,
                onExportIfw = onExportIfw,
                onImportIfw = onImportIfw,
                onResetIfw = onResetIfw
            )
        }
    }
}

@Composable
fun MoreInfo(
    targetSdkVersion: Int,
    miniSdkVersion: Int,
    lastUpdateTime: Instant?,
    dataDir: String?,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
    onExportIfw: () -> Unit,
    onImportIfw: () -> Unit,
    onResetIfw: () -> Unit
) {
    Column {
        MoreInfoItem(itemId = string.target_sdk_version, itemInfo = targetSdkVersion.toString())
        MoreInfoItem(itemId = string.minimum_sdk_version, itemInfo = miniSdkVersion.toString())
        MoreInfoItem(itemId = string.last_update_time, itemInfo = lastUpdateTime.toString())
        if (dataDir != null) {
            MoreInfoItem(itemId = string.data_dir, itemInfo = dataDir)
        }
        Divider()
        BlockerRuleItem(onExportRules = onExportRules, onImportRules = onImportRules)
        Divider()
        IfwRuleItem(onExportIfw = onExportIfw, onImportIfw = onImportIfw, onResetIfw = onResetIfw)
    }
}

@Composable
fun MoreInfoItem(
    itemId: Int,
    itemInfo: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = stringResource(id = itemId),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = itemInfo,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun BlockerRuleItem(
    onExportRules: () -> Unit,
    onImportRules: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        ItemHead(itemRes = string.blocker_rules)
        Item(itemRes = string.export_rules, onItemClick = onExportRules)
        Item(itemRes = string.import_rules, onItemClick = onImportRules)
    }
}

@Composable
fun IfwRuleItem(
    onExportIfw: () -> Unit,
    onImportIfw: () -> Unit,
    onResetIfw: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        ItemHead(itemRes = string.ifw_rules)
        Item(itemRes = string.export_ifw_rules, onItemClick = onExportIfw)
        Item(itemRes = string.import_ifw_rules, onItemClick = onImportIfw)
        Item(itemRes = string.reset_ifw, onItemClick = onResetIfw)
    }
}

@Composable
fun ItemHead(itemRes: Int) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = itemRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun Item(
    itemRes: Int,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = itemRes),
            style = MaterialTheme.typography.bodyMedium
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
                onResetIfw = {}
            )
        }
    }
}
