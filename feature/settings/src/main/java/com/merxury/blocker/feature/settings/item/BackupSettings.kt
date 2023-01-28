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

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerItem
import com.merxury.blocker.core.designsystem.component.ItemHeader
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.settings.R.string

@Composable
fun BackupSettings(
    backupSystemApps: Boolean,
    restoreSystemApp: Boolean,
    ruleBackupFolder: String,
    onChangeBackupSystemApp: (Boolean) -> Unit,
    onChangeRestoreSystemApp: (Boolean) -> Unit,
    onChangeRuleBackupFolder: (Uri?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val getFolderResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            onChangeRuleBackupFolder(uri)
        },
    )
    Column(
        modifier = modifier
            .padding(vertical = 4.dp),
    ) {
        ItemHeader(
            itemRes = string.backup,
            paddingValues = PaddingValues(vertical = 16.dp, horizontal = 56.dp),
        )
        BlockerItem(
            icon = ImageVectorIcon(BlockerIcons.Folder),
            titleRes = string.folder_to_save,
            summary = ruleBackupFolder.ifEmpty {
                stringResource(id = string.directory_invalid_or_not_set)
            },
            onItemClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                if (intent.resolveActivity(context.packageManager) != null) {
                    getFolderResult.launch(null)
                } else {
                    Toast.makeText(context, string.file_manager_required, Toast.LENGTH_LONG).show()
                }
            },
            paddingValues = PaddingValues(16.dp),
        )
        SwitchSettingItem(
            itemRes = string.backup_system_apps,
            checked = backupSystemApps,
            onCheckedChange = onChangeBackupSystemApp,
        )
        SwitchSettingItem(
            itemRes = string.restore_system_apps,
            checked = restoreSystemApp,
            onCheckedChange = onChangeRestoreSystemApp,
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
                onChangeBackupSystemApp = {},
                onChangeRestoreSystemApp = {},
                onChangeRuleBackupFolder = {},
            )
        }
    }
}
