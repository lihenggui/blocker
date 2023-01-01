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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
            .padding(vertical = 4.dp)
    ) {
        SettingItemHead(itemRes = string.theme)
        ThemeSettingsItems(
            itemRes = string.theme,
            itemValue = uiState.settings.themeBrand,
            menuList = listOf(ANDROID, DEFAULT),
            onMenuClick = updateThemeBrand
        )
        ThemeSettingsItems(
            itemRes = string.dark_mode_preference,
            itemValue = uiState.settings.darkThemeConfig,
            menuList = listOf(FOLLOW_SYSTEM, LIGHT, DARK),
            onMenuClick = updateDarkThemeConfig
        )
    }
}

@Composable
fun <T> ThemeSettingsItems(
    itemRes: Int,
    itemValue: T,
    menuList: List<T>,
    onMenuClick: (item: T) -> Unit
) {
    var isShowDialog by remember { mutableStateOf(false) }
    Column {
        SettingItem(
            itemRes = itemRes,
            itemValue = itemValue.toString(),
            onClick = { isShowDialog = true }
        )
    }
    if (isShowDialog) {
        SettingDialog(
            titleRes = itemRes,
            items = menuList,
            value = itemValue,
            onMenuClick = onMenuClick
        ) {
            isShowDialog = false
        }
    }
}

@Composable
fun <T> SettingDialog(
    titleRes: Int,
    items: List<T>,
    value: T,
    onMenuClick: (item: T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Divider()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach {
                    SettingsDialogThemeChooserRow(
                        item = it,
                        selected = value == it,
                        onClick = onMenuClick
                    )
                }
            }
        },
        confirmButton = {
            Text(
                text = stringResource(string.ok),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable { onDismiss() }
            )
        }
    )
}

@Composable
fun <T> SettingsDialogThemeChooserRow(
    item: T,
    selected: Boolean,
    onClick: (item: T) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onClick(item) },
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(Modifier.width(8.dp))
        Text(item.toString())
    }
}
