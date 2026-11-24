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

package com.merxury.blocker.feature.applist.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.screen.EmptyScreen
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.applist.api.R.string
import com.merxury.blocker.feature.applist.impl.component.AppActionConfirmDialog
import com.merxury.blocker.feature.applist.impl.component.AppSortBottomSheetRoute
import com.merxury.blocker.feature.applist.impl.component.PendingConfirmation
import com.merxury.blocker.feature.applist.impl.component.PendingConfirmationSaver
import com.merxury.blocker.feature.applist.impl.component.ProfileSwitcher
import com.merxury.blocker.feature.applist.impl.component.TopAppBarMoreMenu
import com.merxury.blocker.feature.applist.impl.component.UserAppList
import com.merxury.blocker.feature.applist.impl.component.needsConfirmation
import com.merxury.blocker.feature.applist.impl.component.previewAppRows
import com.merxury.blocker.feature.applist.impl.component.text

@Composable
fun AppListScreen(
    navigateToAppDetail: (packageName: String, userId: Int?) -> Unit,
    navigateToAppSlimming: () -> Unit,
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: AppListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refreshOnAccessChanges() }
    val messageText = uiState.message?.text()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText, duration = SnackbarDuration.Long)
            viewModel.onMessageShown()
        }
    }
    AppListScreen(
        uiState = uiState,
        navigateToAppDetail = navigateToAppDetail,
        onSelectUser = viewModel::selectUser,
        onRefresh = viewModel::refresh,
        onAction = { app, action -> viewModel.perform(action, app) },
        countServices = viewModel::countServices,
        navigateToAppSlimming = navigateToAppSlimming,
        navigateToSettings = navigateToSettings,
        navigateToSupportAndFeedback = navigateToSupportAndFeedback,
        modifier = modifier,
    )
}

private fun AppListViewModel.perform(action: AppAction, app: AppKey) {
    when (action) {
        AppAction.ENABLE -> setEnabled(app, enabled = true)
        AppAction.DISABLE -> setEnabled(app, enabled = false)
        AppAction.FORCE_STOP -> forceStop(app)
        AppAction.CLEAR_CACHE -> clearCache(app)
        AppAction.CLEAR_DATA -> clearData(app)
        AppAction.UNINSTALL -> uninstall(app)
    }
}

/**
 * The list of the selected user's apps. Tapping an app opens its detail screen for that user; the
 * user is left out of the navigation for the user Blocker runs in.
 */
@Composable
fun AppListScreen(
    uiState: AppListUiState,
    modifier: Modifier = Modifier,
    navigateToAppDetail: (packageName: String, userId: Int?) -> Unit = { _, _ -> },
    onSelectUser: (UserId) -> Unit = {},
    onRefresh: () -> Unit = {},
    onAction: (AppKey, AppAction) -> Unit = { _, _ -> },
    countServices: suspend (AppKey) -> Unit = {},
    navigateToAppSlimming: () -> Unit = {},
    navigateToSettings: () -> Unit = {},
    navigateToSupportAndFeedback: () -> Unit = {},
) {
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    // Keeps the exact app, user included, across a configuration change or process death.
    var confirming by rememberSaveable(stateSaver = PendingConfirmationSaver) { mutableStateOf<PendingConfirmation?>(null) }
    // A confirmation opened for another user than the one shown, such as after the selection fell
    // back to Blocker's own user, is closed rather than moved to the same package there.
    LaunchedEffect(uiState.selectedUser) {
        if (confirming?.app?.userId != uiState.selectedUser) confirming = null
    }
    val shownApps = (uiState.apps as? AppsUiState.Loaded)?.apps.orEmpty().filter { it.key.userId == uiState.selectedUser }
    val requestAction: (AppRow, AppAction) -> Unit = { app, action ->
        if (action.needsConfirmation) {
            confirming = PendingConfirmation(action, app.key)
        } else {
            onAction(app.key, action)
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppListTopBar(
            showSort = uiState.apps is AppsUiState.Loaded,
            onSortClick = { showSortSheet = true },
            navigateToAppSlimming = navigateToAppSlimming,
            navigateToSettings = navigateToSettings,
            navigateToSupportAndFeedback = navigateToSupportAndFeedback,
        )
        if (uiState.showProfileSwitcher) {
            ProfileSwitcher(
                profiles = uiState.profiles,
                selectedUser = uiState.selectedUser,
                onSelect = onSelectUser,
            )
        }
        AppListContent(
            uiState = uiState,
            onRefresh = onRefresh,
            onAppClick = { app ->
                val userId = app.key.userId.takeIf { it != uiState.ownUser }?.value
                navigateToAppDetail(app.key.packageName, userId)
            },
            onAction = requestAction,
            countServices = countServices,
        )
    }
    if (showSortSheet) {
        AppSortBottomSheetRoute(dismissHandler = { showSortSheet = false })
    }
    confirming?.let { pending ->
        val app = shownApps.find { it.key == pending.app } ?: return@let
        AppActionConfirmDialog(
            action = pending.action,
            appLabel = app.label,
            profile = uiState.selectedProfile.takeIf { uiState.showProfileSwitcher },
            onConfirm = { onAction(pending.app, pending.action) },
            onDismissRequest = { confirming = null },
        )
    }
    TrackScreenViewEvent(screenName = "AppListScreen")
}

@Composable
private fun AppListTopBar(
    showSort: Boolean,
    onSortClick: () -> Unit,
    navigateToAppSlimming: () -> Unit,
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
) {
    BlockerTopAppBar(
        title = stringResource(id = string.feature_applist_api_app_name),
        actions = {
            if (showSort) {
                IconButton(onClick = onSortClick) {
                    Icon(
                        imageVector = BlockerIcons.Sort,
                        contentDescription = stringResource(id = string.feature_applist_api_sort_menu),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            TopAppBarMoreMenu(
                navigateToAppSlimming = navigateToAppSlimming,
                navigateToSettings = navigateToSettings,
                navigateToFeedback = navigateToSupportAndFeedback,
            )
        },
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AppListContent(
    uiState: AppListUiState,
    onRefresh: () -> Unit,
    onAppClick: (AppRow) -> Unit,
    onAction: (AppRow, AppAction) -> Unit,
    countServices: suspend (AppKey) -> Unit,
) {
    val refreshingState = rememberPullRefreshState(refreshing = uiState.isRefreshing, onRefresh = onRefresh)
    Box(modifier = Modifier.pullRefresh(refreshingState)) {
        when (val apps = uiState.apps) {
            AppsUiState.Loading -> LoadingScreen()

            is AppsUiState.Error -> Pullable { ErrorScreen(UiMessage(stringResource(string.feature_applist_api_load_failed)), it) }

            is AppsUiState.Loaded -> Column {
                if (uiState.refreshFailure != null) {
                    RefreshFailedBanner(onRetry = onRefresh)
                }
                if (apps.apps.isEmpty()) {
                    Pullable { EmptyScreen(textRes = string.feature_applist_api_no_applications_to_display, modifier = it) }
                } else {
                    UserAppList(
                        apps = apps.apps,
                        onAppClick = onAppClick,
                        onAction = onAction,
                        countServices = countServices.takeIf { uiState.showServiceInfo },
                        modifier = Modifier.testTag("appList:applicationList"),
                    )
                }
            }
        }
        PullRefreshIndicator(
            refreshing = uiState.isRefreshing,
            state = refreshingState,
            modifier = Modifier.align(Alignment.TopCenter),
            scale = true,
        )
    }
}

/**
 * Lets a full-screen [content] without a list take the pull-to-refresh gesture, which only reaches
 * the indicator through a scrollable child. [content] fills the screen with the modifier given.
 */
@Composable
private fun Pullable(content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val height = maxHeight
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            content(Modifier.height(height))
        }
    }
}

/** Says that the list shown is the cached one because the last refresh failed. */
@Composable
private fun RefreshFailedBanner(onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().testTag("appList:refreshFailed"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = stringResource(string.feature_applist_api_refresh_failed),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            BlockerTextButton(onClick = onRetry) {
                Text(text = stringResource(string.feature_applist_api_retry))
            }
        }
    }
}

@PreviewThemes
@Composable
private fun AppListScreenPreview() {
    BlockerTheme {
        Surface {
            AppListScreen(
                uiState = AppListUiState(
                    ownUser = UserId.SYSTEM,
                    selectedUser = UserId.SYSTEM,
                    apps = AppsUiState.Loaded(previewAppRows()),
                ),
            )
        }
    }
}

@PreviewThemes
@Composable
private fun AppListScreenRefreshFailedPreview() {
    BlockerTheme {
        Surface {
            AppListScreen(
                uiState = AppListUiState(
                    ownUser = UserId.SYSTEM,
                    selectedUser = UserId.SYSTEM,
                    apps = AppsUiState.Loaded(previewAppRows()),
                    refreshFailure = ReadFailure.ChannelUnavailable,
                ),
            )
        }
    }
}
