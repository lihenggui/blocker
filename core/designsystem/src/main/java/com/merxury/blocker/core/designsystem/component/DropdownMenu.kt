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

package com.merxury.blocker.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons

@Composable
fun BlockerAppTopBarMenu(
    menuIcon: ImageVector,
    menuIconDesc: Int,
    menuList: List<DropDownMenuItem>,
) {
    val expanded = remember { mutableStateOf(false) }
    Box(Modifier.wrapContentSize(Alignment.TopStart)) {
        IconButton(
            onClick = {
                expanded.value = true
            },
        ) {
            BlockerActionIcon(
                imageVector = menuIcon,
                contentDescription = stringResource(id = menuIconDesc),
            )
        }
        BlockerDropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
            menuList = menuList,
        )
    }
}

@Composable
fun BlockerDropdownMenu(
    expanded: Boolean,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    onDismissRequest: () -> Unit,
    menuList: List<DropDownMenuItem>,
    dismissOnItemClick: Boolean = true,
) {
    DropdownMenu(
        expanded = expanded,
        offset = offset,
        onDismissRequest = onDismissRequest,
    ) {
        menuList.forEach { item ->
            DropdownMenuItem(
                text = { Text(stringResource(id = item.textRes)) },
                onClick = {
                    item.onClick()
                    if (dismissOnItemClick) onDismissRequest()
                },
            )
        }
    }
}

/**
 * Blocker dropdown menu button with included trailing icon as well as text label and item
 * content slots.
 *
 * @param items The list of items to display in the menu.
 * @param onItemClick Called when the user clicks on a menu item.
 * @param modifier Modifier to be applied to the button.
 * @param enabled Controls the enabled state of the button. When `false`, this button will not be
 * clickable and will appear disabled to accessibility services.
 * @param dismissOnItemClick Whether the menu should be dismissed when an item is clicked.
 * @param itemText The text label content for a given item.
 * @param itemLeadingIcon The leading icon content for a given item.
 * @param itemTrailingIcon The trailing icon content for a given item.
 */
@Composable
fun <T> BlockerDropdownMenuButton(
    items: List<T>,
    onItemClick: (item: T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dismissOnItemClick: Boolean = true,
    text: @Composable () -> Unit,
    itemText: @Composable (item: T) -> Unit,
    itemLeadingIcon: @Composable ((item: T) -> Unit)? = null,
    itemTrailingIcon: @Composable ((item: T) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
            border = BorderStroke(
                width = BlockerDropdownMenuDefaults.DropdownMenuButtonBorderWidth,
                color = if (enabled) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = BlockerDropdownMenuDefaults.DisabledDropdownMenuButtonBorderAlpha,
                    )
                },
            ),
            contentPadding = BlockerDropdownMenuDefaults.DropdownMenuButtonContentPadding,
        ) {
            BlockerDropdownMenuButtonContent(
                text = text,
                trailingIcon = {
                    Icon(
                        imageVector = if (expanded) BlockerIcons.ArrowDropUp else BlockerIcons.ArrowDropDown,
                        contentDescription = null,
                    )
                },
            )
        }
        BlockerDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = items,
            onItemClick = onItemClick,
            dismissOnItemClick = dismissOnItemClick,
            itemText = itemText,
            itemLeadingIcon = itemLeadingIcon,
            itemTrailingIcon = itemTrailingIcon,
        )
    }
}

/**
 * Internal Blocker dropdown menu button content layout for arranging the text label and
 * trailing icon.
 *
 * @param text The button text label content.
 * @param trailingIcon The button trailing icon content. Pass `null` here for no trailing icon.
 */
@Composable
private fun BlockerDropdownMenuButtonContent(
    text: @Composable () -> Unit,
    trailingIcon: @Composable (() -> Unit)?,
) {
    Box(
        Modifier
            .padding(
                end = if (trailingIcon != null) {
                    ButtonDefaults.IconSpacing
                } else {
                    0.dp
                },
            ),
    ) {
        ProvideTextStyle(value = MaterialTheme.typography.labelSmall) {
            text()
        }
    }
    if (trailingIcon != null) {
        Box(Modifier.sizeIn(maxHeight = ButtonDefaults.IconSize)) {
            trailingIcon()
        }
    }
}

/**
 * Blocker dropdown menu with item content slots. Wraps Material 3 [DropdownMenu] and
 * [DropdownMenuItem].
 *
 * @param expanded Whether the menu is currently open and visible to the user.
 * @param onDismissRequest Called when the user requests to dismiss the menu, such as by
 * tapping outside the menu's bounds.
 * @param items The list of items to display in the menu.
 * @param onItemClick Called when the user clicks on a menu item.
 * @param dismissOnItemClick Whether the menu should be dismissed when an item is clicked.
 * @param itemText The text label content for a given item.
 * @param itemLeadingIcon The leading icon content for a given item.
 * @param itemTrailingIcon The trailing icon content for a given item.
 */

@Composable
fun <T> BlockerDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<T>,
    onItemClick: (item: T) -> Unit,
    dismissOnItemClick: Boolean = true,
    itemText: @Composable (item: T) -> Unit,
    itemLeadingIcon: @Composable ((item: T) -> Unit)? = null,
    itemTrailingIcon: @Composable ((item: T) -> Unit)? = null,
) {
    DropdownMenu(
        offset = DpOffset(x = 56.dp, y = (-16).dp),
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { itemText(item) },
                    onClick = {
                        onItemClick(item)
                        if (dismissOnItemClick) onDismissRequest()
                    },
                    leadingIcon = if (itemLeadingIcon != null) {
                        { itemLeadingIcon(item) }
                    } else {
                        null
                    },
                    trailingIcon = if (itemTrailingIcon != null) {
                        { itemTrailingIcon(item) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

data class DropDownMenuItem(
    val textRes: Int,
    val onClick: () -> Unit,
)

/**
 * Blocker dropdown menu default values.
 */
object BlockerDropdownMenuDefaults {
    // TODO: File bug
    // OutlinedButton border color doesn't respect disabled state by default
    const val DisabledDropdownMenuButtonBorderAlpha = 0.12f

    // TODO: File bug
    // OutlinedButton default border width isn't exposed via ButtonDefaults
    val DropdownMenuButtonBorderWidth = 1.dp

    // TODO: File bug
    // Various default button padding values aren't exposed via ButtonDefaults
    val DropdownMenuButtonContentPadding =
        PaddingValues(
            start = 24.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = 8.dp,
        )
}
