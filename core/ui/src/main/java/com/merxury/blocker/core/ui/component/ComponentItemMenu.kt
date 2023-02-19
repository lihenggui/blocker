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

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerDropdownMenu
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.ui.R

@Composable
fun ComponentItemMenu(
    expanded: Boolean,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    type: ComponentType,
    isServiceRunning: Boolean,
    onStopServiceClick: () -> Unit,
    onLaunchActivityClick: () -> Unit,
    onCopyNameClick: () -> Unit,
    onCopyPackageNameClick: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val items = buildList {
        add(
            DropDownMenuItem(
                textRes = R.string.copy_name,
                onClick = onCopyNameClick,
            ),
        )
        add(
            DropDownMenuItem(
                textRes = R.string.copy_full_name,
                onClick = onCopyPackageNameClick,
            ),
        )
        if (type == SERVICE && isServiceRunning) {
            add(
                DropDownMenuItem(
                    textRes = R.string.stop_service,
                    onClick = onStopServiceClick,
                ),
            )
        }
        if (type == ACTIVITY) {
            add(
                DropDownMenuItem(
                    textRes = R.string.launch_activity,
                    onClick = onLaunchActivityClick,
                ),
            )
        }
    }
    BlockerDropdownMenu(
        offset = offset,
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        menuList = items,
    )
}
