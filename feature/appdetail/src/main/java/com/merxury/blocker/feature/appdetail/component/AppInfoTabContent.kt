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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppInfoTabContent(
    app: Application,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val refreshing by remember { mutableStateOf(isRefreshing) }
    val refreshingState = rememberPullRefreshState(refreshing, onRefresh)
    Box(modifier.pullRefresh(refreshingState)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MoreInfo(
                targetSdkVersion = app.packageInfo?.applicationInfo?.targetSdkVersion ?: 0,
                // TODO add min SDK detection
                miniSdkVersion = 23,
                lastUpdateTime = app.lastUpdateTime,
                dataDir = app.packageInfo?.applicationInfo?.dataDir
            )
        }
        PullRefreshIndicator(
            refreshing = refreshing,
            state = refreshingState,
            modifier = Modifier.align(Alignment.TopCenter),
            scale = true
        )
    }
}

@Composable
fun MoreInfo(
    targetSdkVersion: Int,
    miniSdkVersion: Int,
    lastUpdateTime: Instant?,
    dataDir: String?
) {
    Column {
        MoreInfoItem(itemId = string.target_sdk_version, itemInfo = targetSdkVersion.toString())
        MoreInfoItem(itemId = string.minimum_sdk_version, itemInfo = miniSdkVersion.toString())
        MoreInfoItem(itemId = string.last_update_time, itemInfo = lastUpdateTime.toString())
        if (dataDir != null) {
            MoreInfoItem(itemId = string.data_dir, itemInfo = dataDir)
        }
        Divider()
        BlockerRuleItem()
        Divider()
        IfwRuleItem()
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
fun BlockerRuleItem() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Blocker rules",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = string.export_rules),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = string.import_rules),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun IfwRuleItem() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "IFW rules",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = string.export_ifw_rules),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = string.import_ifw_rules),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Reset IFW",
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
                isRefreshing = false,
                onRefresh = {}
            )
        }
    }
}
