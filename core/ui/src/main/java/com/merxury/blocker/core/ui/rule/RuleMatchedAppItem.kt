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

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.ComponentItem
import com.merxury.blocker.core.ui.R
import com.merxury.blocker.core.ui.R.plurals
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.core.ui.component.ComponentListItem

@Composable
fun MatchedComponentItem(
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
) {
    var expanded by remember {
        mutableStateOf(false)
    }
    val expandIcon = if (expanded) {
        BlockerIcons.ExpandLess
    } else {
        BlockerIcons.ExpandMore
    }
    Column(modifier = modifier.animateContentSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
        ) {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = expandIcon,
                    contentDescription = if (expanded) {
                        stringResource(R.string.core_ui_expand_less)
                    } else {
                        stringResource(R.string.core_ui_expand_more)
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
                modifier = modifier.weight(1f),
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
            BlockerAppTopBarMenu(
                menuIcon = BlockerIcons.MoreVert,
                menuIconDesc = R.string.core_ui_more_menu,
                menuList = items,
            )
        }
        HorizontalDivider()
        if (expanded) {
            Column {
                ruleMatchedApp.componentList.forEach {
                    ComponentListItem(
                        simpleName = it.simpleName,
                        name = it.name,
                        packageName = it.packageName,
                        enabled = it.enabled(),
                        type = it.type,
                        isServiceRunning = it.isRunning,
                        onStopServiceClick = { onStopServiceClick(it.packageName, it.name) },
                        onLaunchActivityClick = { onLaunchActivityClick(it.packageName, it.name) },
                        onCopyNameClick = { onCopyNameClick(it.simpleName) },
                        onCopyFullNameClick = { onCopyFullNameClick(it.name) },
                        onSwitchClick = onSwitch,
                    )
                }
            }
        }
    }
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
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun MatchedComponentItemWithExpandPreview() {
    val componentInfo1 = ComponentItem(
        name = "component name component name component name component name ",
        simpleName = "simple name simple name",
        packageName = "blocker",
        type = ACTIVITY,
        pmBlocked = false,
    )
    val componentInfo2 = ComponentItem(
        name = "component2 name component2 name component2 name component2 name ",
        simpleName = "simple2 name simple2 name",
        packageName = "blocker2",
        type = ACTIVITY,
        pmBlocked = false,
    )
    val ruleMatchedApp = RuleMatchedApp(
        app = AppItem(
            packageName = "com.merxury.blocker",
            label = "Blocker",
            isSystem = false,
        ),
        componentList = listOf(componentInfo1, componentInfo2),
    )
    BlockerTheme {
        Surface {
            MatchedComponentItem(
                ruleMatchedApp = ruleMatchedApp,
                onCopyFullNameClick = {},
                onCopyNameClick = {},
                onLaunchActivityClick = { _, _ -> },
                onStopServiceClick = { _, _ -> },
                onSwitch = { _, _, _ -> },
            )
        }
    }
}

@Composable
@Preview
fun MatchedComponentItemPreview() {
    val componentInfo = ComponentItem(
        name = "component",
        simpleName = "com",
        packageName = "blocker",
        type = ACTIVITY,
        pmBlocked = false,
    )
    val ruleMatchedApp = RuleMatchedApp(
        app = AppItem(
            packageName = "com.merxury.blocker",
            label = "Blocker component name test long name",
            isSystem = false,
        ),
        componentList = listOf(componentInfo),
    )
    BlockerTheme {
        Surface {
            MatchedComponentItem(
                ruleMatchedApp = ruleMatchedApp,
            )
        }
    }
}
