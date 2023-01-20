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
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.DarkThemeConfig.DARK
import com.merxury.blocker.core.model.preference.DarkThemeConfig.FOLLOW_SYSTEM
import com.merxury.blocker.core.model.preference.DarkThemeConfig.LIGHT
import com.merxury.blocker.core.model.preference.ThemeBrand
import com.merxury.blocker.core.model.preference.ThemeBrand.ANDROID
import com.merxury.blocker.core.model.preference.ThemeBrand.DEFAULT
import com.merxury.blocker.feature.settings.R.string
import com.merxury.blocker.feature.settings.SettingsUiState.Success

@Composable
fun ThemeSettings(
    modifier: Modifier = Modifier,
    uiState: Success,
    updateThemeBrand: (ThemeBrand) -> Unit,
    updateDarkThemeConfig: (DarkThemeConfig) -> Unit,
) {
    Column(
        modifier = modifier
            .padding(vertical = 4.dp),
    ) {
        SettingItemHeader(itemRes = string.theme)
        DialogSettingsItems(
            itemRes = string.theme,
            itemValue = uiState.settings.themeBrand,
            menuList = listOf(ANDROID, DEFAULT),
            onMenuClick = updateThemeBrand,
        )
        DialogSettingsItems(
            itemRes = string.dark_mode,
            itemValue = uiState.settings.darkThemeConfig,
            menuList = listOf(FOLLOW_SYSTEM, LIGHT, DARK),
            onMenuClick = updateDarkThemeConfig,
        )
    }
}
