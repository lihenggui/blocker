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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.settings.R.string

@Composable
fun BackupSettings(
    backupSystemApps: Boolean,
    restoreSystemApp: Boolean,
    ruleBackupFolder: String,
    updateBackupSystemApp: (Boolean) -> Unit,
    updateRestoreSystemApp: (Boolean) -> Unit,
    updateRuleBackupFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(vertical = 4.dp),
    ) {
        SettingItemHeader(itemRes = string.backup)
        TwoRowsSettingItem(
            icon = BlockerIcons.Folder,
            itemRes = string.folder_to_save,
            itemValue = ruleBackupFolder.ifEmpty {
                stringResource(id = string.directory_invalid_or_not_set)
            },
            onClick = {},
        )
        SwitchSettingItem(
            itemRes = string.backup_system_apps,
            checked = backupSystemApps,
            onCheckedChange = updateBackupSystemApp,
        )
        SwitchSettingItem(
            itemRes = string.show_service_info,
            checked = restoreSystemApp,
            onCheckedChange = updateRestoreSystemApp,
        )
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun BackupSettingsPreview() {
    BlockerTheme {
        Surface {
            BackupSettings(
                backupSystemApps = false,
                restoreSystemApp = true,
                ruleBackupFolder = "/emulated/0/Blocker",
                updateBackupSystemApp = {},
                updateRestoreSystemApp = {},
                updateRuleBackupFolder = {},
            )
        }
    }
}
