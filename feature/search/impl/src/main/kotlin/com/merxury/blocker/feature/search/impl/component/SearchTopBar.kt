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

package com.merxury.blocker.feature.search.impl.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.merxury.blocker.core.ui.topbar.SelectedAppTopBar
import com.merxury.blocker.feature.search.api.R
import com.merxury.blocker.feature.search.impl.SearchUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The search field, or in selection mode the count selected and the actions on the selection,
 * which are disabled while a batch runs. Back leaves selection mode.
 *
 * @param query the text in the search field, which the results follow once typing pauses
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun SearchTopBar(
    uiState: SearchUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onBlockSelection: () -> Unit,
    onEnableSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selecting = uiState.isSelectionMode
    val latestSelecting by rememberUpdatedState(selecting)
    val state = remember { SeekableTransitionState(selecting) }
    val transition = rememberTransition(state, label = "topBar")
    val scope = rememberCoroutineScope()
    LaunchedEffect(selecting) { state.animateTo(selecting) }

    PredictiveBackHandler(enabled = selecting) { events ->
        try {
            events.collect { if (selecting) state.seekTo(it.progress, false) }
        } catch (cancelled: CancellationException) {
            scope.launch { state.animateTo(latestSelecting) }
            throw cancelled
        }
        if (selecting) onExitSelection()
    }

    transition.Crossfade(animationSpec = tween(500), modifier = modifier) { target ->
        if (target) {
            SelectedAppTopBar(
                title = R.plurals.feature_search_api_selected_app_count,
                selectedItemCount = uiState.selectedAppCount,
                selectedComponentCount = uiState.selected.size,
                onNavigationClick = onExitSelection,
                onSelectAll = onSelectAll,
                onBlockAllSelectedComponents = onBlockSelection,
                onEnableAllSelectedComponents = onEnableSelection,
                actionsEnabled = uiState.selectionActionsEnabled,
            )
        } else {
            SearchBar(
                searchQuery = query,
                onSearchQueryChange = onQueryChange,
                onSearchTrigger = onQueryChange,
            )
        }
    }
}
