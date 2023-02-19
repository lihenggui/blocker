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

package com.merxury.blocker.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.ui.R.string
import com.merxury.blocker.core.ui.applist.AppIcon

@Composable
fun MatchedComponentItem(
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    matchedApp: MatchedApp,
    expanded: Boolean,
    onClickExpandIcon: (Boolean) -> Unit,
    onStopServiceClick: (String, String) -> Unit,
    onLaunchActivityClick: (String, String) -> Unit,
    onCopyNameClick: (String) -> Unit,
    onCopyFullNameClick: (String) -> Unit,
    onSwitch: (String, String, Boolean) -> Unit,
) {
    val expandIcon = if (expanded) {
        BlockerIcons.ExpandLess
    } else {
        BlockerIcons.ExpandMore
    }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .clickable { }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AppIcon(matchedApp.app.packageInfo, iconModifier.size(48.dp))
            Spacer(modifier = Modifier.width(16.dp))
            MatchedAppInfo(
                label = matchedApp.app.label,
                matchedComponentCount = matchedApp.component.value.size,
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { onClickExpandIcon(!expanded) }) {
                Icon(
                    imageVector = expandIcon,
                    contentDescription = null,
                )
            }
        }
        if (expanded) {
            ComponentList(
                components = matchedApp.component,
                onStopServiceClick = onStopServiceClick,
                onLaunchActivityClick = onLaunchActivityClick,
                onCopyNameClick = onCopyNameClick,
                onCopyFullNameClick = onCopyFullNameClick,
                onSwitchClick = onSwitch,
            )
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(id = string.matched_rules, matchedComponentCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@Preview
fun MatchedComponentItemWithExpandPreview() {
    val componentInfo = ComponentItem(
        name = "component",
        simpleName = "com",
        packageName = "blocker",
        type = ACTIVITY,
        pmBlocked = false,
    )
//    val matchedApp = MatchedApp(
//        app = AppItem(
//            packageName = "com.merxury.blocker",
//            label = "Blocker",
//            isSystem = false,
//        ),
//        componentInfo =
//    )
    BlockerTheme {
        Surface {
//            MatchedComponentItem(
//                matchedApp = matchedApp,
//                expanded = true,
//                onClickExpandIcon = {},
//                onCopyFullNameClick = {},
//                onCopyNameClick = {},
//                onLaunchActivityClick = { _, _ -> },
//                onStopServiceClick = { _, _ -> },
//                onSwitch = { _, _, _ -> },
//            )
        }
    }
}

@Composable
@Preview
fun MatchedComponentItemPreview() {
    val componentInfo = ComponentInfo(
        name = "component",
        simpleName = "com",
        packageName = "blocker",
        type = ACTIVITY,
        exported = false,
        pmBlocked = false,
    )
//    val matchedApp = MatchedApp(
//        app = AppItem(
//            packageName = "com.merxury.blocker",
//            label = "Blocker",
//            isSystem = false,
//        ),
//        component =,
//    )
    BlockerTheme {
        Surface {
//            MatchedComponentItem(
//                matchedApp = matchedApp,
//                expanded = false,
//                onClickExpandIcon = {},
//                onCopyFullNameClick = {},
//                onCopyNameClick = {},
//                onLaunchActivityClick = { _, _ -> },
//                onStopServiceClick = { _, _ -> },
//                onSwitch = { _, _, _ -> },
//            )
        }
    }
}
