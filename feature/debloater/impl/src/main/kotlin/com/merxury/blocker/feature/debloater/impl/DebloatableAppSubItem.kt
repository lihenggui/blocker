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

package com.merxury.blocker.feature.debloater.impl

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.designsystem.theme.condensedRegular
import com.merxury.blocker.feature.appdebloater.api.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebloatableAppSubItem(
    item: DebloatableComponentUiItem,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSwitchClick: (DebloatableComponentUiItem, Boolean) -> Unit = { _, _ -> },
    isSelected: Boolean = false,
) {
    val animatedColor = animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.background
        },
        animationSpec = tween(300, 0, LinearEasing),
        label = "color",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { }
            .background(color = animatedColor.value)
            .padding(start = 16.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
            val displayTitle = item.entity.displayName.takeIf { it.isNotBlank() } ?: item.entity.simpleName
            BlockerBodyLargeText(
                modifier = Modifier.fillMaxWidth(),
                text = displayTitle,
            )
            BlockerBodyMediumText(
                text = item.entity.componentName,
                style = MaterialTheme.typography.bodyMedium.condensedRegular(),
            )

            // Display chips for different component types
            val hasAnyChip = item.isLauncherEntry || item.isDeeplinkEntry || item.isShareableComponent ||
                item.isWakelockComponent || item.isAutoStartReceiver || item.isExportedNoPerm ||
                item.isForegroundService || item.isPushService || item.isDangerousProvider
            if (hasAnyChip) {
                Spacer(modifier = Modifier.padding(top = 4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (item.isLauncherEntry) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = stringResource(R.string.feature_debloater_api_launcher_entry),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (item.isShareableComponent) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = stringResource(R.string.feature_debloater_api_share_component),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (item.isDeeplinkEntry) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = stringResource(R.string.feature_debloater_api_deeplink_entry),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (item.isWakelockComponent) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = stringResource(R.string.feature_debloater_api_filter_chip_wakelock),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (item.isAutoStartReceiver) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = stringResource(R.string.feature_debloater_api_filter_chip_auto_start),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (item.isExportedNoPerm) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = stringResource(R.string.feature_debloater_api_filter_chip_exported_no_perm),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (item.isForegroundService) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = stringResource(R.string.feature_debloater_api_filter_chip_foreground_service),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (item.isPushService) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = stringResource(R.string.feature_debloater_api_filter_chip_push_service),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (item.isDangerousProvider) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = stringResource(R.string.feature_debloater_api_filter_chip_dangerous_provider),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        BlockerSwitch(
            checked = enabled,
            onCheckedChange = {
                onSwitchClick(item, !enabled)
            },
        )
    }
}

@Composable
@PreviewThemes
private fun DebloatableAppSubItemPreview() {
    BlockerTheme {
        Surface {
            DebloatableAppSubItem(
                item = DebloaterPreviewParameterData.debloaterComponentUiItem,
                enabled = true,
            )
        }
    }
}

@Composable
@PreviewThemes
private fun DebloatableAppSubItemDisabledPreview() {
    BlockerTheme {
        Surface {
            DebloatableAppSubItem(
                item = DebloaterPreviewParameterData.debloaterComponentUiItem,
                enabled = false,
            )
        }
    }
}

@Composable
@PreviewThemes
private fun DebloatableAppSubItemSelectedPreview() {
    BlockerTheme {
        Surface {
            DebloatableAppSubItem(
                item = DebloaterPreviewParameterData.debloaterComponentUiItem,
                enabled = true,
                isSelected = true,
            )
        }
    }
}

@Composable
@PreviewThemes
private fun DebloatableAppSubItemLauncherOnlyPreview() {
    BlockerTheme {
        Surface {
            DebloatableAppSubItem(
                item = DebloaterPreviewParameterData.launcherOnlyComponent,
                enabled = true,
            )
        }
    }
}

@Composable
@Preview
private fun DebloatableAppSubItemShareOnlyPreview() {
    BlockerTheme {
        Surface {
            DebloatableAppSubItem(
                item = DebloaterPreviewParameterData.shareComponent,
                enabled = true,
            )
        }
    }
}
