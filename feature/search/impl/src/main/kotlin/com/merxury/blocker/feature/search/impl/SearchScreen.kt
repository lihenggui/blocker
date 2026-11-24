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

package com.merxury.blocker.feature.search.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.AppAction
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.ProcessingProgressSnackbar
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.core.ui.applist.AppActionConfirmDialog
import com.merxury.blocker.core.ui.applist.AppRow
import com.merxury.blocker.core.ui.applist.PendingConfirmation
import com.merxury.blocker.core.ui.applist.PendingConfirmationSaver
import com.merxury.blocker.core.ui.applist.needsConfirmation
import com.merxury.blocker.core.ui.profile.ProfileSwitcher
import com.merxury.blocker.core.ui.screen.EmptyScreen
import com.merxury.blocker.feature.search.api.R.string
import com.merxury.blocker.feature.search.impl.component.SearchActions
import com.merxury.blocker.feature.search.impl.component.SearchResultTabs
import com.merxury.blocker.feature.search.impl.component.SearchTopBar
import com.merxury.blocker.feature.search.impl.component.leavingWith
import com.merxury.blocker.feature.search.impl.component.needsAccess
import com.merxury.blocker.feature.search.impl.component.text
import com.merxury.blocker.feature.search.impl.screen.SearchingScreen
import com.merxury.blocker.core.ui.R.string as uiString

/**
 * Opens the detail screen of an app.
 *
 * @param userId the user whose copy to open; null for the user Blocker runs in
 * @param keyword what the detail screen's component lists are filtered by; empty for none
 */
typealias OpenAppDetail = (packageName: String, userId: Int?, tab: AppDetailTabs, keyword: List<String>) -> Unit

/** The search of the selected user's apps, components and the general rules. */
@Composable
fun SearchScreen(
    snackbarHostState: SnackbarHostState,
    navigateToAppDetail: OpenAppDetail,
    navigateToRuleDetail: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    KeepFreshWhileStarted(viewModel::keepFresh)
    // The text is kept here, so typing never waits for the results.
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.search(query) }
    SearchMessages(uiState, snackbarHostState, viewModel::onMessageShown, onGrantAccess = { viewModel.grantAccess() })
    val actions = rememberSearchActions(viewModel, uiState, navigateToAppDetail, navigateToRuleDetail)
    SearchScreen(
        uiState = uiState,
        query = query,
        onQueryChange = { query = it },
        actions = actions,
        onSelectUser = viewModel::selectUser,
        onSelectAll = viewModel::toggleSelectAll,
        onBlockSelection = viewModel::blockSelected,
        onEnableSelection = viewModel::enableSelected,
    )
}

/** Runs [keepFresh] while the screen is at least started, so nothing is read in the background. */
@Composable
private fun KeepFreshWhileStarted(keepFresh: suspend () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(keepFresh)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { current() }
    }
}

/**
 * Shows each message for its whole duration, then reports it shown, so each is told once. A message
 * caused by missing access offers to grant it.
 */
@Composable
private fun SearchMessages(uiState: SearchUiState, snackbarHostState: SnackbarHostState, onConsume: (Long) -> Unit, onGrantAccess: () -> Unit) {
    val queued = uiState.messages.firstOrNull()
    val text = queued?.message?.text()
    val grantLabel = stringResource(uiString.core_ui_grant_access)
    val currentGrant by rememberUpdatedState(onGrantAccess)
    LaunchedEffect(queued?.id) {
        if (queued != null && text != null) {
            val actionLabel = grantLabel.takeIf { queued.message.needsAccess }
            val result = snackbarHostState.showSnackbar(text, actionLabel = actionLabel, duration = SnackbarDuration.Long, withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) currentGrant()
            onConsume(queued.id)
        }
    }
}

@Composable
private fun rememberSearchActions(
    viewModel: SearchViewModel,
    uiState: SearchUiState,
    navigateToAppDetail: OpenAppDetail,
    navigateToRuleDetail: (String) -> Unit,
): SearchActions {
    val currentState by rememberUpdatedState(uiState)
    val openApp by rememberUpdatedState(navigateToAppDetail)
    val openRule by rememberUpdatedState(navigateToRuleDetail)
    return remember(viewModel) {
        // The user is left out of the navigation for the user Blocker runs in.
        fun userIdOf(user: UserId): Int? = user.value.takeIf { user != currentState.ownUser }
        fun keyword() = listOf(currentState.keyword)
        SearchActions(
            onSelectTab = viewModel::selectTab,
            onOpenApp = { app -> openApp(app.key.packageName, userIdOf(app.key.userId), AppDetailTabs.Info, emptyList()) },
            onAppAction = { app, action -> viewModel.perform(action, app) },
            countServices = viewModel::countServices,
            onOpenGroup = { group ->
                val tab = group.firstType?.tab ?: AppDetailTabs.Info
                openApp(group.app.packageName, userIdOf(group.app.userId), tab, keyword())
            },
            onOpenComponent = { component ->
                openApp(component.id.packageName, userIdOf(currentState.selectedUser), component.type.tab, keyword())
            },
            onSelectionModeChange = viewModel::setSelectionMode,
            onSwitch = { component, enabled -> viewModel.setEnabled(component.id, enabled) },
            onToggleSelectedApp = viewModel::toggleSelectedApp,
            onRefreshComponents = viewModel::refreshComponents,
            onOpenRule = { id -> openRule(id) },
        )
    }
}

private val ComponentType.tab: AppDetailTabs
    get() = when (this) {
        ComponentType.RECEIVER -> AppDetailTabs.Receiver
        ComponentType.SERVICE -> AppDetailTabs.Service
        ComponentType.ACTIVITY -> AppDetailTabs.Activity
        ComponentType.PROVIDER -> AppDetailTabs.Provider
    }

/**
 * The search for [uiState]: the search field, or the selection's actions in selection mode, the
 * profile switcher when there is more than one user, and the results by tab. Actions on an app
 * that need it are confirmed first, for exactly the app they were asked for. Opening a result
 * hides the keyboard, which would otherwise stay open over the detail shown next to the search.
 */
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    modifier: Modifier = Modifier,
    query: String = uiState.keyword,
    onQueryChange: (String) -> Unit = {},
    actions: SearchActions = SearchActions(),
    onSelectUser: (UserId) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onBlockSelection: () -> Unit = {},
    onEnableSelection: () -> Unit = {},
) {
    // Keeps the exact app, user included, across a configuration change or process death.
    var confirming by rememberSaveable(stateSaver = PendingConfirmationSaver) { mutableStateOf<PendingConfirmation?>(null) }
    LaunchedEffect(uiState.selectedUser) {
        if (confirming?.app?.userId != uiState.selectedUser) confirming = null
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Opening a result hides the keyboard, which would otherwise stay open over the detail.
    val opening = remember(actions, keyboard, focusManager) {
        actions.leavingWith {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    val requestAction: (AppRow, AppAction) -> Unit = remember(actions) {
        { app, action -> if (action.needsConfirmation) confirming = PendingConfirmation(action, app.key) else actions.onAppAction(app.key, action) }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
            SearchTopBar(
                uiState = uiState,
                query = query,
                onQueryChange = onQueryChange,
                onExitSelection = { actions.onSelectionModeChange(false) },
                onSelectAll = onSelectAll,
                onBlockSelection = onBlockSelection,
                onEnableSelection = onEnableSelection,
                modifier = Modifier.testTag("blockerTopAppBar"),
            )
            if (uiState.showProfileSwitcher && !uiState.isSelectionMode) {
                ProfileSwitcher(profiles = uiState.profiles, selectedUser = uiState.selectedUser, onSelect = onSelectUser, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            when (val results = uiState.results) {
                SearchResults.Idle -> EmptyScreen(textRes = string.feature_search_api_no_search_result)
                SearchResults.Loading -> SearchingScreen()
                is SearchResults.Loaded -> SearchResultTabs(uiState = uiState, results = results, actions = opening, onAppAction = requestAction)
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
        }
        ProcessingProgressSnackbar(
            progress = uiState.batch,
            onDismiss = {},
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
    confirming?.let { pending ->
        val app = (uiState.results as? SearchResults.Loaded)?.apps?.find { it.key == pending.app } ?: return@let
        AppActionConfirmDialog(
            action = pending.action,
            appLabel = app.label,
            profile = uiState.selectedProfile.takeIf { uiState.showProfileSwitcher },
            onConfirm = { actions.onAppAction(pending.app, pending.action) },
            onDismissRequest = { confirming = null },
        )
    }
    TrackScreenViewEvent(screenName = "SearchScreen")
}
