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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerSwitch
import com.merxury.blocker.core.designsystem.component.ThemePreviews
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.DevicePreviews
import com.merxury.blocker.feature.settings.R

@Composable
fun SwitchSettingItem(
    @StringRes itemRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp)
            .padding(start = 56.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(id = itemRes),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.width(16.dp))
        BlockerSwitch(
            checked = checked,
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
