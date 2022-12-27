/*
 * Copyright 2022 Blocker
 * Copyright 2022 The Android Open Source Project
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

package com.merxury.blocker.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource

@Composable
fun BlockerAppTopBarMenu(
    menuIcon: ImageVector,
    menuIconDesc: Int,
    items: List<DropDownMenuItem>
) {
    val expanded = remember { mutableStateOf(false) }
    Box(Modifier.wrapContentSize(Alignment.TopStart)) {
        IconButton(onClick = {
            expanded.value = true
        }) {
            Icon(
                imageVector = menuIcon,
                contentDescription = stringResource(id = menuIconDesc)
            )
        }
        BlockerDropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
            items = items
        )
    }
}

@Composable
fun BlockerDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<DropDownMenuItem>,
    dismissOnItemClick: Boolean = true
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        items.forEach { item ->
            DropdownMenuItem(
                text = { stringResource(id = item.textRes) },
                onClick = {
                    item.onClick
                    if (dismissOnItemClick) onDismissRequest()
                }
            )
        }
    }
}

data class DropDownMenuItem(
    val textRes: Int,
    val onClick: () -> Unit
)
