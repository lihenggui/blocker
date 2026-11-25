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

package com.merxury.blocker.feature.impl.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.designsystem.component.BlockerButtonAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerConfirmAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.rule.backup.BackupSet
import com.merxury.blocker.core.rule.entity.RuleWorkEvent
import com.merxury.blocker.core.ui.profile.ProfileLabel
import com.merxury.blocker.core.ui.profile.text
import com.merxury.blocker.feature.settings.api.R.string

enum class SettingsRuleAction {
    EXPORT,
    IMPORT,
    RESET_PM,
    IMPORT_MAT,
}

data class SettingsRuleTarget(val user: UserId, val label: ProfileLabel?)

data class SettingsRuleImport(val target: SettingsRuleTarget, val path: String, val restoreSystemApps: Boolean)

internal data class SettingsRuleEvent(val work: RuleWorkEvent, val target: ProfileLabel? = null)

sealed interface SettingsRuleDialog {
    data class Confirm(
        val action: SettingsRuleAction,
        val target: SettingsRuleTarget,
        val import: SettingsRuleImport? = null,
    ) : SettingsRuleDialog

    data class ChooseBackup(
        val import: SettingsRuleImport,
        val target: ProfileLabel,
        val sets: Map<BackupSet, ProfileLabel>,
    ) : SettingsRuleDialog
}

@Composable
internal fun SettingsRuleDialog(
    dialog: SettingsRuleDialog,
    onConfirm: () -> Unit,
    onChooseBackup: (BackupSet) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        is SettingsRuleDialog.Confirm -> ConfirmRuleAction(dialog, onConfirm, onDismiss)

        is SettingsRuleDialog.ChooseBackup -> BlockerButtonAlertDialog(
            onDismissRequest = onDismiss,
            title = stringResource(string.feature_settings_api_choose_rule_backup, dialog.target.text()),
            buttons = {
                Column {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        dialog.sets.forEach { (set, label) ->
                            BlockerTextButton(
                                onClick = { onChooseBackup(set) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(label.text())
                            }
                        }
                    }
                    BlockerTextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun ConfirmRuleAction(
    dialog: SettingsRuleDialog.Confirm,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val user = dialog.target.label
    val title = if (user == null) {
        stringResource(string.feature_settings_api_reset_pm_dialog_title)
    } else {
        val titleRes = when (dialog.action) {
            SettingsRuleAction.EXPORT -> string.feature_settings_api_confirm_user_export
            SettingsRuleAction.IMPORT -> string.feature_settings_api_confirm_user_import
            SettingsRuleAction.RESET_PM -> string.feature_settings_api_confirm_user_reset_pm
            SettingsRuleAction.IMPORT_MAT -> string.feature_settings_api_confirm_user_mat_import
        }
        stringResource(titleRes, user.text())
    }
    if (dialog.action == SettingsRuleAction.RESET_PM) {
        BlockerWarningAlertDialog(
            title = title,
            text = stringResource(string.feature_settings_api_reset_pm_dialog_message),
            onDismissRequest = onDismiss,
            onConfirmRequest = onConfirm,
        )
    } else {
        BlockerConfirmAlertDialog(
            text = title,
            onDismissRequest = onDismiss,
            onConfirmRequest = onConfirm,
        )
    }
}
