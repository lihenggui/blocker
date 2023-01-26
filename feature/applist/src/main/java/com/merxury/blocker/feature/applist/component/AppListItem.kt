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

package com.merxury.blocker.feature.applist.component

import android.content.pm.PackageInfo
import android.content.res.Configuration
import android.view.MotionEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.applist.AppServiceStatus
import com.merxury.blocker.feature.applist.R.string

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AppListItem(
    label: String,
    packageName: String,
    versionName: String,
    versionCode: Long,
    packageInfo: PackageInfo?,
    appServiceStatus: AppServiceStatus?,
    onClick: (String) -> Unit,
    onClearCacheClick: (String) -> Unit,
    onClearDataClick: (String) -> Unit,
    onForceStopClick: (String) -> Unit,
    onUninstallClick: (String) -> Unit,
    onEnableClick: (String) -> Unit,
    onDisableClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var touchPoint: Offset by remember { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick(packageName) },
                    onLongClick = {
                        expanded = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )
                .pointerInteropFilter {
                    if (it.action == MotionEvent.ACTION_DOWN) {
                        touchPoint = Offset(it.x, it.y)
                    }
                    false
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AppIcon(packageInfo, iconModifier.size(48.dp))
            Spacer(modifier = Modifier.width(16.dp))
            AppContent(
                label = label,
                versionName = versionName,
                versionCode = versionCode,
                serviceStatus = appServiceStatus,
            )
            val offset = with(density) {
                DpOffset(touchPoint.x.toDp(), -touchPoint.y.toDp())
            }
            AppListItemMenuList(
                expanded = expanded,
                offset = offset,
                onClearCacheClick = { onClearCacheClick(packageName) },
                onClearDataClick = { onClearDataClick(packageName) },
                onForceStopClick = { onForceStopClick(packageName) },
                onUninstallClick = { onUninstallClick(packageName) },
                onEnableClick = { onEnableClick(packageName) },
                onDisableClick = { onDisableClick(packageName) },
                onDismissRequest = { expanded = false },
            )
        }
    }
}

@Composable
private fun AppIcon(info: PackageInfo?, modifier: Modifier = Modifier) {
    AsyncImage(
        modifier = modifier,
        model = ImageRequest.Builder(LocalContext.current)
            .data(info)
            .crossfade(true)
            .build(),
        contentDescription = null,
    )
}

@Composable
private fun AppContent(
    label: String,
    versionName: String,
    versionCode: Long,
    serviceStatus: AppServiceStatus?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(id = string.version_code_template, versionName, versionCode),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (serviceStatus != null) {
            Text(
                text = stringResource(
                    id = string.service_status_template,
                    serviceStatus.running,
                    serviceStatus.blocked,
                    serviceStatus.total,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        packageName = "com.merxury.blocker",
    )
    BlockerTheme {
        Surface {
            AppListItem(
                label = "Blocker",
                packageName = "com.merxury.blocker",
                versionName = "1.0.12",
                versionCode = 1206,
                packageInfo = PackageInfo(),
                appServiceStatus = appServiceStatus,
                onClick = {},
                onClearCacheClick = { },
                onClearDataClick = { },
                onForceStopClick = { },
                onUninstallClick = { },
                onEnableClick = { },
                onDisableClick = { },
            )
        }
    }
}

@Composable
@Preview
fun AppListItemWithoutServicePreview() {
    BlockerTheme {
        Surface {
            AppListItem(
                label = "Blocker",
                packageName = "com.merxury.blocker",
                versionName = "1.0.12",
                versionCode = 1206,
                packageInfo = PackageInfo(),
                appServiceStatus = null,
                onClick = {},
                onClearCacheClick = { },
                onClearDataClick = { },
                onForceStopClick = { },
                onUninstallClick = { },
                onEnableClick = { },
                onDisableClick = { },
            )
        }
    }
}

@Composable
@Preview
fun AppListItemWithLongAppName() {
    BlockerTheme {
        Surface {
            AppListItem(
                label = "AppNameWithVeryLongLongLongLongLongLongName",
                packageName = "com.merxury.blocker",
                versionName = "1.0.12",
                versionCode = 1206,
                packageInfo = PackageInfo(),
                appServiceStatus = null,
                onClick = {},
                onClearCacheClick = { },
                onClearDataClick = { },
                onForceStopClick = { },
                onUninstallClick = { },
                onEnableClick = { },
                onDisableClick = { },
            )
        }
    }
}
