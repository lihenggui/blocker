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

package com.merxury.blocker.feature.ruledetail.impl.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.ui.R.string
import com.merxury.blocker.core.ui.collapseList.CollapsibleList
import com.merxury.blocker.core.ui.collapseList.collapsibleGroup
import com.merxury.blocker.core.ui.component.ComponentListItem
import com.merxury.blocker.core.ui.component.ComponentRow
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.ruledetail.impl.MatchedApp
import com.merxury.blocker.feature.ruledetail.impl.MatchedAppsUiState

/**
 * What the matched apps and their component rows can ask for. A component is named with the user
 * of the app its row is in, so an action acts on what the row shows.
 */
class MatchedAppActions(
    /** A component's switch was turned; true means on. */
    val onSwitch: (ComponentKey, Boolean) -> Unit = { _, _ -> },
    val onStopService: (ComponentKey) -> Unit = {},
    val onLaunchActivity: (ComponentKey) -> Unit = {},
    val onCopyName: (String) -> Unit = {},
    val onCopyFullName: (String) -> Unit = {},
    val onBlockAll: (MatchedApp) -> Unit = {},
    val onEnableAll: (MatchedApp) -> Unit = {},
    val onOpenApp: (AppKey) -> Unit = {},
)

/**
 * The apps with components the rule matches, each expandable to those components. A switch
 * shows whether its component can run and ignores taps while a change of it is in flight; it
 * moves once the system shows the change.
 */
@Composable
fun RuleMatchedAppList(
    ruleMatchedAppListUiState: MatchedAppsUiState,
    modifier: Modifier = Modifier,
    inFlight: Set<ComponentId> = emptySet(),
    actions: MatchedAppActions = MatchedAppActions(),
) {
    when (ruleMatchedAppListUiState) {
        MatchedAppsUiState.Loading -> LoadingScreen()

        is MatchedAppsUiState.Loaded -> {
            if (ruleMatchedAppListUiState.apps.isEmpty()) {
                NoApplicableAppScreen()
                return
            }
            val apps = ruleMatchedAppListUiState.apps.associateBy { it.app.packageName }
            CollapsibleList(
                modifier = modifier.testTag("rule:matchedAppList"),
                groups = ruleMatchedAppListUiState.apps.map { collapsibleGroup(key = it.app.packageName, title = it.label, icon = it.icon, rows = it.rows) },
                itemKey = { it.id.className },
                navigationMenuItemDesc = string.core_ui_open_app_detail,
                navigateToDetail = { group -> apps[group.key]?.let { actions.onOpenApp(it.app) } },
                onBlockAllClick = { group -> apps[group.key]?.let(actions.onBlockAll) },
                onEnableAllClick = { group -> apps[group.key]?.let(actions.onEnableAll) },
            ) { row, itemModifier ->
                val user = apps[row.id.packageName]?.app?.userId ?: return@CollapsibleList
                MatchedComponentItem(row = row, key = ComponentKey(row.id, user), inFlight = row.id in inFlight, actions = actions, modifier = itemModifier)
            }
        }
    }
}

@Composable
private fun MatchedComponentItem(
    row: ComponentRow,
    key: ComponentKey,
    inFlight: Boolean,
    actions: MatchedAppActions,
    modifier: Modifier = Modifier,
) {
    ComponentListItem(
        modifier = modifier,
        simpleName = row.id.simpleName,
        name = row.id.className,
        description = row.description,
        enabled = row.enabled,
        type = row.type,
        isServiceRunning = row.isRunning,
        inFlight = inFlight,
        onStopServiceClick = { actions.onStopService(key) },
        onLaunchActivityClick = { actions.onLaunchActivity(key) },
        onCopyNameClick = { actions.onCopyName(row.id.simpleName) },
        onCopyFullNameClick = { actions.onCopyFullName(row.id.className) },
        onSwitchClick = { enabled -> actions.onSwitch(key, enabled) },
    )
}

@Composable
fun NoApplicableAppScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BlockerBodyLargeText(
            text = stringResource(id = string.core_ui_no_applicable_app),
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
@Preview
private fun RuleMatchedAppListPreview() {
    val row = ComponentRow(
        id = ComponentId("com.merxury.blocker.test1", "com.merxury.blocker.test1.ui.component.ComponentListActivity"),
        type = ACTIVITY,
        enabled = true,
    )
    val uiState = MatchedAppsUiState.Loaded(
        listOf(
            MatchedApp(app = AppKey("com.merxury.blocker.test1", UserId.SYSTEM), label = "Blocker", icon = null, rows = listOf(row)),
            MatchedApp(app = AppKey("com.merxury.blocker.test2", UserId.SYSTEM), label = "Test long long long long long name", icon = null, rows = emptyList()),
        ),
    )
    BlockerTheme {
        Surface {
            RuleMatchedAppList(
                ruleMatchedAppListUiState = uiState,
            )
        }
    }
}
