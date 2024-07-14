/*
 * Copyright 2024 Blocker
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.domain.model.MatchedHeaderData
import com.merxury.blocker.core.domain.model.MatchedItem
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.ui.R.plurals
import com.merxury.blocker.core.ui.R.string
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider

@Composable
fun CollapsibleItem(
    matchedItem: MatchedItem,
    modifier: Modifier = Modifier,
    @StringRes navigationMenuItemDesc: Int = string.core_ui_open_app_detail,
    navigation: () -> Unit = {},
    onBlockAllInItemClick: (List<ComponentInfo>) -> Unit = { _ -> },
    onEnableAllInItemClick: (List<ComponentInfo>) -> Unit = { _ -> },
    expanded: Boolean = false,
    onCardArrowClick: (String) -> Unit = {},
) {
    val expandIcon = if (expanded) {
        BlockerIcons.ExpandLess
    } else {
        BlockerIcons.ExpandMore
    }
    val items = listOf(
        DropDownMenuItem(
            navigationMenuItemDesc,
        ) {
            navigation()
        },
        DropDownMenuItem(
            string.core_ui_block_all_components,
        ) {
            onBlockAllInItemClick(matchedItem.componentList)
        },
        DropDownMenuItem(
            string.core_ui_enable_all_components,
        ) {
            onEnableAllInItemClick(matchedItem.componentList)
        },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardArrowClick(matchedItem.header.uniqueId) }
            .padding(vertical = 8.dp),
    ) {
        IconButton(onClick = { onCardArrowClick(matchedItem.header.uniqueId) }) {
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
                .data(matchedItem.header.icon)
                .error(BlockerIcons.Android)
                .placeholder(BlockerIcons.Android)
                .build(),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(16.dp))
        MatchedAppInfo(
            label = matchedItem.header.title,
            matchedComponentCount = matchedItem.componentList.size,
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
    matchedComponentCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        BlockerBodyLargeText(
            text = label,
        )
        BlockerBodyMediumText(
            text = pluralStringResource(
                id = plurals.core_ui_matched_component,
                matchedComponentCount,
                matchedComponentCount,
            ),
        )
    }
}

@Composable
@PreviewThemes
private fun CollapseAppItemPreview() {
    val components = ComponentListPreviewParameterProvider().values
        .first()
    val matchedItem = MatchedItem(
        header = MatchedHeaderData(
            title = "Blocker",
            uniqueId = "com.merxury.blocker",
        ),
        componentList = components,
    )
    BlockerTheme {
        Surface {
            CollapsibleItem(
                matchedItem = matchedItem,
            )
        }
    }
}

@Composable
@PreviewThemes
private fun CollapseRuleItemPreview() {
    val components = ComponentListPreviewParameterProvider().values
        .first()
    val matchedItem = MatchedItem(
        header = MatchedHeaderData(
            title = "Blocker",
            uniqueId = "com.merxury.blocker",
            icon = null,
        ),
        componentList = components,
    )
    BlockerTheme {
        Surface {
            CollapsibleItem(
                matchedItem = matchedItem,
            )
        }
    }
}

@Composable
@PreviewThemes
private fun CollapseItemLongNamePreview() {
    val components = ComponentListPreviewParameterProvider().values
        .first()
    val matchedItem = MatchedItem(
        header = MatchedHeaderData(
            title = "Blocker Test test long long long long name",
            uniqueId = "com.merxury.blocker",
        ),
        componentList = components,
    )
    BlockerTheme {
        Surface {
            CollapsibleItem(
                matchedItem = matchedItem,
                expanded = true,
            )
        }
    }
}
