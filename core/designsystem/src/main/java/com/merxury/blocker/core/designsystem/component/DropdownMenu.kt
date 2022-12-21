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

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Blocker dropdown menu with item content slots. Wraps Material 3 [DropdownMenu] and
 * [DropdownMenuItem].
 *
 * @param expanded Whether the menu is currently open and visible to the user.
 * @param onDismissRequest Called when the user requests to dismiss the menu, such as by
 * tapping outside the menu's bounds.
 * @param items The list of items to display in the menu.
 * @param dismissOnItemClick Whether the menu should be dismissed when an item is clicked.
 */
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
                text = { item.text },
                onClick = {
                    item.onClick
                    if (dismissOnItemClick) onDismissRequest()
                },
                trailingIcon = if (item.trailingIcon != null) {
                    { item.trailingIcon }
                } else {
                    null
                }
            )
        }
    }
}

data class DropDownMenuItem(
    val text: String,
    val trailingIcon: ImageVector? = null,
    val onClick: () -> Unit
)
