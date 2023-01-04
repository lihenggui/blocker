/*
 * Copyright 2022 Blocker
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
fun AppListSettings(
    showSystemApps: Boolean,
    showServiceInfo: Boolean,
    updateShowSystemApps: (Boolean) -> Unit,
    updateShowServiceInfo: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(vertical = 4.dp)
    ) {
        SettingItemHead(itemRes = string.application_list)
        SwitchSettingItem(
            itemRes = string.show_system_apps,
            checked = showSystemApps,
            onCheckedChange = updateShowSystemApps
        )
        SwitchSettingItem(
            itemRes = string.show_service_info,
            checked = showServiceInfo,
            onCheckedChange = updateShowServiceInfo
        )
    }
}

@Composable
@Preview
fun AppListSettingsSectionPreview() {
    BlockerTheme {
        AppListSettings(
            showSystemApps = false,
            showServiceInfo = true,
            updateShowSystemApps = {},
            updateShowServiceInfo = {}
        )
    }
}
