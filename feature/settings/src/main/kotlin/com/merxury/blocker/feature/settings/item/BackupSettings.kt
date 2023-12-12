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

import android.content.ActivityNotFoundException
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration.Short
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.BlockerSettingItem
import com.merxury.blocker.core.ui.ItemHeader
import com.merxury.blocker.feature.settings.R.string
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun BackupSettings(
    backupSystemApps: Boolean,
    restoreSystemApp: Boolean,
    ruleBackupFolder: String,
    snackbarHostState: SnackbarHostState,
    onChangeBackupSystemApp: (Boolean) -> Unit,
    onChangeRestoreSystemApp: (Boolean) -> Unit,
    onChangeRuleBackupFolder: (Uri?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
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
            title = stringResource(id = string.feature_settings_backup),
            extraIconPadding = true,
        )
        BlockerSettingItem(
            icon = ImageVectorIcon(BlockerIcons.Folder),
            title = stringResource(id = string.feature_settings_folder_to_save),
            summary = ruleBackupFolder.ifEmpty {
                stringResource(id = string.feature_settings_directory_invalid_or_not_set)
            },
            onItemClick = {
                try {
                    getFolderResult.launch(null)
                } catch (e: ActivityNotFoundException) {
                    Timber.e(e, "No activity found to handle picking a directory.")
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(string.feature_settings_file_manager_required),
                            duration = Short,
                        )
                    }
                }
            },
        )
        SwitchSettingItem(
            itemRes = string.feature_settings_backup_system_apps,
            checked = backupSystemApps,
            onCheckedChange = onChangeBackupSystemApp,
        )
        SwitchSettingItem(
            itemRes = string.feature_settings_restore_system_apps,
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
                snackbarHostState = SnackbarHostState(),
                onChangeBackupSystemApp = {},
                onChangeRestoreSystemApp = {},
                onChangeRuleBackupFolder = {},
            )
        }
    }
}
