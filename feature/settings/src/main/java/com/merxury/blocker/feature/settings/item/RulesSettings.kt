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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.settings.R.string

@Composable
fun BlockerRulesSettings(
    exportRules: () -> Unit,
    importRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(vertical = 4.dp)
    ) {
        SettingItemHead(itemRes = string.blocker_rules)
        SettingsItem(itemRes = string.export_rules, onItemClick = exportRules)
        SettingsItem(itemRes = string.import_rules, onItemClick = importRules)
    }
}

@Composable
fun IfwRulesSettings(
    exportIfwRules: () -> Unit,
    importIfwRules: () -> Unit,
    resetIfwRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(vertical = 4.dp)
    ) {
        SettingItemHead(itemRes = string.ifw_rules)
        SettingsItem(itemRes = string.export_ifw_rules, onItemClick = exportIfwRules)
        SettingsItem(itemRes = string.import_ifw_rules, onItemClick = importIfwRules)
        SettingsItem(itemRes = string.reset_ifw, onItemClick = resetIfwRules)
    }
}

@Composable
@Preview
fun BackupSectionPreview() {
    BlockerTheme {
        BlockerRulesSettings(exportRules = {}, importRules = {})
    }
}

@Composable
@Preview
fun IfwSectionPreview() {
    BlockerTheme {
        IfwRulesSettings(
            exportIfwRules = {},
            importIfwRules = {},
            resetIfwRules = {}
        )
    }
}
