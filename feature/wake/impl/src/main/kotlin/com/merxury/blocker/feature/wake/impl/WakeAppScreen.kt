// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.feature.wake.api.R.string

@Composable
internal fun WakeAppRoute(
    viewModel: WakeAppViewModel,
    onBack: () -> Unit,
    onPeerClick: (String) -> Unit,
    onComponents: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WakeAppScreen(
        state = state,
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
    val expand: (String) -> Unit = { expanded = it.takeUnless { key -> expanded == key } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))) {
        var noticeHeight by remember { mutableStateOf(0.dp) }
        val fabLift by animateDpAsState(
            if (noticeHeight == 0.dp) 0.dp else noticeHeight + 8.dp,
            MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "Block all position",
        )
        Column(Modifier.fillMaxSize()) {
            WakeAppBar(state.busy || !state.loaded, onBack, onComponents, onRestore)
            val bottomPadding = 112.dp + if (state.canBlockAll) fabLift else 0.dp
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding)) {
                if (state.loaded) item(key = "hero") { WakeAppHero(state, onPeerClick) }
                wakeDetailGroup(string.feature_wake_api_section_relations, state.relations, expanded, state.busy, expand, onToggle, onPeerClick)
                wakeDetailGroup(string.feature_wake_api_section_methods, state.methods, expanded, state.busy, expand, onToggle, onPeerClick)
                if (state.normal.parts().isNotEmpty()) {
                    item(key = "normal") {
                        Box(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                            NormalWakeStarts(state.normal, normalExpanded) { normalExpanded = !normalExpanded }
                        }
                    }
                }
                state.background?.let { usage ->
                    item(key = "background") { WakeBackgroundCard(usage, onBackground, state.busy) }
                }
            }
        }

        WakeBlockFab(state.canBlockAll && !sheetOpen, fabLift, onClick = {
            if (!state.busy) {
                expanded = null
                sheetOpen = true
            }
        }, modifier = Modifier.align(Alignment.BottomEnd))
        WakeSnackbarHost(
            state.notice,
            onUndo,
            onDismissNotice,
            undoEnabled = !state.busy,
            onHeight = { noticeHeight = it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeBlockFab(visible: Boolean, lift: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.padding(end = 16.dp, bottom = 16.dp + lift),
        enter = fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f),
        exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.defaultSpatialSpec(), targetScale = 0.6f),
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 56.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BlockerActionIcon(BlockerIcons.LinkOff, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    stringResource(string.feature_wake_api_block_all),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
