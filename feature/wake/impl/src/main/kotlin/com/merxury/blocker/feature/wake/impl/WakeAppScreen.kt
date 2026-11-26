// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.feature.wake.api.R.string

@Composable
internal fun WakeAppRoute(
    viewModel: WakeAppViewModel,
    active: Boolean,
    onBack: () -> Unit,
    onPeerClick: (String) -> Unit,
    onComponents: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    DisposableEffect(viewModel, active) {
        onDispose {
            // Recreating the activity for a configuration change does not leave the screen.
            if (active && activity?.isChangingConfigurations != true) viewModel.leaveScreen()
        }
    }
    WakeAppScreen(
        state = if (active) state else state.copy(notice = null),
        onBack = onBack,
        onPeerClick = onPeerClick,
        onComponents = onComponents,
        onToggle = viewModel::toggle,
        onBackground = viewModel::setBackgroundAllowed,
        onBlock = viewModel::blockAll,
        onRestore = viewModel::restoreAll,
        onUndo = viewModel::undo,
        onDismissNotice = viewModel::dismissNotice,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WakeAppScreen(
    state: WakeAppUiState,
    onBack: () -> Unit,
    onPeerClick: (String) -> Unit,
    onComponents: () -> Unit = {},
    onToggle: (String, Boolean) -> Unit = { _, _ -> },
    onBackground: (Boolean) -> Unit = {},
    onBlock: (Set<String>) -> Unit = {},
    onRestore: () -> Unit = {},
    onUndo: (Long) -> Unit = {},
    onDismissNotice: (Long) -> Unit = {},
    initiallyExpanded: String? = null,
    initiallyShowingSheet: Boolean = false,
) = WakeTheme {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var normalExpanded by rememberSaveable { mutableStateOf(false) }
    var sheetOpen by rememberSaveable { mutableStateOf(initiallyShowingSheet) }
    var fabHeight by remember { mutableIntStateOf(0) }
    val fabVisible = state.canBlockAll && !sheetOpen
    val snackbar = remember { SnackbarHostState() }
    ShowWakeNotice(snackbar, state.notice, onUndo, onDismissNotice)
    Scaffold(
        topBar = { WakeAppBar(state.busy || !state.loaded, onBack, onComponents, onRestore) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(string.feature_wake_api_block_all)) },
                icon = { BlockerActionIcon(BlockerIcons.LinkOff, null, tint = LocalContentColor.current) },
                onClick = {
                    if (fabVisible && !state.busy) {
                        expanded = null
                        sheetOpen = true
                    }
                },
                modifier = Modifier.animateFloatingActionButton(fabVisible, Alignment.BottomEnd)
                    .onSizeChanged { fabHeight = it.height },
            )
        },
    ) { padding ->
        // Give the last list item room to scroll clear of the standard FAB.
        val fabClearance = with(LocalDensity.current) { fabHeight.toDp() }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(
                start = WakeContentSpacing,
                end = WakeContentSpacing,
                bottom = if (fabVisible) fabClearance + WakeContentSpacing else WakeContentSpacing,
            ),
        ) {
            if (state.loaded) item(key = "hero") { WakeAppHero(state, onPeerClick) }
            val expand: (String) -> Unit = { expanded = it.takeUnless { key -> expanded == key } }
            wakeDetailGroup(string.feature_wake_api_section_relations, state.relations, expanded, state.background, state.busy, expand, onToggle, onPeerClick)
            wakeDetailGroup(string.feature_wake_api_section_methods, state.methods, expanded, state.background, state.busy, expand, onToggle, onPeerClick)
            if (state.normal.parts().isNotEmpty()) {
                item(key = "normal") {
                    NormalWakeStarts(
                        normal = state.normal,
                        expanded = normalExpanded,
                        onExpand = { normalExpanded = !normalExpanded },
                        onComponents = onComponents,
                        modifier = Modifier.padding(bottom = WakeContentSpacing),
                    )
                }
            }
            state.background?.let { usage ->
                item(key = "background") { WakeBackgroundCard(usage, onBackground, state.busy) }
            }
        }
    }
    if (sheetOpen) {
        WakeBlockSheet(
            appLabel = state.app.label,
            choices = state.choices,
            onDismiss = { sheetOpen = false },
            onConfirm = onBlock,
        )
    }
}

@Composable
private fun ShowWakeNotice(
    host: SnackbarHostState,
    notice: WakeNotice?,
    onUndo: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
) {
    val message = notice?.text?.resolve()
    val undoLabel = if (notice?.undo != null) stringResource(string.feature_wake_api_undo) else null
    LaunchedEffect(host, notice?.id) {
        if (notice == null || message == null) return@LaunchedEffect
        when (host.showSnackbar(message, actionLabel = undoLabel, duration = if (undoLabel == null) SnackbarDuration.Short else SnackbarDuration.Long)) {
            SnackbarResult.ActionPerformed -> onUndo(notice.id)
            SnackbarResult.Dismissed -> onDismiss(notice.id)
        }
    }
}
