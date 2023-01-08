/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.feature.applist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumedWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerHomeTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.feature.applist.R.string
import com.merxury.blocker.feature.applist.component.AppListItem
import com.merxury.blocker.feature.applist.component.TopAppBarMoreMenu
import com.merxury.blocker.feature.applist.component.TopAppBarSortMenu

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun AppListRoute(
    navigateToAppDetail: (String) -> Unit,
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorState by remember { viewModel.errorState }
    AppListScreen(
        uiState = uiState,
        onAppItemClick = navigateToAppDetail,
        onClearCacheClick = viewModel::clearCache,
        onClearDataClick = viewModel::clearData,
        onForceStopClick = viewModel::forceStop,
        onUninstallClick = viewModel::uninstall,
        onEnableClick = viewModel::enable,
        onDisableClick = viewModel::disable,
        onSortingUpdate = viewModel::updateSorting,
        onServiceStateUpdate = viewModel::updateServiceStatus,
        navigateToSettings = navigateToSettings,
        navigateToSupportAndFeedback = navigateToSupportAndFeedback,
        modifier = modifier
    )
    if (errorState != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            title = {
                Text(errorState?.message.orEmpty())
            },
            text = {
                Text(errorState?.stackTrace.orEmpty())
            },
            confirmButton = {
                BlockerTextButton(
                    onClick = { viewModel.dismissDialog() }
                ) {
                    Text(stringResource(id = android.R.string.ok))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppListScreen(
    uiState: AppListUiState,
    onAppItemClick: (String) -> Unit,
    onClearCacheClick: (String) -> Unit,
    onClearDataClick: (String) -> Unit,
    onForceStopClick: (String) -> Unit,
    onUninstallClick: (String) -> Unit,
    onEnableClick: (String) -> Unit,
    onDisableClick: (String) -> Unit,
    onServiceStateUpdate: (String) -> Unit,
    onSortingUpdate: (AppSorting) -> Unit,
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            BlockerHomeTopAppBar(
                titleRes = string.app_name,
                actions = {
                    TopAppBarSortMenu(onSortingUpdate)
                    TopAppBarMoreMenu(
                        navigateToSettings = navigateToSettings,
                        navigateToFeedback = navigateToSupportAndFeedback,
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .consumedWindowInsets(padding)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal
                    )
                ),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState) {
                AppListUiState.Loading -> {
                    Column(
                        modifier = modifier
                            .fillMaxSize()
                            .padding(padding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BlockerLoadingWheel(
                            modifier = modifier,
                            contentDesc = stringResource(id = string.loading),
                        )
                    }
                }

                is AppListUiState.Success -> {
                    AppListContent(
                        appList = uiState.appList,
                        onAppItemClick = onAppItemClick,
                        onClearCacheClick = onClearCacheClick,
                        onClearDataClick = onClearDataClick,
                        onForceStopClick = onForceStopClick,
                        onUninstallClick = onUninstallClick,
                        onEnableClick = onEnableClick,
                        onDisableClick = onDisableClick,
                        onServiceStateUpdate = onServiceStateUpdate,
                        modifier = modifier
                    )
                }

                is AppListUiState.Error -> ErrorAppListScreen(uiState.error)
            }
        }
    }
}

@Composable
fun AppListContent(
    appList: SnapshotStateList<AppItem>,
    onAppItemClick: (String) -> Unit,
    onClearCacheClick: (String) -> Unit,
    onClearDataClick: (String) -> Unit,
    onForceStopClick: (String) -> Unit,
    onUninstallClick: (String) -> Unit,
    onEnableClick: (String) -> Unit,
    onDisableClick: (String) -> Unit,
    onServiceStateUpdate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listContent = remember { appList }
    val listState = rememberLazyListState()
    Box(modifier) {
        LazyColumn(
            modifier = modifier,
            state = listState
        ) {
            items(listContent, key = { it.packageName }) {
                AppListItem(
                    label = it.label,
                    packageName = it.packageName,
                    versionName = it.versionName,
                    packageInfo = it.packageInfo,
                    appServiceStatus = it.appServiceStatus,
                    onClick = onAppItemClick,
                    onClearCacheClick = onClearCacheClick,
                    onClearDataClick = onClearDataClick,
                    onForceStopClick = onForceStopClick,
                    onUninstallClick = onUninstallClick,
                    onEnableClick = onEnableClick,
                    onDisableClick = onDisableClick,
                )
                LaunchedEffect(true) {
                    onServiceStateUpdate(it.packageName)
                }
            }
        }
    }
}

@Composable
fun ErrorAppListScreen(message: ErrorMessage) {
    Text(text = message.message)
}
