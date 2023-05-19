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
import com.merxury.blocker.core.designsystem.component.ItemHeader
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.DarkThemeConfig.DARK
import com.merxury.blocker.core.model.preference.DarkThemeConfig.FOLLOW_SYSTEM
import com.merxury.blocker.core.model.preference.DarkThemeConfig.LIGHT
import com.merxury.blocker.feature.settings.R.string
import com.merxury.blocker.feature.settings.UserEditableSettings

@Composable
fun ThemeSettings(
    modifier: Modifier = Modifier,
    settings: UserEditableSettings,
    supportDynamicColor: Boolean,
    onChangeDynamicColorPreference: (useDynamicColor: Boolean) -> Unit,
    onChangeDarkThemeConfig: (DarkThemeConfig) -> Unit,
) {
    Column(
        modifier = modifier
            .padding(vertical = 4.dp),
    ) {
        ItemHeader(
            title = stringResource(id = string.theme),
            extraIconPadding = true,
        )
        if (supportDynamicColor) {
            SwitchSettingItem(
                itemRes = string.dynamic_color,
                checked = settings.useDynamicColor,
                onCheckedChange = onChangeDynamicColorPreference,
            )
        }
        DialogSettingsItems(
            titleRes = string.dark_mode,
            selectedItem = settings.darkThemeConfig,
            itemList = listOf(
                FOLLOW_SYSTEM to string.system_default,
                LIGHT to string.light,
                DARK to string.dark,
            ),
            onValueChange = onChangeDarkThemeConfig,
        )
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun ThemeSettingsSettingsPreview() {
    BlockerTheme {
        Surface {
            ThemeSettings(
                settings = UserEditableSettings(
                    darkThemeConfig = DARK,
                    useDynamicColor = true,
                ),
                supportDynamicColor = true,
                onChangeDynamicColorPreference = {},
                onChangeDarkThemeConfig = {},
            )
        }
    }
}
