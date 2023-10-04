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

package com.merxury.blocker.core.ui.component

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.BlockerLabelSmallText
import com.merxury.blocker.core.designsystem.component.ThemePreviews
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.designsystem.theme.condensedRegular
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ComponentItem
import com.merxury.blocker.core.ui.R.string
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ComponentListItem(
    item: ComponentItem,
    enabled: Boolean,
    type: ComponentType,
    isServiceRunning: Boolean,
    modifier: Modifier = Modifier,
    navigateToComponentDetail: (String) -> Unit = { },
    onStopServiceClick: () -> Unit = { },
    onLaunchActivityClick: () -> Unit = { },
    onCopyNameClick: () -> Unit = { },
    onCopyFullNameClick: () -> Unit = { },
    onSwitchClick: (String, String, Boolean) -> Unit = { _, _, _ -> },
    isSelected: Boolean = false,
    isSelectedMode: Boolean = false,
    onSelect: (ComponentInfo) -> Unit = {},
    onDeselect: (ComponentInfo) -> Unit = {},
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
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (!isSelectedMode) {
                        navigateToComponentDetail(item.name)
                    } else {
                        if (isSelected) {
                            onDeselect(item.toComponentInfo())
                        } else {
                            onSelect(item.toComponentInfo())
                        }
                    }
                },
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
            .padding(start = 16.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BlockerBodyLargeText(
                    modifier = Modifier.weight(1F),
                    text = item.simpleName,
                )
                if (isServiceRunning) {
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
            }
            BlockerBodyMediumText(text = item.name)
            item.description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                BlockerBodyMediumText(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium.condensedRegular(),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = enabled,
            onCheckedChange = {
                onSwitchClick(item.packageName, item.name, !enabled)
            },
        )
        val offset = with(density) {
            DpOffset(touchPoint.x.toDp(), -touchPoint.y.toDp())
        }
        ComponentItemMenu(
            expanded = expanded,
            type = type,
            offset = offset,
            isServiceRunning = isServiceRunning,
            onStopServiceClick = onStopServiceClick,
            onLaunchActivityClick = onLaunchActivityClick,
            onCopyNameClick = onCopyNameClick,
            onCopyPackageNameClick = onCopyFullNameClick,
            onDismissRequest = { expanded = false },
        )
    }
}

@Composable
@ThemePreviews
fun ComponentItemPreview(
    @PreviewParameter(
        ComponentListPreviewParameterProvider::class,
    ) components: List<ComponentItem>,
) {
    BlockerTheme {
        Surface {
            ComponentListItem(
                item = components[0],
                enabled = true,
                type = ACTIVITY,
                isServiceRunning = true,
            )
        }
    }
}

@Composable
@ThemePreviews
fun ComponentItemSelectedPreview(
    @PreviewParameter(
        ComponentListPreviewParameterProvider::class,
    ) components: List<ComponentItem>,
) {
    BlockerTheme {
        Surface {
            ComponentListItem(
                item = components[1],
                enabled = false,
                type = RECEIVER,
                isServiceRunning = true,
                isSelectedMode = true,
                isSelected = true,
            )
        }
    }
}
