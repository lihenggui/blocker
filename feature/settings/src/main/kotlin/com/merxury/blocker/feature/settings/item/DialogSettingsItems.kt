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

package com.merxury.blocker.feature.settings.item

import android.R.string
import android.content.res.Configuration
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.designsystem.icon.Icon
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.BlockerSettingItem
import com.merxury.blocker.feature.settings.R

@Composable
fun <T> DialogSettingsItems(
    titleRes: Int,
    selectedItem: T,
    itemList: List<Pair<T, Int>>,
    onValueChange: (item: T) -> Unit,
    modifier: Modifier = Modifier,
    icon: Icon? = null,
) {
    var isShowDialog by remember { mutableStateOf(false) }
    val itemWithSummary = itemList.find { it.first == selectedItem }
        ?: throw RuntimeException("Can't find selectedValue in the list")
    Column(modifier = modifier) {
        BlockerSettingItem(
            icon = icon,
            title = stringResource(id = titleRes),
            summary = stringResource(id = itemWithSummary.second),
            onItemClick = { isShowDialog = true },
            extraIconPadding = true,
        )
    }
    if (isShowDialog) {
        SettingDialog(
            titleRes = titleRes,
            items = itemList,
            selectedValue = selectedItem,
            onValueChange = onValueChange,
        ) {
            isShowDialog = false
        }
    }
}

@Composable
fun <T> SettingDialog(
    titleRes: Int,
    items: List<Pair<T, Int>>,
    selectedValue: T,
    onValueChange: (item: T) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = {
            onDismiss()
        },
        title = {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            HorizontalDivider()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { item ->
                    val value = item.first
                    SettingsDialogChooserRow(
                        item = item,
                        selected = selectedValue == value,
                        onClick = { onValueChange(value) },
                    )
                }
            }
        },
        confirmButton = {
            BlockerTextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(
                    text = stringResource(string.ok),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
fun <T> SettingsDialogChooserRow(
    item: Pair<T, Int>,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (item: Pair<T, Int>) -> Unit = { _ -> },
) {
    Row(
        modifier.fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onClick(item) },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(id = item.second))
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun DialogSettingsItemPreview() {
    BlockerTheme {
        Surface {
            SettingDialog(
                titleRes = R.string.feature_settings_theme,
                items = listOf(
                    "Android" to R.string.feature_settings_android,
                    "Default" to R.string.feature_settings_theme_default,
                ),
                selectedValue = "Default" to R.string.feature_settings_theme_default,
                onValueChange = {},
            ) {}
        }
    }
}
