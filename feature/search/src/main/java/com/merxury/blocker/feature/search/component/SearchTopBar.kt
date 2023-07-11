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

package com.merxury.blocker.feature.search.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.merxury.blocker.core.designsystem.component.BlockerConfirmAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerMediumTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerSearchTextField
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.search.R.plurals
import com.merxury.blocker.feature.search.R.string
import com.merxury.blocker.feature.search.model.SearchBoxUiState

@Composable
fun SelectedAppTopBar(
    selectedAppCount: Int,
    selectedComponentCount: Int,
    onNavigationClick: () -> Unit,
    onSelectAll: () -> Unit,
    onBlockAll: () -> Unit,
    onEnableAll: () -> Unit,
) {
    var showBlockAllDialog by remember {
        mutableStateOf(false)
    }
    var showEnableAllDialog by remember {
        mutableStateOf(false)
    }
    BlockerMediumTopAppBar(
        title = pluralStringResource(
            id = plurals.selected_app_count,
            count = selectedAppCount,
            selectedAppCount,
        ),
        navigation = {
            IconButton(onClick = onNavigationClick) {
                BlockerActionIcon(
                    imageVector = BlockerIcons.Close,
                    contentDescription = null,
                )
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                BlockerActionIcon(
                    imageVector = BlockerIcons.SelectAll,
                    contentDescription = null,
                )
            }
            IconButton(onClick = { showBlockAllDialog = true }) {
                BlockerActionIcon(
                    imageVector = BlockerIcons.Block,
                    contentDescription = null,
                )
            }
            IconButton(onClick = { showEnableAllDialog = true }) {
                BlockerActionIcon(
                    imageVector = BlockerIcons.CheckCircle,
                    contentDescription = null,
                )

            }
        },
    )
    if (showBlockAllDialog) {
        BlockerConfirmAlertDialog(
            text = pluralStringResource(
                id = plurals.block_all,
                count = selectedComponentCount,
                selectedComponentCount,
            ),
            onDismissRequest = { showBlockAllDialog = false },
            onConfirmRequest = { onBlockAll() },
        )
    }
    if (showEnableAllDialog) {
        BlockerConfirmAlertDialog(
            text = pluralStringResource(
                id = plurals.enable_all,
                count = selectedComponentCount,
                selectedComponentCount,
            ),
            onDismissRequest = { showEnableAllDialog = false },
            onConfirmRequest = { onEnableAll() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    uiState: SearchBoxUiState,
    onSearchTextChanged: (TextFieldValue) -> Unit,
    onClearClick: () -> Unit,
) {
    val colors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
    BlockerTopAppBar(
        title = stringResource(id = string.searching),
        actions = {
            BlockerSearchTextField(
                keyword = uiState.keyword,
                placeholder = {
                    Text(
                        text = stringResource(id = string.search_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                onValueChange = onSearchTextChanged,
                onClearClick = onClearClick,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = modifier.fillMaxWidth(),
                colors = colors,
            )
        },
    )
}

@Composable
@Preview
fun SelectedAppTopBarPreview() {
    BlockerTheme {
        Surface {
            SelectedAppTopBar(
                selectedAppCount = 3,
                selectedComponentCount = 6,
                onNavigationClick = {},
                onSelectAll = {},
                onBlockAll = {},
                onEnableAll = {},
            )
        }
    }
}
