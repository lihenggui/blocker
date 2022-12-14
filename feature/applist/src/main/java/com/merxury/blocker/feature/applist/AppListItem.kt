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

package com.merxury.blocker.feature.applist

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.applist.R.string

@Composable
fun AppListItem(
    packageName: String,
    versionName: String,
    appServiceStatus: AppServiceStatus,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable { onClick(packageName) },
    ) {
        AppIcon(packageName)
        Spacer(modifier = Modifier.width(16.dp))
        AppContent(
            appName = packageName,
            appVersion = versionName,
            appServiceStatus = appServiceStatus
        )
    }
}

@Composable
private fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    Image(
        modifier = modifier,
        painter = rememberAsyncImagePainter(
            LocalContext.current.packageManager.getApplicationIcon(packageName)
        ),
        contentDescription = null
    )
}

@Composable
private fun AppContent(
    appName: String,
    appVersion: String,
    appServiceStatus: AppServiceStatus,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = appName,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        if (appVersion.isNotEmpty()) {
            Text(
                text = appVersion,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = stringResource(
                id = string.service_status_template,
                appServiceStatus.running,
                appServiceStatus.blocked,
                appServiceStatus.total
            ),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun AppListItemPreview() {
    val appServiceStatus = AppServiceStatus(
        running = 1,
        blocked = 2,
        total = 10,
        packageName = "com.merxury.blocker"
    )
    BlockerTheme {
        AppListItem(
            packageName = "com.merxury.blocker",
            versionName = "1.0.12",
            appServiceStatus = appServiceStatus,
            onClick = {}
        )
    }
}

@Composable
@Preview("Item without service status")
fun AppListItemWithoutServicePreview() {
    val appServiceStatus = AppServiceStatus(
        packageName = "com.merxury.blocker",
        running = 8,
        blocked = 2,
        total = 13,
    )
    BlockerTheme {
        AppListItem(
            packageName = "com.merxury.blocker",
            versionName = "1.0.12",
            appServiceStatus = appServiceStatus,
            onClick = {}
        )
    }
}

@Composable
@Preview("Item without version")
fun AppListItemWithoutVersionPreview() {
    val appServiceStatus = AppServiceStatus(
        running = 1,
        blocked = 2,
        total = 10,
        packageName = "com.merxury.blocker"
    )
    BlockerTheme {
        AppListItem(
            packageName = "com.merxury.blocker",
            versionName = "0.0.13",
            appServiceStatus = appServiceStatus,
            onClick = {}
        )
    }
}
