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

package com.merxury.blocker.feature.settings.item

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.settings.R

@Composable
fun TwoRowsSettingItem(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    itemRes: Int,
    itemValue: String,
    onClick: () -> Unit,
) {
    Box(modifier = modifier) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(id = itemRes),
                    modifier = modifier.padding(end = 16.dp),
                )
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
            Column {
                Text(
                    text = stringResource(id = itemRes),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = itemValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SingleRowSettingItem(
    itemRes: Int,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(vertical = 16.dp, horizontal = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = itemRes),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun SettingItemHeader(itemRes: Int) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = itemRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 56.dp),
        )
    }
}

@Composable
fun SwitchSettingItem(
    itemRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp)
            .padding(start = 56.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = itemRes),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun SettingsItemPreview() {
    BlockerTheme {
        Surface {
            TwoRowsSettingItem(
                icon = BlockerIcons.AutoFix,
                itemRes = R.string.controller_type,
                itemValue = "IFW",
                onClick = {},
            )
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun SettingsItemWithoutIconPreview() {
    BlockerTheme {
        Surface {
            TwoRowsSettingItem(
                itemRes = R.string.theme,
                itemValue = "Default",
                onClick = {},
            )
        }
    }
}

@Composable
@Preview
fun SettingsItemSinglePreview() {
    BlockerTheme {
        Surface {
            SingleRowSettingItem(
                itemRes = R.string.import_mat_rules,
                onItemClick = {},
            )
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun SwitchSettingsItemPreview() {
    BlockerTheme {
        Surface {
            SwitchSettingItem(
                itemRes = R.string.show_system_apps,
                checked = true,
                onCheckedChange = {},
            )
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun SettingsItemHeaderPreview() {
    BlockerTheme {
        Surface {
            SettingItemHeader(itemRes = R.string.backup)
        }
    }
}
