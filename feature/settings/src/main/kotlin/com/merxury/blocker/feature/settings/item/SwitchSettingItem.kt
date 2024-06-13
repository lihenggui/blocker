/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.feature.settings.item

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.designsystem.component.ThemePreviews
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.icon.Icon
import com.merxury.blocker.core.designsystem.icon.Icon.DrawableResourceIcon
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.DevicePreviews
import com.merxury.blocker.feature.settings.R

@Composable
fun SwitchSettingItem(
    @StringRes itemRes: Int,
    @StringRes itemSummaryRes: Int? = null,
    icon: Icon? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit = {},
    enabled: Boolean = true,
) {
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 12.dp)
            .padding(
                if (icon != null) {
                    PaddingValues(start = 16.dp, end = 24.dp)
                } else {
                    PaddingValues(start = 56.dp, end = 24.dp)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            when (icon) {
                is ImageVectorIcon -> Icon(
                    imageVector = icon.imageVector,
                    contentDescription = null,
                    tint = if (!enabled) disabledColor else LocalContentColor.current,
                )

                is DrawableResourceIcon -> Icon(
                    painter = painterResource(id = icon.id),
                    contentDescription = null,
                    tint = if (!enabled) disabledColor else LocalContentColor.current,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(id = itemRes),
                style = MaterialTheme.typography.bodyLarge,
                color = if (!enabled) disabledColor else Color.Unspecified,
            )
            if (itemSummaryRes != null) {
                Text(
                    text = stringResource(id = itemSummaryRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!enabled) disabledColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))
        BlockerSwitch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
        )
    }
}

@Composable
@ThemePreviews
fun SwitchSettingsItemPreview() {
    BlockerTheme {
        Surface {
            SwitchSettingItem(
                itemRes = R.string.feature_settings_show_system_apps,
                checked = true,
            )
        }
    }
}

@Composable
@DevicePreviews
fun SwitchSettingsItemLongNamePreview() {
    BlockerTheme {
        Surface {
            SwitchSettingItem(
                itemRes = R.string.feature_settings_file_manager_required,
                checked = true,
            )
        }
    }
}

@Composable
@ThemePreviews
fun SwitchSettingsItemWithSummaryPreview() {
    BlockerTheme {
        Surface {
            SwitchSettingItem(
                itemRes = R.string.feature_settings_show_system_apps,
                itemSummaryRes = R.string.feature_settings_anonymous_statistics_summary,
                icon = ImageVectorIcon(BlockerIcons.Apps),
                checked = true,
                enabled = false,
            )
        }
    }
}
