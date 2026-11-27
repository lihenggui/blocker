// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.BlockerIconButton
import com.merxury.blocker.core.designsystem.component.BlockerSearchTextField
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.LocalSnackbarHostState
import com.merxury.blocker.feature.wake.api.R.string
import com.merxury.blocker.core.ui.R.string as coreString

// Material layout margins and spacing grid.
internal val WakeContentSpacing = 16.dp
internal val WakeGridUnit = 4.dp

@Composable
internal fun WakeOverviewRoute(
    onAppClick: (String) -> Unit,
    viewModel: WakeOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val grantFailure = stringResource(coreString.core_ui_grant_failed)
    LaunchedEffect(viewModel, lifecycle, snackbar, grantFailure) {
        viewModel.accessGrantFailures.flowWithLifecycle(lifecycle).collect {
            snackbar.showSnackbar(grantFailure)
        }
    }

    WakeOverviewScreen(state, viewModel::grantAccess, viewModel::checkNow, onAppClick)
}

@Composable
internal fun WakeOverviewScreen(
    state: WakeOverviewUiState,
    onGrantAccess: () -> Unit,
    onCheckNow: () -> Unit,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var blockedExpanded by rememberSaveable { mutableStateOf(false) }
    var dormantExpanded by rememberSaveable { mutableStateOf(false) }
    var installedExpanded by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val overviewListState = rememberLazyListState()
    val ready = state as? WakeOverviewUiState.Ready
    val searching = ready?.takeIf { searchOpen }
    val closeSearch = {
        searchOpen = false
        searchQuery = ""
    }
    LaunchedEffect(state) {
        if (state !is WakeOverviewUiState.Ready && state != WakeOverviewUiState.Loading) closeSearch()
    }
    BackHandler(enabled = searching != null, onBack = closeSearch)

    WakeTheme {
        Column(modifier.fillMaxSize()) {
            if (searching != null) {
                WakeOverviewSearchBar(searchQuery, { searchQuery = it }, closeSearch)
                val results = remember(searching, searchQuery) { searching.searchResults(searchQuery) }
                val focus = LocalFocusManager.current
                WakeOverviewSearchResults(
                    results = results,
                    onAppClick = { packageName ->
                        focus.clearFocus()
                        onAppClick(packageName)
                    },
                    modifier = Modifier.weight(1f).imePadding(),
                )
                return@Column
            }
            WakeOverviewTopBar(ready, onCheckNow, onSearch = { searchOpen = true })
            if (state != WakeOverviewUiState.Loading) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = overviewListState,
                        contentPadding = PaddingValues(WakeContentSpacing),
                    ) {
                        item(key = "hero") {
                            WakeOverviewHero(state, onGrantAccess, onCheckNow)
                        }
                        when (state) {
                            is WakeOverviewUiState.Ready -> {
                                wakeAppGroup("keepAlive", string.feature_wake_api_section_keep_alive, state.keepAlive, onAppClick)
                                wakeAppGroup("attention", string.feature_wake_api_section_attention, state.attention, onAppClick)
                                wakeOverviewTotals(state)
                                wakeOverviewAppLists(
                                    state = state,
                                    blockedExpanded = blockedExpanded,
                                    dormantExpanded = dormantExpanded,
                                    onToggleBlocked = { blockedExpanded = !blockedExpanded },
                                    onToggleDormant = { dormantExpanded = !dormantExpanded },
                                    onAppClick = onAppClick,
                                )
                            }

                            is WakeOverviewUiState.Collecting -> {
                                item(key = "firstRun") { WakeFirstRunSection() }
                                wakeCollectingApps(state.installedApps, installedExpanded, { installedExpanded = !installedExpanded }, onAppClick)
                            }

                            else -> Unit
                        }
                    }
                    if (state is WakeOverviewUiState.Ready && state.checking) {
                        val description = stringResource(string.feature_wake_api_checking)
                        LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = description })
                    }
                }
            }
        }
    }
}

@Composable
private fun WakeOverviewTopBar(state: WakeOverviewUiState.Ready?, onCheckNow: () -> Unit, onSearch: () -> Unit) {
    BlockerTopAppBar(title = stringResource(string.feature_wake_api_title)) {
        if (state != null) {
            if (state.hasSearchableApps) {
                BlockerIconButton(BlockerIcons.Search, stringResource(string.feature_wake_api_search_apps), onClick = onSearch)
            }
            BlockerAppTopBarMenu(
                menuIcon = BlockerIcons.MoreVert,
                menuIconDesc = coreString.core_ui_more_menu,
                menuList = listOf(DropDownMenuItem(string.feature_wake_api_check_now, onCheckNow, enabled = !state.checking)),
            )
        }
    }
}

@Composable
private fun WakeOverviewSearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    BlockerTopAppBar(
        title = {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                BlockerSearchTextField(
                    onSearchQueryChange = onQueryChange,
                    onSearchTrigger = {},
                    modifier = Modifier.fillMaxWidth(),
                    searchQuery = query,
                    placeholder = { Text(stringResource(string.feature_wake_api_search_apps)) },
                )
            }
        },
        hasNavigationIcon = true,
        onNavigationClick = onClose,
    )
}
