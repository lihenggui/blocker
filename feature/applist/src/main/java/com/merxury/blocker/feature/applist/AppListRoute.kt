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

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
import com.merxury.blocker.feature.applist.appdetail.AppDetailViewModel
import com.merxury.blocker.feature.applist.applist.AppListScreen
import com.merxury.blocker.feature.applist.applist.AppListViewModel

@Composable
fun AppListRoute(
    listState: LazyGridState,
    shouldShowTwoPane: Boolean,
    navigateToAppDetail: (String) -> Unit,
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    navigateTooAppSortScreen: () -> Unit,
    modifier: Modifier = Modifier,
    appListViewModel: AppListViewModel = hiltViewModel(),
    appDetailViewModel: AppDetailViewModel = hiltViewModel(),
) {
    val uiState by appListViewModel.uiState.collectAsStateWithLifecycle()
    val errorState by appListViewModel.errorState.collectAsStateWithLifecycle()
    val warningState by appListViewModel.warningState.collectAsStateWithLifecycle()
    val appList = appListViewModel.appListFlow.collectAsState()
    AppListScreen(
        uiState = uiState,
        appList = appList.value,
        onAppItemClick = navigateToAppDetail,
        onClearCacheClick = appListViewModel::clearCache,
        onClearDataClick = appListViewModel::clearData,
        onForceStopClick = appListViewModel::forceStop,
        onUninstallClick = appListViewModel::uninstall,
        onEnableClick = appListViewModel::enable,
        onDisableClick = appListViewModel::disable,
        onServiceStateUpdate = appListViewModel::updateServiceStatus,
        navigateToSettings = navigateToSettings,
        navigateToSupportAndFeedback = navigateToSupportAndFeedback,
        navigateTooAppSortScreen = navigateTooAppSortScreen,
        modifier = modifier,
    )
    if (errorState != null) {
        BlockerErrorAlertDialog(
            title = errorState?.title.orEmpty(),
            text = errorState?.content.orEmpty(),
            onDismissRequest = appListViewModel::dismissErrorDialog,
        )
    }
    warningState?.let {
        BlockerWarningAlertDialog(
            title = it.title,
            text = stringResource(id = it.message),
            onDismissRequest = appListViewModel::dismissWarningDialog,
            onConfirmRequest = it.onPositiveButtonClicked,
        )
    }
}
