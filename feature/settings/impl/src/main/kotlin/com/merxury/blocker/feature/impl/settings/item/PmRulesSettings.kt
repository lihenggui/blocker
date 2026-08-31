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

package com.merxury.blocker.feature.impl.settings.item

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.BlockerSettingItem
import com.merxury.blocker.core.ui.ItemHeader
import com.merxury.blocker.feature.settings.api.R.string

@Composable
fun PmRulesSettings(
    resetPmRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .padding(vertical = 4.dp),
    ) {
        ItemHeader(
            title = stringResource(id = string.feature_settings_api_pm_rules),
            extraIconPadding = true,
        )
        BlockerSettingItem(
            title = stringResource(id = string.feature_settings_api_reset_pm),
            summary = stringResource(id = string.feature_settings_api_reset_pm_summary),
            onItemClick = { showConfirmDialog = true },
            extraIconPadding = true,
        )
    }
    if (showConfirmDialog) {
        BlockerWarningAlertDialog(
            title = stringResource(id = string.feature_settings_api_reset_pm_dialog_title),
            text = stringResource(id = string.feature_settings_api_reset_pm_dialog_message),
            onDismissRequest = { showConfirmDialog = false },
            onConfirmRequest = resetPmRules,
        )
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun PmRulesSettingsPreview() {
    BlockerTheme {
        Surface {
            PmRulesSettings(resetPmRules = {})
        }
    }
}
