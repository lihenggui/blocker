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

package com.merxury.blocker.core.ui.applist

import android.content.pm.PackageInfo
import android.view.MotionEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.BlockerLabelSmallText
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.AppServiceStatus
import com.merxury.blocker.core.ui.R.string
import com.merxury.blocker.core.ui.previewparameter.AppListPreviewParameterProvider

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AppListItem(
    label: String,
    packageName: String,
    versionName: String,
    versionCode: Long,
    isAppEnabled: Boolean,
    isAppRunning: Boolean,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    packageInfo: PackageInfo? = null,
    appServiceStatus: AppServiceStatus? = null,
    onClick: (String) -> Unit = {},
    onClearCacheClick: (String) -> Unit = {},
    onClearDataClick: (String) -> Unit = {},
    onForceStopClick: (String) -> Unit = {},
    onUninstallClick: (String) -> Unit = {},
    onEnableClick: (String) -> Unit = {},
    onDisableClick: (String) -> Unit = {},
    isSelected: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    var touchPoint: Offset by remember { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val animatedColor = animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.background,
        animationSpec = tween(300, 0, LinearEasing),
        label = "color",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .semantics(mergeDescendants = true) {
                selected = isSelected
            }
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
            .background(
                color = animatedColor.value,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AppIcon(packageInfo, iconModifier.size(48.dp))
        Spacer(modifier = Modifier.width(16.dp))
        AppContent(
            label = label,
            versionName = versionName,
            versionCode = versionCode,
            isAppEnabled = isAppEnabled,
            isAppRunning = isAppRunning,
            serviceStatus = appServiceStatus,
        )
        val offset = with(density) {
            DpOffset(touchPoint.x.toDp(), -touchPoint.y.toDp())
        }
        AppListItemMenuList(
            expanded = expanded,
            offset = offset,
            isAppRunning = isAppRunning,
            isAppEnabled = isAppEnabled,
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

@Composable
fun AppIcon(info: PackageInfo?, modifier: Modifier = Modifier) {
    AsyncImage(
        modifier = modifier,
        model = ImageRequest.Builder(LocalContext.current)
            .data(info)
            .error(BlockerIcons.Android)
            .placeholder(BlockerIcons.Android)
            .crossfade(false)
            .build(),
        contentDescription = null,
    )
}

@Composable
private fun AppContent(
    label: String,
    versionName: String,
    versionCode: Long,
    isAppEnabled: Boolean,
    isAppRunning: Boolean,
    serviceStatus: AppServiceStatus?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlockerBodyLargeText(
                modifier = Modifier.weight(1F),
                text = label,
            )
            if (isAppRunning) {
                Spacer(modifier = Modifier.width(8.dp))
                val indicatorColor = MaterialTheme.colorScheme.tertiary
                BlockerLabelSmallText(
                    modifier = Modifier
                        .drawBehind {
                            drawRoundRect(
                                color = indicatorColor,
                                cornerRadius = CornerRadius(x = 4.dp.toPx(), y = 4.dp.toPx()),
                            )
                        }
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    text = stringResource(id = string.core_ui_running),
                    color = MaterialTheme.colorScheme.onTertiary,
                )
            }
            if (!isAppEnabled) {
                Spacer(modifier = Modifier.width(8.dp))
                val indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                BlockerLabelSmallText(
                    modifier = Modifier
                        .drawBehind {
                            drawRoundRect(
                                color = indicatorColor,
                                cornerRadius = CornerRadius(x = 4.dp.toPx(), y = 4.dp.toPx()),
                            )
                        }
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    text = stringResource(id = string.core_ui_disabled),
                )
            }
        }
        BlockerBodyMediumText(
            text = stringResource(
                id = string.core_ui_version_code_template,
                versionName,
                versionCode,
            ),
        )
        if (serviceStatus != null) {
            BlockerBodyMediumText(
                text = stringResource(
                    id = string.core_ui_service_status_template,
                    serviceStatus.running,
                    serviceStatus.blocked,
                    serviceStatus.total,
                ),
            )
        }
    }
}

@Composable
@PreviewThemes
private fun AppListItemPreview(
    @PreviewParameter(AppListPreviewParameterProvider::class)
    appList: List<AppItem>,
) {
    BlockerTheme {
        Surface {
            AppListItem(
                label = appList[0].label,
                packageName = appList[0].packageName,
                versionName = appList[0].versionName,
                versionCode = appList[0].versionCode,
                isAppEnabled = appList[0].isEnabled,
                isAppRunning = appList[0].isRunning,
                packageInfo = appList[0].packageInfo,
                appServiceStatus = appList[0].appServiceStatus,
            )
        }
    }
}

@Composable
@Preview
private fun AppListItemWithoutServiceInfoPreview(
    @PreviewParameter(AppListPreviewParameterProvider::class)
    appList: List<AppItem>,
) {
    BlockerTheme {
        Surface {
            AppListItem(
                label = appList[1].label,
                packageName = appList[1].packageName,
                versionName = appList[1].versionName,
                versionCode = appList[1].versionCode,
                isAppEnabled = appList[1].isEnabled,
                isAppRunning = appList[1].isRunning,
                packageInfo = appList[1].packageInfo,
                appServiceStatus = appList[1].appServiceStatus,
            )
        }
    }
}

@Composable
@Preview
private fun AppListItemWithLongAppName(
    @PreviewParameter(AppListPreviewParameterProvider::class)
    appList: List<AppItem>,
) {
    BlockerTheme {
        Surface {
            AppListItem(
                label = appList[2].label,
                packageName = appList[2].packageName,
                versionName = appList[2].versionName,
                versionCode = appList[2].versionCode,
                isAppEnabled = appList[2].isEnabled,
                isAppRunning = appList[2].isRunning,
                packageInfo = appList[2].packageInfo,
                appServiceStatus = appList[2].appServiceStatus,
                isSelected = true,
            )
        }
    }
}
