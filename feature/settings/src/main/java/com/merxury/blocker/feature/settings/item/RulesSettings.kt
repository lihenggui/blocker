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
        SettingItemHeader(itemRes = string.blocker_rules)
        SingleRowSettingItem(itemRes = string.export_rules, onItemClick = exportRules)
        SingleRowSettingItem(itemRes = string.import_rules, onItemClick = importRules)
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
        SettingItemHeader(itemRes = string.ifw_rules)
        SingleRowSettingItem(itemRes = string.export_ifw_rules, onItemClick = exportIfwRules)
        SingleRowSettingItem(itemRes = string.import_ifw_rules, onItemClick = importIfwRules)
        SingleRowSettingItem(itemRes = string.reset_ifw, onItemClick = resetIfwRules)
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun BackupSectionPreview() {
    BlockerTheme {
        Surface {
            BlockerRulesSettings(exportRules = {}, importRules = {})
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun IfwSectionPreview() {
    BlockerTheme {
        Surface {
            IfwRulesSettings(
                exportIfwRules = {},
                importIfwRules = {},
                resetIfwRules = {}
            )
        }
    }
}
