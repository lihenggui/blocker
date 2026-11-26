// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
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
    WakeTheme {
        Column(modifier.fillMaxSize()) {
            WakeOverviewTopBar(state, onCheckNow)
            if (state != WakeOverviewUiState.Loading) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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
                            }

                            is WakeOverviewUiState.Collecting -> item(key = "firstRun") { WakeFirstRunSection() }

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
private fun WakeOverviewTopBar(state: WakeOverviewUiState, onCheckNow: () -> Unit) {
    BlockerTopAppBar(title = stringResource(string.feature_wake_api_title)) {
        if (state is WakeOverviewUiState.Ready) {
            BlockerAppTopBarMenu(
                menuIcon = BlockerIcons.MoreVert,
                menuIconDesc = coreString.core_ui_more_menu,
                menuList = listOf(DropDownMenuItem(string.feature_wake_api_check_now, onCheckNow, enabled = !state.checking)),
            )
        }
    }
}
