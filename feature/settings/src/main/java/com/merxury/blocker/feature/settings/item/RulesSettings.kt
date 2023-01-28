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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerItem
import com.merxury.blocker.core.designsystem.component.ItemHeader
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.settings.R.string

@Composable
fun BlockerRulesSettings(
    exportRules: () -> Unit,
    importRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(vertical = 4.dp),
    ) {
        ItemHeader(
            itemRes = string.blocker_rules,
            paddingValues = PaddingValues(vertical = 16.dp, horizontal = 56.dp),
        )
        BlockerItem(
            titleRes = string.export_rules,
            onItemClick = exportRules,
            paddingValues = PaddingValues(16.dp),
            spacePadding = 40.dp,
        )
        BlockerItem(
            titleRes = string.import_rules,
            onItemClick = importRules,
            paddingValues = PaddingValues(16.dp),
            spacePadding = 40.dp,
        )
    }
}

@Composable
fun IfwRulesSettings(
    exportIfwRules: () -> Unit,
    importIfwRules: () -> Unit,
    resetIfwRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(vertical = 4.dp),
    ) {
        ItemHeader(
            itemRes = string.ifw_rules,
            paddingValues = PaddingValues(vertical = 16.dp, horizontal = 56.dp),
        )
        BlockerItem(
            titleRes = string.export_ifw_rules,
            onItemClick = exportIfwRules,
            paddingValues = PaddingValues(16.dp),
            spacePadding = 40.dp,
        )
        BlockerItem(
            titleRes = string.import_ifw_rules,
            onItemClick = importIfwRules,
            paddingValues = PaddingValues(16.dp),
            spacePadding = 40.dp,
        )
        BlockerItem(
            titleRes = string.reset_ifw,
            onItemClick = resetIfwRules,
            paddingValues = PaddingValues(16.dp),
            spacePadding = 40.dp,
        )
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
                resetIfwRules = {},
            )
        }
    }
}
