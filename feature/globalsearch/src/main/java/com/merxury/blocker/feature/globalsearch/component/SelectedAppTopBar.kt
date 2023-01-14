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

package com.merxury.blocker.feature.globalsearch.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.globalsearch.R.string

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectedAppTopBar(
    selectedAppCount: Int,
    onNavigationClick: () -> Unit,
    onSelectAll: () -> Unit
) {
    BlockerTopAppBar(
        title = stringResource(id = string.selected_app, selectedAppCount),
        navigationIcon = BlockerIcons.Clear,
        navigationIconContentDescription = null,
        onNavigationClick = onNavigationClick,
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = BlockerIcons.SelectAll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    )
}

@Composable
@Preview
fun SelectedAppTopBarPreview() {
    BlockerTheme {
        Surface {
            SelectedAppTopBar(
                selectedAppCount = 1,
                onNavigationClick = {},
                onSelectAll = {}
            )
        }
    }
}
