/*
 * Copyright 2024 Blocker
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.ItemHeader
import com.merxury.blocker.feature.settings.R.string

@Composable
fun AppListSettings(
    showSystemApps: Boolean,
    showServiceInfo: Boolean,
    modifier: Modifier = Modifier,
    onChangeShowSystemApps: (Boolean) -> Unit = { },
    onChangeShowServiceInfo: (Boolean) -> Unit = { },
) {
    Column(
        modifier = modifier
            .padding(vertical = 4.dp),
    ) {
        ItemHeader(
            title = stringResource(id = string.feature_settings_application_list),
            extraIconPadding = true,
        )
        SwitchSettingItem(
            itemRes = string.feature_settings_show_system_apps,
            checked = showSystemApps,
            onCheckedChange = onChangeShowSystemApps,
        )
        SwitchSettingItem(
            itemRes = string.feature_settings_show_service_info,
            checked = showServiceInfo,
            onCheckedChange = onChangeShowServiceInfo,
        )
    }
}

@Composable
@Preview
private fun AppListSettingsSectionPreview() {
    BlockerTheme {
        AppListSettings(
            showSystemApps = false,
            showServiceInfo = true,
        )
    }
}
