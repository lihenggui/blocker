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

package com.merxury.blocker.core.ui.collapseList

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest.Builder
import coil3.request.error
import coil3.request.placeholder
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.R.string
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider

/**
 * The header of a group of components that can be expanded: its icon, title and counts, and a
 * menu to open what the group stands for and to block or enable all of its components.
 *
 * @param icon Coil data for the icon; the Android icon when null or when it cannot be loaded
 * @param matchedCount every component of the group, which may be more than the blocked and the
 * enabled ones together when the state of some is not known
 * @param batchActionsEnabled whether block all and enable all can be chosen, for example false
 * while another batch runs
 */
@Composable
fun CollapsibleHeader(
    title: String,
    icon: Any?,
    matchedCount: Int,
    blockedCount: Int,
    enabledCount: Int,
    modifier: Modifier = Modifier,
    @StringRes navigationMenuItemDesc: Int = string.core_ui_open_app_detail,
    navigation: () -> Unit = {},
    onBlockAllClick: () -> Unit = {},
    onEnableAllClick: () -> Unit = {},
    expanded: Boolean = false,
    onCardArrowClick: () -> Unit = {},
    batchActionsEnabled: Boolean = true,
) {
    val expandIcon = if (expanded) {
        BlockerIcons.ExpandLess
    } else {
        BlockerIcons.ExpandMore
    }
    val items = listOf(
        DropDownMenuItem(navigationMenuItemDesc, navigation),
        DropDownMenuItem(string.core_ui_block_all_components, onBlockAllClick, enabled = batchActionsEnabled),
        DropDownMenuItem(string.core_ui_enable_all_components, onEnableAllClick, enabled = batchActionsEnabled),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardArrowClick() }
            .padding(vertical = 8.dp),
    ) {
        IconButton(onClick = onCardArrowClick) {
            Icon(
                imageVector = expandIcon,
                contentDescription = if (expanded) {
                    stringResource(string.core_ui_collapse_list)
                } else {
                    stringResource(string.core_ui_expand_list)
                },
            )
        }
        AsyncImage(
            modifier = Modifier.size(48.dp),
            model = Builder(LocalContext.current)
                .data(icon)
                .error(BlockerIcons.Android)
                .placeholder(BlockerIcons.Android)
                .build(),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(16.dp))
        MatchedAppInfo(
            label = title,
            matchedCount = matchedCount,
            blockedCount = blockedCount,
            enabledCount = enabledCount,
            modifier = Modifier.fillMaxWidth(0.8f),
        )
        Spacer(modifier = Modifier.weight(1f))
        BlockerAppTopBarMenu(
            menuIcon = BlockerIcons.MoreVert,
            menuIconDesc = string.core_ui_more_menu,
            menuList = items,
        )
    }
}

@Composable
private fun MatchedAppInfo(
    label: String,
    matchedCount: Int,
    blockedCount: Int,
    enabledCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        BlockerBodyLargeText(
            text = label,
        )
        BlockerBodyMediumText(
            text = stringResource(
                id = string.core_ui_matched,
                matchedCount,
                blockedCount,
                enabledCount,
            ),
        )
    }
}

@Composable
@PreviewThemes
private fun CollapsibleHeaderPreview() {
    val components = ComponentListPreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            CollapsibleHeader(
                title = "Blocker",
                icon = null,
                matchedCount = components.size,
                blockedCount = components.count { !it.enabled() },
                enabledCount = components.count { it.enabled() },
            )
        }
    }
}

@Composable
@PreviewThemes
private fun CollapsibleHeaderLongNamePreview() {
    val components = ComponentListPreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            CollapsibleHeader(
                title = "Blocker Test test long long long long name",
                icon = null,
                matchedCount = components.size,
                blockedCount = components.count { !it.enabled() },
                enabledCount = components.count { it.enabled() },
                expanded = true,
            )
        }
    }
}
