/*
 * Copyright 2023 Blocker
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.core.ui.applist.AppList
import com.merxury.blocker.core.ui.applist.model.AppItem
import com.merxury.blocker.core.ui.bottomsheet.AppSortBottomSheet
import com.merxury.blocker.core.ui.bottomsheet.AppSortInfoUiState
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.InitializingScreen
import com.merxury.blocker.feature.applist.R.string
import com.merxury.blocker.feature.applist.component.TopAppBarMoreMenu
import kotlinx.coroutines.launch

@Composable
fun AppListRoute(
    navigateToAppDetail: (String) -> Unit,
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bottomSheetUiState by viewModel.appSortInfoUiState.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()
    val warningState by viewModel.warningState.collectAsStateWithLifecycle()
    val appList = viewModel.appListFlow.collectAsState()
    AppListScreen(
        uiState = uiState,
        bottomSheetUiState = bottomSheetUiState,
        appList = appList.value,
        onAppItemClick = navigateToAppDetail,
        onClearCacheClick = viewModel::clearCache,
        onClearDataClick = viewModel::clearData,
        onForceStopClick = viewModel::forceStop,
        onUninstallClick = viewModel::uninstall,
        onEnableClick = viewModel::enable,
        onDisableClick = viewModel::disable,
        onServiceStateUpdate = viewModel::updateServiceStatus,
        navigateToSettings = navigateToSettings,
        navigateToSupportAndFeedback = navigateToSupportAndFeedback,
        onSortOptionsClick = viewModel::loadAppSortInfo,
        onSortByClick = viewModel::updateAppSorting,
        onSortOrderClick = viewModel::updateAppSortingOrder,
        onChangeShowRunningAppsOnTop = viewModel::updateShowRunningAppsOnTop,
        modifier = modifier,
    )
    if (errorState != null) {
        BlockerErrorAlertDialog(
            title = errorState?.title.orEmpty(),
            text = errorState?.content.orEmpty(),
            onDismissRequest = viewModel::dismissErrorDialog,
        )
    }
    warningState?.let {
        BlockerWarningAlertDialog(
            title = it.title,
            text = stringResource(id = it.message),
            onDismissRequest = viewModel::dismissWarningDialog,
            onConfirmRequest = it.onPositiveButtonClicked,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    uiState: AppListUiState,
    bottomSheetUiState: AppSortInfoUiState,
    appList: List<AppItem>,
    onAppItemClick: (String) -> Unit,
    onClearCacheClick: (String) -> Unit,
    onClearDataClick: (String) -> Unit,
    onForceStopClick: (String) -> Unit,
    onUninstallClick: (String) -> Unit,
    onEnableClick: (String) -> Unit,
    onDisableClick: (String) -> Unit,
    onServiceStateUpdate: (String, Int) -> Unit,
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    onSortOptionsClick: () -> Unit,
    onSortByClick: (AppSorting) -> Unit,
    onSortOrderClick: (SortingOrder) -> Unit,
    onChangeShowRunningAppsOnTop: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var openBottomSheet by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            BlockerTopAppBar(
                title = stringResource(id = string.app_name),
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                onSortOptionsClick()
                                openBottomSheet = true
                            }
                        },
                    ) {
                        Icon(
                            imageVector = BlockerIcons.Sort,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    TopAppBarMoreMenu(
                        navigateToSettings = navigateToSettings,
                        navigateToFeedback = navigateToSupportAndFeedback,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal,
                    ),
                ),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val appListTestTag = "appList:applicationList"
            when (uiState) {
                is AppListUiState.Initializing -> InitializingScreen(processingName = uiState.processingName)

                is AppListUiState.Success -> AppList(
                    appList = appList,
                    onAppItemClick = onAppItemClick,
                    onClearCacheClick = onClearCacheClick,
                    onClearDataClick = onClearDataClick,
                    onForceStopClick = onForceStopClick,
                    onUninstallClick = onUninstallClick,
                    onEnableClick = onEnableClick,
                    onDisableClick = onDisableClick,
                    onServiceStateUpdate = onServiceStateUpdate,
                    modifier = modifier.testTag(appListTestTag),
                )

                is AppListUiState.Error -> ErrorScreen(uiState.error)
            }
        }
    }
    if (openBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { openBottomSheet = false },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
            ),
        ) {
            AppSortBottomSheet(
                uiState = bottomSheetUiState,
                onSortByClick = onSortByClick,
                onSortOrderClick = onSortOrderClick,
                onChangeShowRunningAppsOnTop = onChangeShowRunningAppsOnTop,
            )
        }
    }
    TrackScreenViewEvent(screenName = "AppListScreen")
}
