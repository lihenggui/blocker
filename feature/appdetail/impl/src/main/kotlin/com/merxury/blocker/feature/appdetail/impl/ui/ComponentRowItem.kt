/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.feature.appdetail.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.merxury.blocker.core.ui.component.ComponentListItem
import com.merxury.blocker.core.ui.component.ComponentRow

/** What a component row can ask for. */
class ComponentRowActions(
    /** The switch was turned; true means on. */
    val onSwitch: (ComponentRow, Boolean) -> Unit = { _, _ -> },
    /** The row was tapped outside selection mode. */
    val onClick: (ComponentRow) -> Unit = {},
    val onToggleSelected: (ComponentRow) -> Unit = {},
    val onStopService: (ComponentRow) -> Unit = {},
    val onLaunchActivity: (ComponentRow) -> Unit = {},
    val onCopyName: (ComponentRow) -> Unit = {},
    val onCopyFullName: (ComponentRow) -> Unit = {},
    val onEditIfwRule: (ComponentRow) -> Unit = {},
)

/**
 * One component: its names and description and a switch that shows whether it can run. While its
 * change is in flight the switch ignores taps; it moves once the system shows the change.
 */
@Composable
internal fun ComponentRowItem(
    row: ComponentRow,
    inFlight: Boolean,
    actions: ComponentRowActions,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
) {
    ComponentListItem(
        simpleName = row.id.simpleName,
        name = row.id.className,
        description = row.description,
        enabled = row.enabled,
        type = row.type,
        isServiceRunning = row.isRunning,
        modifier = modifier.testTag("component:${row.id.className}"),
        inFlight = inFlight,
        onClick = { actions.onClick(row) },
        onStopServiceClick = { actions.onStopService(row) },
        onLaunchActivityClick = { actions.onLaunchActivity(row) },
        onCopyNameClick = { actions.onCopyName(row) },
        onCopyFullNameClick = { actions.onCopyFullName(row) },
        onEditIfwRuleClick = { actions.onEditIfwRule(row) },
        onSwitchClick = { actions.onSwitch(row, it) },
        isSelected = isSelected,
        isSelectedMode = isSelectionMode,
        onSelectionToggle = { actions.onToggleSelected(row) },
    )
}
