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

package com.merxury.blocker.core.ui.rule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.component.ThemePreviews
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.ComponentItem
import com.merxury.blocker.core.ui.R
import com.merxury.blocker.core.ui.R.plurals
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.core.ui.previewparameter.AppListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider

@Composable
fun MatchedAppItemHeader(
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    ruleMatchedApp: RuleMatchedApp,
    navigateToAppDetail: (String) -> Unit = { _ -> },
    onBlockAllClick: (List<ComponentItem>) -> Unit = { _ -> },
    onEnableAllClick: (List<ComponentItem>) -> Unit = { _ -> },
    expanded: Boolean = false,
    onCardArrowClicked: (String) -> Unit = {},
) {
    val expandIcon = if (expanded) {
        BlockerIcons.ExpandLess
    } else {
        BlockerIcons.ExpandMore
    }
    val items = listOf(
        DropDownMenuItem(
            R.string.core_ui_open_app_detail,
        ) {
            navigateToAppDetail(ruleMatchedApp.app.packageName)
        },
        DropDownMenuItem(
            R.string.core_ui_block_all_components,
        ) {
            onBlockAllClick(ruleMatchedApp.componentList)
        },
        DropDownMenuItem(
            R.string.core_ui_enable_all_components,
        ) {
            onEnableAllClick(ruleMatchedApp.componentList)
        },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardArrowClicked(ruleMatchedApp.app.packageName) }
            .padding(vertical = 8.dp),
    ) {
        IconButton(onClick = { onCardArrowClicked(ruleMatchedApp.app.packageName) }) {
            Icon(
                imageVector = expandIcon,
                contentDescription = if (expanded) {
                    stringResource(R.string.core_ui_collapse_list)
                } else {
                    stringResource(R.string.core_ui_expand_list)
                },
            )
        }
        AppIcon(
            ruleMatchedApp.app.packageInfo,
            iconModifier
                .size(48.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        MatchedAppInfo(
            label = ruleMatchedApp.app.label,
            matchedComponentCount = ruleMatchedApp.componentList.size,
            modifier = modifier.fillMaxWidth(0.8f),
        )
        Spacer(modifier = Modifier.weight(1f))
        BlockerAppTopBarMenu(
            menuIcon = BlockerIcons.MoreVert,
            menuIconDesc = R.string.core_ui_more_menu,
            menuList = items,
        )
    }
    Divider(modifier = modifier)
}

@Composable
private fun MatchedAppInfo(
    modifier: Modifier = Modifier,
    label: String,
    matchedComponentCount: Int,
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
@ThemePreviews
fun MatchedAppItemHeaderPreview() {
    val appList = AppListPreviewParameterProvider().values.first()
    val components = ComponentListPreviewParameterProvider().values
        .first()
        .toMutableStateList()
    val ruleMatchedApp = RuleMatchedApp(
        app = appList[0],
        componentList = components,
    )
    BlockerTheme {
        Surface {
            MatchedAppItemHeader(
                ruleMatchedApp = ruleMatchedApp,
            )
        }
    }
}

@Composable
@ThemePreviews
fun MatchedAppItemHeaderLongNamePreview() {
    val appList = AppListPreviewParameterProvider().values.first()
    val components = ComponentListPreviewParameterProvider().values
        .first()
        .toMutableStateList()
    val ruleMatchedApp = RuleMatchedApp(
        app = appList[2],
        componentList = components,
    )
    BlockerTheme {
        Surface {
            MatchedAppItemHeader(
                ruleMatchedApp = ruleMatchedApp,
                expanded = true,
            )
        }
    }
}
