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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.FrameMetricsAggregator.ANIMATION_DURATION
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.component.ThemePreviews
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.ComponentItem
import com.merxury.blocker.core.ui.R
import com.merxury.blocker.core.ui.R.plurals
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.core.ui.component.ComponentListItem

fun LazyListScope.matchedComponentItem(
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    ruleMatchedApp: RuleMatchedApp,
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
    navigateToAppDetail: (String) -> Unit = { _ -> },
    onBlockAllClick: (List<ComponentItem>) -> Unit = { _ -> },
    onEnableAllClick: (List<ComponentItem>) -> Unit = { _ -> },
    onSwitch: (String, String, Boolean) -> Unit = { _, _, _ -> },
    expanded: Boolean = false,
    onCardArrowClicked: (String) -> Unit = {},
) {
    item {
        MatchedAppItemHeader(
            modifier = modifier,
            iconModifier = iconModifier,
            ruleMatchedApp = ruleMatchedApp,
            navigateToAppDetail = navigateToAppDetail,
            onBlockAllClick = onBlockAllClick,
            onEnableAllClick = onEnableAllClick,
            expanded = expanded,
            onCardArrowClicked = onCardArrowClicked,
        )
    }
    if (ruleMatchedApp.componentList.isEmpty()) {
        return
    }
    itemsIndexed(ruleMatchedApp.componentList) { _, item ->
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(ANIMATION_DURATION)) +
                expandVertically(animationSpec = tween(ANIMATION_DURATION)),
            exit = fadeOut(animationSpec = tween(ANIMATION_DURATION)) +
                shrinkVertically(animationSpec = tween(ANIMATION_DURATION)),
        ) {
            ComponentListItem(
                item = item,
                enabled = item.enabled(),
                type = item.type,
                isServiceRunning = item.isRunning,
                onStopServiceClick = { onStopServiceClick(item.packageName, item.name) },
                onLaunchActivityClick = { onLaunchActivityClick(item.packageName, item.name) },
                onCopyNameClick = { onCopyNameClick(item.simpleName) },
                onCopyFullNameClick = { onCopyFullNameClick(item.name) },
                onSwitchClick = onSwitch,
            )
        }
    }
}

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
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -180f,
        animationSpec = tween(500), label = "iconRotation",
    )
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
                imageVector = BlockerIcons.ExpandMore,
                contentDescription = if (expanded) {
                    stringResource(R.string.core_ui_collapse_list)
                } else {
                    stringResource(R.string.core_ui_expand_list)
                },
                modifier = Modifier.rotate(rotation),
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
            modifier = modifier.weight(1f),
        )
        BlockerAppTopBarMenu(
            menuIcon = BlockerIcons.MoreVert,
            menuIconDesc = R.string.core_ui_more_menu,
            menuList = items,
        )
    }
    HorizontalDivider()
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
    val ruleMatchedApp = RuleMatchedApp(
        app = AppItem(
            packageName = "com.merxury.example",
            label = "Example label",
            packageInfo = null,
        ),
        componentList = listOf(
            ComponentItem(
                packageName = "com.merxury.example",
                name = "com.merxury.example.MainActivity",
                simpleName = "MainActivity",
                pmBlocked = true,
                type = ACTIVITY,
            ),
            ComponentItem(
                packageName = "com.merxury.example",
                name = "com.merxury.example.provider",
                simpleName = "example",
                type = PROVIDER,
                pmBlocked = false,
            ),
        ),
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
    val ruleMatchedApp = RuleMatchedApp(
        app = AppItem(
            packageName = "com.merxury.example",
            label = "Example long long long longlong name",
            packageInfo = null,
        ),
        componentList = listOf(
            ComponentItem(
                packageName = "com.merxury.example",
                name = "com.merxury.example.MainActivity",
                simpleName = "MainActivity",
                pmBlocked = true,
                type = ACTIVITY,
            ),
            ComponentItem(
                packageName = "com.merxury.example",
                name = "com.merxury.example.provider",
                simpleName = "example",
                type = PROVIDER,
                pmBlocked = false,
            ),
        ),
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
