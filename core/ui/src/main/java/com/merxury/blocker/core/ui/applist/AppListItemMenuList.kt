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

package com.merxury.blocker.core.ui.applist

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerDropdownMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.R

@Composable
fun AppListItemMenuList(
    expanded: Boolean,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    isAppRunning: Boolean,
    isAppEnabled: Boolean,
    onClearCacheClick: () -> Unit,
    onClearDataClick: () -> Unit,
    onForceStopClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onEnableClick: () -> Unit,
    onDisableClick: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val items = buildList {
        if (isAppRunning) {
            add(
                DropDownMenuItem(
                    textRes = R.string.force_stop,
                    onClick = onForceStopClick,
                ),
            )
        }
        if (isAppEnabled) {
            add(
                DropDownMenuItem(
                    textRes = R.string.disable,
                    onClick = onDisableClick,
                ),
            )
        } else {
            add(
                DropDownMenuItem(
                    textRes = R.string.enable,
                    onClick = onEnableClick,
                ),
            )
        }
        add(
            DropDownMenuItem(
                textRes = R.string.clear_cache,
                onClick = onClearCacheClick,
            ),
        )
        add(
            DropDownMenuItem(
                textRes = R.string.clear_data,
                onClick = onClearDataClick,
            ),
        )
        add(
            DropDownMenuItem(
                textRes = R.string.uninstall,
                onClick = onUninstallClick,
            ),
        )
    }
    BlockerDropdownMenu(
        offset = offset,
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        menuList = items,
    )
}

@Preview
@Composable
fun AppListItemMenuPreview() {
    BlockerTheme {
        Surface {
            AppListItemMenuList(
                expanded = true,
                isAppRunning = true,
                isAppEnabled = false,
                onClearCacheClick = { },
                onClearDataClick = { },
                onForceStopClick = { },
                onUninstallClick = { },
                onEnableClick = { },
                onDisableClick = { },
                onDismissRequest = { },
            )
        }
    }
}
