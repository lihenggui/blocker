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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerCollapsingTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerSearchTextField
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.state.toolbar.ToolbarState
import com.merxury.blocker.core.ui.topbar.SelectedAppTopBar
import com.merxury.blocker.feature.appdetail.api.R.plurals
import com.merxury.blocker.feature.appdetail.api.R.string
import com.merxury.blocker.feature.appdetail.impl.AppHeaderUiState
import com.merxury.blocker.feature.appdetail.impl.ComponentListUiState
import com.merxury.blocker.feature.appdetail.impl.LaunchMode
import com.merxury.blocker.feature.appdetail.impl.listsComponents

/** What the top bar can ask for. */
class TopBarActions(
    val onBack: () -> Unit = {},
    val onLaunchApp: () -> Unit = {},
    val onSearchModeChange: (Boolean) -> Unit = {},
    val onSearch: (String) -> Unit = {},
    val onBlockAllInTab: () -> Unit = {},
    val onEnableAllInTab: () -> Unit = {},
    val onSortClick: () -> Unit = {},
    val onSelectionModeChange: (Boolean) -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onBlockSelected: () -> Unit = {},
    val onEnableSelected: () -> Unit = {},
    val onShareAppRules: () -> Unit = {},
    val onShareAllRules: () -> Unit = {},
)

/**
 * The collapsing header with the app's icon, name, package and version, and the profile when the
 * app is not of the user Blocker runs in; in selection mode, the count of the selected components
 * and the actions on them.
 *
 * @param batchRunning whether a batch runs for the app, which disables the batch actions
 */
@Composable
internal fun AppDetailTopBar(
    header: AppHeaderUiState,
    list: ComponentListUiState,
    toolbarState: ToolbarState,
    actions: TopBarActions,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    batchRunning: Boolean = false,
) {
    if (list.isSelectionMode) {
        SelectedAppTopBar(
            title = plurals.feature_appdetail_api_selected_component_count,
            selectedItemCount = list.selected.size,
            selectedComponentCount = list.selected.size,
            onNavigationClick = { actions.onSelectionModeChange(false) },
            onSelectAll = actions.onSelectAll,
            onBlockAllSelectedComponents = actions.onBlockSelected,
            onEnableAllSelectedComponents = actions.onEnableSelected,
            actionsEnabled = !batchRunning,
            modifier = modifier,
        )
        return
    }
    val app = header.app
    val version = app?.let { stringResource(string.feature_appdetail_api_data_with_explanation, it.versionName, it.versionCode) }
    val profile = header.profile?.headerText()
    // Without the app, as while it cannot be read, the profile stands alone.
    val summary = when {
        version != null && profile != null -> stringResource(string.feature_appdetail_api_summary_with_profile, version, profile)
        else -> version ?: profile.orEmpty()
    }
    BlockerCollapsingTopAppBar(
        showBackButton = showBackButton,
        progress = toolbarState.progress,
        onNavigationClick = {
            if (list.isSearchMode) actions.onSearchModeChange(false) else actions.onBack()
        },
        title = app?.label.orEmpty(),
        actions = {
            TopBarActionRow(
                list = list,
                hasCustomizedRules = header.hasCustomizedRules,
                actions = actions,
                batchRunning = batchRunning,
            )
        },
        subtitle = app?.packageName.orEmpty(),
        summary = summary,
        iconSource = app?.icon,
        // An app that cannot be opened from here offers no launch action at all.
        onIconClick = actions.onLaunchApp.takeIf { header.launch != LaunchMode.NONE },
        modifier = modifier
            .fillMaxWidth()
            .height(with(LocalDensity.current) { toolbarState.height.toDp() }),
    )
}

/**
 * Search and the menu on a tab of rows; on the matched rules tab only the menu, whose block all
 * and enable all act on every component the rules match. Nothing of either on the app info tab.
 */
@Composable
private fun TopBarActionRow(
    list: ComponentListUiState,
    hasCustomizedRules: Boolean,
    actions: TopBarActions,
    batchRunning: Boolean,
) {
    val tab = list.tabs.selectedItem
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tab.listsComponents) {
            if (list.isSearchMode) {
                // Opened with a keyword, as from the search, the keyboard stays closed.
                val focusOnStart = remember { list.keyword.isEmpty() }
                BlockerSearchTextField(
                    modifier = Modifier.weight(1f),
                    focusOnStart = focusOnStart,
                    searchQuery = list.keyword,
                    onSearchQueryChange = actions.onSearch,
                    onSearchTrigger = actions.onSearch,
                    placeholder = {
                        Text(
                            text = stringResource(id = string.feature_appdetail_api_search_components),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            } else {
                SearchActionMenu(onSearchModeChange = actions.onSearchModeChange)
            }
        }
        if (hasCustomizedRules) {
            ShareAction(shareAppRule = actions.onShareAppRules, shareAllRules = actions.onShareAllRules)
        }
        if (tab != AppDetailTabs.Info) {
            MoreActionMenu(
                blockAllComponents = actions.onBlockAllInTab,
                enableAllComponents = actions.onEnableAllInTab,
                onAdvanceSortClick = actions.onSortClick,
                switchSelectedMode = actions.onSelectionModeChange,
                listActions = tab.listsComponents,
                batchActionsEnabled = !batchRunning,
            )
        }
    }
}
