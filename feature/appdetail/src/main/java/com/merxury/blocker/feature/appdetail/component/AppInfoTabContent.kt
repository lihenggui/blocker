/*
 * Copyright 2022 Blocker
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

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.feature.appdetail.R.string
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

private data class ActionItems(
    @StringRes val label: Int,
    val icon: ImageVector,
)

@Composable
fun AppInfoTabContent(
    app: Application,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        AppBasicInfo(
            appName = app.label,
            packageName = app.packageName,
            versionName = app.versionName
        )
        ActionSection()
        MoreInfo(
            targetSdkVersion = app.packageInfo?.applicationInfo?.targetSdkVersion ?: 0,
            // TODO add min SDK detection
            miniSdkVersion = 23,
            lastUpdateTime = app.lastUpdateTime,
            dataDir = app.packageInfo?.applicationInfo?.dataDir
        )
    }
}

@Composable
fun AppBasicInfo(
    appName: String,
    packageName: String,
    versionName: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = BlockerIcons.NoApp, contentDescription = null)
        Text(text = appName, style = MaterialTheme.typography.headlineMedium)
        Text(text = packageName, style = MaterialTheme.typography.labelMedium)
        if (versionName != null) {
            Text(text = versionName, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun ActionSection() {
    val actionItems = listOf(
        ActionItems(string.launch_app, BlockerIcons.RocketLaunch),
        ActionItems(string.export_rules, ImageVector.vectorResource(id = BlockerIcons.Export)),
        ActionItems(string.import_rules, ImageVector.vectorResource(id = BlockerIcons.Import)),
        ActionItems(
            string.export_ifw_rules,
            ImageVector.vectorResource(id = BlockerIcons.Export)
        ),
        ActionItems(
            string.import_ifw_rules,
            ImageVector.vectorResource(id = BlockerIcons.Import)
        ),
    )
    Divider()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        actionItems.forEach {
            ActionItem(actionIcon = it.icon, actionId = it.label)
        }
    }
    Divider()
}

@Composable
fun MoreInfo(
    targetSdkVersion: Int,
    miniSdkVersion: Int,
    lastUpdateTime: Instant?,
    dataDir: String?
) {
    Column {
        Text(
            text = stringResource(id = string.more_info),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        MoreInfoItem(itemId = string.target_sdk_version, itemInfo = targetSdkVersion.toString())
        MoreInfoItem(itemId = string.minimum_sdk_version, itemInfo = miniSdkVersion.toString())
        MoreInfoItem(itemId = string.last_update_time, itemInfo = lastUpdateTime.toString())
        if (dataDir != null) {
            MoreInfoItem(itemId = string.data_dir, itemInfo = dataDir)
        }
    }
}

@Composable
fun ActionItem(
    actionIcon: ImageVector,
    actionId: Int,
) {
    Column(
        modifier = Modifier
            .padding(12.dp)
            .clickable { },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = actionIcon, contentDescription = null)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(id = actionId), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun MoreInfoItem(
    itemId: Int,
    itemInfo: String
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text(
            text = stringResource(id = itemId),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = itemInfo,
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
            AppInfoTabContent(app = app)
        }
    }
}
