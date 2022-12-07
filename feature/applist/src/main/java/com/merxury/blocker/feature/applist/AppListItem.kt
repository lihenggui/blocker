package com.merxury.blocker.feature.applist

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme

@Composable
fun AppListItem(
    appIconUrl: String,
    packageName: String,
    versionName: String?,
    serviceStatus: String,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable { onClick },
    ) {
        AppIcon(appIconUrl = appIconUrl)
        Spacer(modifier = Modifier.width(16.dp))
        AppContent(appName = packageName, appVersion = versionName, serviceStatus = serviceStatus)
    }
}

@Composable
private fun AppIcon(appIconUrl: String, modifier: Modifier = Modifier) {
    if (appIconUrl.isEmpty()) {
        Icon(
            modifier = modifier.padding(4.dp),
            imageVector = BlockerIcons.NoApp,
            contentDescription = null, // decorative image
        )
    } else {
        AsyncImage(
            model = appIconUrl,
            contentDescription = null,
            modifier = modifier
        )
    }
}

@Composable
private fun AppContent(
    appName: String,
    appVersion: String?,
    serviceStatus: String,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = appName,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        if (appVersion != null) {
            if (appVersion.isNotEmpty()) {
                Text(
                    text = appVersion,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (serviceStatus.isNotEmpty()) {
            Text(
                text = serviceStatus,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun AppListItemPreview() {
    BlockerTheme {
        AppListItem(
            appIconUrl = "",
            packageName = "RC Downloader",
            versionName = "1.0.12",
            serviceStatus = "服务：正在运行0个，已组织0个，共计11个.",
            onClick = {}
        )
    }
}

@Composable
@Preview("Item without service status")
fun AppListItemWithoutServicePreview() {
    BlockerTheme {
        AppListItem(
            appIconUrl = "",
            packageName = "RC Downloader",
            versionName = "1.0.12",
            serviceStatus = "",
            onClick = {}
        )
    }
}

@Composable
@Preview("Item without version")
fun AppListItemWithoutVersionPreview() {
    BlockerTheme {
        AppListItem(
            appIconUrl = "",
            packageName = "RC Downloader",
            versionName = "",
            serviceStatus = "服务：正在运行0个，已组织0个，共计11个.",
            onClick = {}
        )
    }
}
