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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarDuration.Short
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
import com.merxury.blocker.core.rule.R.string
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.feature.applist.appdetail.AppDetailScreen
import com.merxury.blocker.feature.applist.appdetail.AppDetailViewModel
import com.merxury.blocker.feature.applist.applist.AppListScreen
import com.merxury.blocker.feature.applist.applist.AppListViewModel
import kotlinx.coroutines.launch

@Composable
fun AppListRoute(
    onBackClick: () -> Unit,
    shouldShowTwoPane: Boolean,
    navigateToAppDetail: (String) -> Unit,
    navigateToSettings: () -> Unit,
    navigateToSupportAndFeedback: () -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    appListViewModel: AppListViewModel = hiltViewModel(),
    appDetailViewModel: AppDetailViewModel = hiltViewModel(),
) {
    val appListUiState by appListViewModel.uiState.collectAsStateWithLifecycle()
    val appListBottomSheetState by appListViewModel.appSortInfoUiState.collectAsStateWithLifecycle()
    val appListErrorState by appListViewModel.errorState.collectAsStateWithLifecycle()
    val warningState by appListViewModel.warningState.collectAsStateWithLifecycle()
    val appList = appListViewModel.appListFlow.collectAsState()
    val tabState by appDetailViewModel.tabState.collectAsStateWithLifecycle()
    val appInfoBottomSheetState by appDetailViewModel.componentSortInfoUiState.collectAsStateWithLifecycle()
    val appInfoUiState by appDetailViewModel.appInfoUiState.collectAsStateWithLifecycle()
    val appDetailErrorState by appDetailViewModel.errorState.collectAsStateWithLifecycle()
    val topAppBarUiState by appDetailViewModel.appBarUiState.collectAsStateWithLifecycle()
    val componentListUiState by appDetailViewModel.componentListUiState.collectAsStateWithLifecycle()
    val event by appDetailViewModel.eventFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Row(modifier = modifier.fillMaxSize()) {
        if (shouldShowTwoPane || appInfoUiState == null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(
                        if (appInfoUiState != null) {
                            Modifier.widthIn(min = 300.dp)
                        } else {
                            Modifier.weight(1f)
                        },
                    ),
            ) {
                AppListScreen(
                    uiState = appListUiState,
                    bottomSheetUiState = appListBottomSheetState,
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
                    onSortOptionsClick = appListViewModel::loadAppSortInfo,
                    onSortByClick = appListViewModel::updateAppSorting,
                    onSortOrderClick = appListViewModel::updateAppSortingOrder,
                    onChangeShowRunningAppsOnTop = appListViewModel::updateShowRunningAppsOnTop,
                    modifier = Modifier.matchParentSize(),
                )
                if (appListErrorState != null) {
                    BlockerErrorAlertDialog(
                        title = appListErrorState?.title.orEmpty(),
                        text = appListErrorState?.content.orEmpty(),
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
        }
        AnimatedVisibility(
            visible = appInfoUiState != null,
            enter = fadeIn(initialAlpha = 0.3f),
            exit = fadeOut(targetAlpha = 0.3f),
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .run {
                    if (!shouldShowTwoPane) {
                        safeDrawingPadding()
                    } else {
                        this
                    }
                },
        ) {
            appInfoUiState?.let { appInfoUiState ->
                AppDetailScreen(
                    appInfoUiState = appInfoUiState,
                    bottomSheetState = appInfoBottomSheetState,
                    topAppBarUiState = topAppBarUiState,
                    componentListUiState = componentListUiState,
                    tabState = tabState,
                    navigateToComponentDetail = navigateToComponentDetail,
                    modifier = modifier.fillMaxSize(),
                    onLaunchAppClick = { packageName ->
                        appDetailViewModel.launchApp(context, packageName)
                    },
                    switchTab = appDetailViewModel::switchTab,
                    onBackClick = onBackClick,
                    onSearchTextChanged = appDetailViewModel::search,
                    onSearchModeChanged = appDetailViewModel::changeSearchMode,
                    blockAllComponents = { appDetailViewModel.controlAllComponents(false) },
                    enableAllComponents = { appDetailViewModel.controlAllComponents(true) },
                    onExportRules = appDetailViewModel::exportBlockerRule,
                    onImportRules = appDetailViewModel::importBlockerRule,
                    onExportIfw = appDetailViewModel::exportIfwRule,
                    onImportIfw = appDetailViewModel::importIfwRule,
                    onResetIfw = appDetailViewModel::resetIfw,
                    onSwitchClick = appDetailViewModel::controlComponent,
                    onStopServiceClick = appDetailViewModel::stopService,
                    onLaunchActivityClick = appDetailViewModel::launchActivity,
                    onCopyNameClick = { clipboardManager.setText(AnnotatedString(it)) },
                    onCopyFullNameClick = { clipboardManager.setText(AnnotatedString(it)) },
                    onSortOptionsClick = appDetailViewModel::loadComponentSortInfo,
                    onSortByClick = appDetailViewModel::updateComponentSorting,
                    onSortOrderClick = appDetailViewModel::updateComponentSortingOrder,
                    onShowPriorityClick = appDetailViewModel::updateComponentShowPriority,
                )
            }
            if (appDetailErrorState != null) {
                BlockerErrorAlertDialog(
                    title = appDetailErrorState?.title.orEmpty(),
                    text = appDetailErrorState?.content.orEmpty(),
                    onDismissRequest = appDetailViewModel::dismissAlert,
                )
            }
            event?.let {
                val messageRes = when (it.second) {
                    RuleWorkResult.STARTED -> string.processing_please_wait
                    RuleWorkResult.FINISHED -> string.done
                    RuleWorkResult.FOLDER_NOT_DEFINED,
                    RuleWorkResult.MISSING_STORAGE_PERMISSION,
                    -> string.error_msg_folder_not_defined

                    RuleWorkResult.MISSING_ROOT_PERMISSION -> string.error_msg_missing_root_permission
                    RuleWorkResult.UNEXPECTED_EXCEPTION -> string.error_msg_unexpected_exception
                    RuleWorkResult.CANCELLED -> string.task_cancelled
                    else -> string.error_msg_unexpected_exception
                }
                val message = stringResource(id = messageRes)
                LaunchedEffect(message) {
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = Short,
                            withDismissAction = true,
                        )
                    }
                }
            }
            LaunchedEffect(Unit) {
                appDetailViewModel.initShizuku()
            }
        }
    }
}
