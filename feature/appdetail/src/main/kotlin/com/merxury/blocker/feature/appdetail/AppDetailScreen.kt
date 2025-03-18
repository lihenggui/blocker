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

package com.merxury.blocker.feature.appdetail

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration.Short
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerCollapsingTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerScrollableTabRow
import com.merxury.blocker.core.designsystem.component.BlockerSearchTextField
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.component.MaxToolbarHeight
import com.merxury.blocker.core.designsystem.component.MinToolbarHeight
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.core.domain.model.ComponentSearchResult
import com.merxury.blocker.core.domain.model.MatchedHeaderData
import com.merxury.blocker.core.domain.model.MatchedItem
import com.merxury.blocker.core.domain.model.ZippedRule
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.result.Result.Error
import com.merxury.blocker.core.result.Result.Loading
import com.merxury.blocker.core.result.Result.Success
import com.merxury.blocker.core.rule.entity.RuleWorkResult.CANCELLED
import com.merxury.blocker.core.rule.entity.RuleWorkResult.FINISHED
import com.merxury.blocker.core.rule.entity.RuleWorkResult.FOLDER_NOT_DEFINED
import com.merxury.blocker.core.rule.entity.RuleWorkResult.MISSING_ROOT_PERMISSION
import com.merxury.blocker.core.rule.entity.RuleWorkResult.MISSING_STORAGE_PERMISSION
import com.merxury.blocker.core.rule.entity.RuleWorkResult.STARTED
import com.merxury.blocker.core.rule.entity.RuleWorkResult.UNEXPECTED_EXCEPTION
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.AppDetailTabs.Activity
import com.merxury.blocker.core.ui.AppDetailTabs.Info
import com.merxury.blocker.core.ui.AppDetailTabs.Provider
import com.merxury.blocker.core.ui.AppDetailTabs.Receiver
import com.merxury.blocker.core.ui.AppDetailTabs.Sdk
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.PreviewDevices
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.core.ui.component.ComponentList
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.core.ui.previewparameter.AppDetailTabStatePreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.AppListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.PreviewParameterData.appList
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SEARCH
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SHARE_RULE
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.core.ui.state.toolbar.ExitUntilCollapsedState
import com.merxury.blocker.core.ui.state.toolbar.ToolbarState
import com.merxury.blocker.core.ui.topbar.SelectedAppTopBar
import com.merxury.blocker.feature.appdetail.R.string
import com.merxury.blocker.feature.appdetail.sdk.SdkContent
import com.merxury.blocker.feature.appdetail.summary.SummaryContent
import com.merxury.blocker.feature.appdetail.ui.MoreActionMenu
import com.merxury.blocker.feature.appdetail.ui.SearchActionMenu
import com.merxury.blocker.feature.appdetail.ui.ShareAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import com.merxury.blocker.core.rule.R.string as rulestring
import com.merxury.blocker.core.ui.R.string as uistring

@Composable
fun AppDetailScreen(
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    onBackClick: () -> Unit,
    navigateToComponentDetail: (String) -> Unit,
    navigateToComponentSortScreen: () -> Unit,
    modifier: Modifier = Modifier,
    navigateToRuleDetail: (String) -> Unit = {},
    showBackButton: Boolean = true,
    viewModel: AppDetailViewModel = hiltViewModel(),
) {
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    val appInfoUiState by viewModel.appInfoUiState.collectAsStateWithLifecycle()
    val topAppBarUiState by viewModel.appBarUiState.collectAsStateWithLifecycle()
    val event by viewModel.eventFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    AppDetailScreen(
        appInfoUiState = appInfoUiState,
        topAppBarUiState = topAppBarUiState,
        componentListUiState = appInfoUiState.componentSearchUiState,
        tabState = tabState,
        navigateToComponentDetail = navigateToComponentDetail,
        navigateToRuleDetail = navigateToRuleDetail,
        navigateToComponentSortScreen = navigateToComponentSortScreen,
        modifier = modifier.fillMaxSize(),
        onLaunchAppClick = { packageName ->
            val result = viewModel.launchApp(context, packageName)
            if (!result) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(string.feature_appdetail_cannot_launch_this_app),
                        duration = Short,
                        withDismissAction = true,
                    )
                }
            }
        },
        switchTab = viewModel::switchTab,
        onBackClick = onBackClick,
        onSearchTextChange = viewModel::search,
        onSearchModeChange = viewModel::changeSearchMode,
        blockAllComponentsInPage = {
            viewModel.controlAllComponentsInPage(false) { current, total ->
                showDisableProgress(context, snackbarHostState, scope, current, total)
            }
        },
        enableAllComponentsInPage = {
            viewModel.controlAllComponentsInPage(true) { current, total ->
                showEnableProgress(context, snackbarHostState, scope, current, total)
            }
        },
        onExportRules = viewModel::exportBlockerRule,
        onImportRules = viewModel::importBlockerRule,
        onExportIfw = viewModel::exportIfwRule,
        onImportIfw = viewModel::importIfwRule,
        onResetIfw = viewModel::resetIfw,
        onSwitchClick = viewModel::controlComponent,
        onStopServiceClick = viewModel::stopService,
        onLaunchActivityClick = viewModel::launchActivity,
        onCopyNameClick = { clipboardManager.setText(AnnotatedString(it)) },
        onCopyFullNameClick = { clipboardManager.setText(AnnotatedString(it)) },
        updateIconThemingState = updateIconThemingState,
        onSelectAll = viewModel::selectAll,
        blockAllSelectedComponents = { viewModel.controlAllSelectedComponents(false) },
        enableAllSelectedComponents = { viewModel.controlAllSelectedComponents(true) },
        switchSelectedMode = viewModel::switchSelectedMode,
        onSelect = viewModel::selectItem,
        onDeselect = viewModel::deselectItem,
        blockAllInItem = {
            viewModel.controlAllComponents(it, false) { current, total ->
                showDisableProgress(context, snackbarHostState, scope, current, total)
            }
        },
        enableAllInItem = {
            viewModel.controlAllComponents(it, true) { current, total ->
                showEnableProgress(context, snackbarHostState, scope, current, total)
            }
        },
        shareAppRule = {
            scope.launch {
                viewModel.zipAppRule().collect { rule ->
                    shareFile(context, rule, scope, snackbarHostState)
                }
            }
        },
        shareAllRules = {
            scope.launch {
                viewModel.zipAllRule().collect { rule ->
                    shareFile(context, rule, scope, snackbarHostState)
                }
            }
        },
        onShowAppInfoClick = { viewModel.showAppInfo(context) },
        onRefresh = {
            viewModel.loadComponentList()
            viewModel.updateComponentList()
        },
        showBackButton = showBackButton,
    )
    if (appInfoUiState.error != null) {
        BlockerErrorAlertDialog(
            title = appInfoUiState.error?.title.orEmpty(),
            text = appInfoUiState.error?.content.orEmpty(),
            onDismissRequest = viewModel::dismissAlert,
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            updateIconThemingState(IconThemingState())
        }
    }
    event?.let {
        val messageRes = when (it.second) {
            STARTED -> rulestring.core_rule_processing_please_wait
            FINISHED -> rulestring.core_rule_done
            FOLDER_NOT_DEFINED,
            MISSING_STORAGE_PERMISSION,
                -> rulestring.core_rule_error_msg_folder_not_defined

            MISSING_ROOT_PERMISSION -> rulestring.core_rule_error_msg_missing_root_permission
            UNEXPECTED_EXCEPTION -> rulestring.core_rule_error_msg_unexpected_exception
            CANCELLED -> rulestring.core_rule_task_cancelled
            else -> rulestring.core_rule_error_msg_unexpected_exception
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
}

private fun showEnableProgress(
    context: Context,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    current: Int,
    total: Int,
) {
    scope.launch {
        if (current == total) {
            snackbarHostState.showSnackbarWithoutQueue(
                message = context.getString(uistring.core_ui_operation_completed),
                duration = Short,
                withDismissAction = true,
            )
        } else {
            snackbarHostState.showSnackbarWithoutQueue(
                message = context.getString(
                    uistring.core_ui_enabling_component_hint,
                    current,
                    total,
                ),
                duration = Short,
                withDismissAction = false,
            )
        }
    }
}

private fun showDisableProgress(
    context: Context,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    current: Int,
    total: Int,
) {
    scope.launch {
        if (current == total) {
            snackbarHostState.showSnackbarWithoutQueue(
                message = context.getString(uistring.core_ui_operation_completed),
                duration = Short,
                withDismissAction = true,
            )
        } else {
            snackbarHostState.showSnackbarWithoutQueue(
                message = context.getString(
                    uistring.core_ui_disabling_component_hint,
                    current,
                    total,
                ),
                duration = Short,
                withDismissAction = false,
            )
        }
    }
}

@SuppressLint("WrongConstant", "QueryPermissionsNeeded")
private fun shareFile(
    context: Context,
    rule: ZippedRule,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    val zippedFile = rule.zippedFile
    if (zippedFile == null) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(string.feature_appdetail_cannot_share_rule_report_issue),
            )
        }
        return
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.FileProvider",
        zippedFile,
    )
    val appPackageName = rule.packageName ?: context.getString(string.feature_appdetail_all_rules)
    val subject = context.getString(string.feature_appdetail_rules_sharing_title, appPackageName)
    val text = context.getString(string.feature_appdetail_provide_additional_details)
    val receiver = arrayOf("mercuryleee@gmail.com")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        clipData = ClipData.newRawUri("", uri)
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_EMAIL, receiver)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooserIntent = Intent.createChooser(intent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val activityInfo = chooserIntent.resolveActivityInfo(context.packageManager, intent.flags)
    if (activityInfo == null) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(string.feature_appdetail_cannot_share_rule_no_apps_found),
            )
        }
        return
    }
    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooserIntent)
}

@Composable
fun AppDetailScreen(
    appInfoUiState: AppInfoUiState,
    topAppBarUiState: AppBarUiState,
    componentListUiState: Result<ComponentSearchResult>,
    tabState: TabState<AppDetailTabs>,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    onBackClick: () -> Unit = {},
    onLaunchAppClick: (String) -> Unit = {},
    switchTab: (AppDetailTabs) -> Unit = {},
    navigateToComponentDetail: (String) -> Unit = {},
    navigateToRuleDetail: (String) -> Unit = {},
    onSearchTextChange: (String) -> Unit = {},
    onSearchModeChange: (Boolean) -> Unit = {},
    blockAllComponentsInPage: () -> Unit = {},
    enableAllComponentsInPage: () -> Unit = {},
    onShowAppInfoClick: () -> Unit = {},
    onExportRules: (String) -> Unit = {},
    onImportRules: (String) -> Unit = {},
    onExportIfw: (String) -> Unit = {},
    onImportIfw: (String) -> Unit = {},
    onResetIfw: (String) -> Unit = {},
    onSwitchClick: (ComponentInfo, Boolean) -> Unit = { _, _ -> },
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
    navigateToComponentSortScreen: () -> Unit = {},
    updateIconThemingState: (IconThemingState) -> Unit = {},
    onSelectAll: () -> Unit = {},
    blockAllSelectedComponents: () -> Unit = {},
    enableAllSelectedComponents: () -> Unit = {},
    switchSelectedMode: (Boolean) -> Unit = {},
    onSelect: (ComponentInfo) -> Unit = {},
    onDeselect: (ComponentInfo) -> Unit = {},
    blockAllInItem: (List<ComponentInfo>) -> Unit = {},
    enableAllInItem: (List<ComponentInfo>) -> Unit = {},
    shareAppRule: () -> Unit = {},
    shareAllRules: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    AppDetailContent(
        app = appInfoUiState.appInfo,
        isRefreshing = appInfoUiState.isRefreshing,
        seedColor = appInfoUiState.seedColor,
        topAppBarUiState = topAppBarUiState,
        componentListUiState = componentListUiState,
        tabState = tabState,
        onBackClick = onBackClick,
        navigateToComponentDetail = navigateToComponentDetail,
        navigateToRuleDetail = navigateToRuleDetail,
        navigateToComponentSortScreen = navigateToComponentSortScreen,
        onLaunchAppClick = onLaunchAppClick,
        switchTab = switchTab,
        modifier = modifier,
        onSearchTextChange = onSearchTextChange,
        onSearchModeChange = onSearchModeChange,
        enableAllComponentsInPage = enableAllComponentsInPage,
        blockAllComponentsInPage = blockAllComponentsInPage,
        onShowAppInfoClick = onShowAppInfoClick,
        onExportRules = onExportRules,
        onImportRules = onImportRules,
        onExportIfw = onExportIfw,
        onImportIfw = onImportIfw,
        onResetIfw = onResetIfw,
        onSwitchClick = onSwitchClick,
        onStopServiceClick = onStopServiceClick,
        onLaunchActivityClick = onLaunchActivityClick,
        onCopyNameClick = onCopyNameClick,
        onCopyFullNameClick = onCopyFullNameClick,
        updateIconThemingState = updateIconThemingState,
        onSelectAll = onSelectAll,
        blockAllSelectedComponents = blockAllSelectedComponents,
        enableAllSelectedComponents = enableAllSelectedComponents,
        switchSelectedMode = switchSelectedMode,
        onSelect = onSelect,
        onDeselect = onDeselect,
        blockAllInItem = blockAllInItem,
        enableAllInItem = enableAllInItem,
        shareAppRule = shareAppRule,
        shareAllRules = shareAllRules,
        onRefresh = onRefresh,
        showOpenInLibChecker = appInfoUiState.showOpenInLibChecker,
        matchedGeneralRuleUiState = appInfoUiState.matchedRuleUiState,
        showBackButton = showBackButton,
    )
    TrackScreenViewEvent(screenName = "AppDetailScreen")
}

@Composable
fun AppDetailContent(
    app: AppItem,
    tabState: TabState<AppDetailTabs>,
    componentListUiState: Result<ComponentSearchResult>,
    onBackClick: () -> Unit,
    onLaunchAppClick: (String) -> Unit,
    switchTab: (AppDetailTabs) -> Unit,
    navigateToComponentSortScreen: () -> Unit,
    updateIconThemingState: (IconThemingState) -> Unit,
    topAppBarUiState: AppBarUiState,
    modifier: Modifier = Modifier,
    seedColor: Color? = null,
    isRefreshing: Boolean = false,
    showBackButton: Boolean = true,
    navigateToComponentDetail: (String) -> Unit = {},
    navigateToRuleDetail: (String) -> Unit = {},
    onShowAppInfoClick: () -> Unit = {},
    onSearchTextChange: (String) -> Unit = {},
    onSearchModeChange: (Boolean) -> Unit = {},
    blockAllComponentsInPage: () -> Unit = {},
    enableAllComponentsInPage: () -> Unit = {},
    onExportRules: (String) -> Unit = {},
    onImportRules: (String) -> Unit = {},
    onExportIfw: (String) -> Unit = {},
    onImportIfw: (String) -> Unit = {},
    onResetIfw: (String) -> Unit = {},
    onSwitchClick: (ComponentInfo, Boolean) -> Unit = { _, _ -> },
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
    onSelectAll: () -> Unit = {},
    blockAllSelectedComponents: () -> Unit = {},
    enableAllSelectedComponents: () -> Unit = {},
    switchSelectedMode: (Boolean) -> Unit = {},
    onSelect: (ComponentInfo) -> Unit = {},
    onDeselect: (ComponentInfo) -> Unit = {},
    blockAllInItem: (List<ComponentInfo>) -> Unit = {},
    enableAllInItem: (List<ComponentInfo>) -> Unit = {},
    shareAppRule: () -> Unit = {},
    shareAllRules: () -> Unit = {},
    onRefresh: () -> Unit = {},
    showOpenInLibChecker: Boolean = false,
    matchedGeneralRuleUiState: Result<List<MatchedItem>> = Loading,
) {
    val listState = rememberLazyListState()
    val systemStatusHeight = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val toolbarHeightRange = with(LocalDensity.current) {
        MinToolbarHeight.roundToPx() + systemStatusHeight.roundToPx()..MaxToolbarHeight.roundToPx() + systemStatusHeight.roundToPx()
    }
    val toolbarState = rememberToolbarState(toolbarHeightRange)
    val scope = rememberCoroutineScope()
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                toolbarState.scrollTopLimitReached =
                    listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
                toolbarState.scrollOffset -= available.y
                return Offset(0f, toolbarState.consumed)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0) {
                    scope.launch {
                        animateDecay(
                            initialValue = toolbarState.height + toolbarState.offset,
                            initialVelocity = available.y,
                            animationSpec = FloatExponentialDecaySpec(),
                        ) { value, _ ->
                            toolbarState.scrollTopLimitReached =
                                listState.firstVisibleItemIndex == 0 &&
                                    listState.firstVisibleItemScrollOffset == 0
                            toolbarState.scrollOffset -= (value - (toolbarState.height + toolbarState.offset))
                            if (toolbarState.scrollOffset == 0f) scope.coroutineContext.cancelChildren()
                        }
                    }
                }
                return super.onPostFling(consumed, available)
            }
        }
    }
    updateIconThemingState(IconThemingState(seedColor = seedColor))
    Scaffold(
        topBar = {
            TopAppBar(
                isSelectedMode = topAppBarUiState.isSelectedMode,
                isSearchMode = topAppBarUiState.isSearchMode,
                app = app,
                topAppBarUiState = topAppBarUiState,
                toolbarState = toolbarState,
                onSearchTextChange = onSearchTextChange,
                onSearchModeChange = onSearchModeChange,
                blockAllComponentsInPage = blockAllComponentsInPage,
                enableAllComponentsInPage = enableAllComponentsInPage,
                navigateToComponentSortScreen = navigateToComponentSortScreen,
                onLaunchAppClick = onLaunchAppClick,
                onSelectAll = onSelectAll,
                blockAllSelectedComponents = blockAllSelectedComponents,
                enableAllSelectedComponents = enableAllSelectedComponents,
                switchSelectedMode = switchSelectedMode,
                onBackClick = onBackClick,
                shareAppRule = shareAppRule,
                shareAllRules = shareAllRules,
                showBackButton = showBackButton,
            )
        },
        modifier = modifier.nestedScroll(nestedScrollConnection),
    ) { innerPadding ->
        AppDetailTabContent(
            app = app,
            componentListUiState = componentListUiState,
            selectedComponentList = topAppBarUiState.selectedComponentList,
            isSelectedMode = topAppBarUiState.isSelectedMode,
            tabState = tabState,
            switchTab = switchTab,
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { scope.coroutineContext.cancelChildren() },
                    )
                },
            navigateToComponentDetail = navigateToComponentDetail,
            navigateToRuleDetail = navigateToRuleDetail,
            onShowAppInfoClick = onShowAppInfoClick,
            onExportRules = onExportRules,
            onImportRules = onImportRules,
            onExportIfw = onExportIfw,
            onImportIfw = onImportIfw,
            onResetIfw = onResetIfw,
            onSwitchClick = onSwitchClick,
            onStopServiceClick = onStopServiceClick,
            onLaunchActivityClick = onLaunchActivityClick,
            onCopyNameClick = onCopyNameClick,
            onCopyFullNameClick = onCopyFullNameClick,
            onBlockAllInItemClick = blockAllInItem,
            onEnableAllInItemClick = enableAllInItem,
            onSelect = onSelect,
            onDeselect = onDeselect,
            onRefresh = onRefresh,
            isRefreshing = isRefreshing,
            showOpenInLibChecker = showOpenInLibChecker,
            matchedGeneralRuleUiState = matchedGeneralRuleUiState,
        )
    }
}

@Composable
fun AppDetailAppBarActions(
    appBarUiState: AppBarUiState,
    modifier: Modifier = Modifier,
    onSearchTextChange: (String) -> Unit = {},
    onSearchModeChange: (Boolean) -> Unit = {},
    blockAllComponentsInPage: () -> Unit = {},
    enableAllComponentsInPage: () -> Unit = {},
    navigateToComponentSortScreen: () -> Unit = {},
    switchSelectedMode: (Boolean) -> Unit = {},
    shareAppRule: () -> Unit = {},
    shareAllRules: () -> Unit = {},
) {
    val actions = appBarUiState.actions
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (actions.contains(SEARCH)) {
            if (appBarUiState.isSearchMode) {
                BlockerSearchTextField(
                    modifier = Modifier.weight(1f),
                    searchQuery = appBarUiState.keyword,
                    onSearchQueryChange = onSearchTextChange,
                    onSearchTrigger = onSearchTextChange,
                    placeholder = {
                        Text(
                            text = stringResource(id = string.feature_appdetail_search_components),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            } else {
                SearchActionMenu(onSearchModeChange = onSearchModeChange)
            }
        }
        if (actions.contains(SHARE_RULE)) {
            ShareAction(
                shareAppRule = shareAppRule,
                shareAllRules = shareAllRules,
            )
        }
        if (actions.contains(MORE)) {
            MoreActionMenu(
                blockAllComponents = blockAllComponentsInPage,
                enableAllComponents = enableAllComponentsInPage,
                onAdvanceSortClick = navigateToComponentSortScreen,
                switchSelectedMode = switchSelectedMode,
            )
        }
    }
}

@Composable
private fun rememberToolbarState(toolbarHeightRange: IntRange): ToolbarState =
    rememberSaveable(saver = ExitUntilCollapsedState.Saver) {
        ExitUntilCollapsedState(heightRange = toolbarHeightRange)
    }

@Composable
private fun TopAppBar(
    isSelectedMode: Boolean,
    isSearchMode: Boolean,
    app: AppItem,
    topAppBarUiState: AppBarUiState,
    toolbarState: ToolbarState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    onSearchTextChange: (String) -> Unit = {},
    onSearchModeChange: (Boolean) -> Unit = {},
    blockAllComponentsInPage: () -> Unit = {},
    enableAllComponentsInPage: () -> Unit = {},
    navigateToComponentSortScreen: () -> Unit = {},
    onLaunchAppClick: (String) -> Unit = {},
    onSelectAll: () -> Unit = {},
    blockAllSelectedComponents: () -> Unit = {},
    enableAllSelectedComponents: () -> Unit = {},
    switchSelectedMode: (Boolean) -> Unit = {},
    shareAppRule: () -> Unit = {},
    shareAllRules: () -> Unit = {},
) {
    if (!isSelectedMode) {
        BlockerCollapsingTopAppBar(
            showBackButton = showBackButton,
            progress = toolbarState.progress,
            onNavigationClick = {
                if (isSearchMode) {
                    onSearchModeChange(false)
                } else {
                    onBackClick()
                }
            },
            title = app.label,
            actions = {
                AppDetailAppBarActions(
                    appBarUiState = topAppBarUiState,
                    onSearchTextChange = onSearchTextChange,
                    onSearchModeChange = onSearchModeChange,
                    blockAllComponentsInPage = blockAllComponentsInPage,
                    enableAllComponentsInPage = enableAllComponentsInPage,
                    navigateToComponentSortScreen = navigateToComponentSortScreen,
                    switchSelectedMode = switchSelectedMode,
                    shareAppRule = shareAppRule,
                    shareAllRules = shareAllRules,
                )
            },
            subtitle = app.packageName,
            summary = stringResource(
                id = string.feature_appdetail_data_with_explanation,
                app.versionName,
                app.versionCode,
            ),
            iconSource = app.packageInfo,
            onIconClick = { onLaunchAppClick(app.packageName) },
            modifier = modifier
                .fillMaxWidth()
                .height(with(LocalDensity.current) { toolbarState.height.toDp() }),
        )
    } else {
        SelectedAppTopBar(
            title = R.plurals.feature_appdetail_selected_component_count,
            selectedItemCount = topAppBarUiState.selectedComponentList.size,
            selectedComponentCount = topAppBarUiState.selectedComponentList.size,
            onNavigationClick = { switchSelectedMode(false) },
            onSelectAll = onSelectAll,
            onBlockAllSelectedComponents = blockAllSelectedComponents,
            onEnableAllSelectedComponents = enableAllSelectedComponents,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppDetailTabContent(
    app: AppItem,
    componentListUiState: Result<ComponentSearchResult>,
    tabState: TabState<AppDetailTabs>,
    switchTab: (AppDetailTabs) -> Unit,
    modifier: Modifier = Modifier,
    selectedComponentList: List<ComponentInfo> = emptyList(),
    isSelectedMode: Boolean = false,
    navigateToComponentDetail: (String) -> Unit = {},
    navigateToRuleDetail: (String) -> Unit = {},
    onShowAppInfoClick: () -> Unit = {},
    onExportRules: (String) -> Unit = {},
    onImportRules: (String) -> Unit = {},
    onExportIfw: (String) -> Unit = {},
    onImportIfw: (String) -> Unit = {},
    onResetIfw: (String) -> Unit = {},
    onSwitchClick: (ComponentInfo, Boolean) -> Unit = { _, _ -> },
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
    onSelect: (ComponentInfo) -> Unit = {},
    onDeselect: (ComponentInfo) -> Unit = {},
    onBlockAllInItemClick: (List<ComponentInfo>) -> Unit = { _ -> },
    onEnableAllInItemClick: (List<ComponentInfo>) -> Unit = { _ -> },
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    showOpenInLibChecker: Boolean = false,
    matchedGeneralRuleUiState: Result<List<MatchedItem>> = Loading,
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = tabState.currentIndex) { tabState.items.size }
    LaunchedEffect(tabState) {
        pagerState.animateScrollToPage(tabState.currentIndex)
    }
    LaunchedEffect(pagerState.targetPage) {
        switchTab(tabState.items[pagerState.targetPage])
    }

    Column(
        modifier = modifier,
    ) {
        BlockerScrollableTabRow(
            selectedTabIndex = tabState.currentIndex,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            tabState.items.forEachIndexed { index, tabItem ->
                BlockerTab(
                    selected = index == pagerState.currentPage,
                    onClick = {
                        switchTab(tabItem)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Text(
                            text = stringResource(
                                id = tabItem.title,
                                tabState.itemCount[tabItem] ?: 0,
                            ),
                        )
                    },
                )
            }
        }
        HorizontalPager(state = pagerState) {
            when (tabState.items[it]) {
                Info -> SummaryContent(
                    app = app,
                    isLibCheckerInstalled = showOpenInLibChecker,
                    onShowAppInfoClick = onShowAppInfoClick,
                    onExportRules = onExportRules,
                    onImportRules = onImportRules,
                    onExportIfw = onExportIfw,
                    onImportIfw = onImportIfw,
                    onResetIfw = onResetIfw,
                )

                Sdk -> SdkContent(
                    data = matchedGeneralRuleUiState,
                    navigateToRuleDetail = navigateToRuleDetail,
                    onStopServiceClick = onStopServiceClick,
                    onLaunchActivityClick = onLaunchActivityClick,
                    onCopyNameClick = onCopyNameClick,
                    onCopyFullNameClick = onCopyFullNameClick,
                    onEnableAllInItemClick = onEnableAllInItemClick,
                    onBlockAllInItemClick = onBlockAllInItemClick,
                    onSwitch = onSwitchClick,
                )

                else -> {
                    when (componentListUiState) {
                        is Loading -> LoadingScreen()
                        is Error -> ErrorScreen(
                            error = componentListUiState.exception.toErrorMessage(),
                        )

                        is Success -> {
                            val componentList = componentListUiState.data
                            val components = when (tabState.items[it]) {
                                Receiver -> componentList.receiver
                                Service -> componentList.service
                                Activity -> componentList.activity
                                Provider -> componentList.provider
                                else -> emptyList()
                            }
                            val refreshingState = rememberPullRefreshState(
                                refreshing = isRefreshing,
                                onRefresh = onRefresh,
                            )
                            Box(
                                modifier = Modifier
                                    .pullRefresh(refreshingState),
                            ) {
                                ComponentList(
                                    components = components,
                                    selectedComponentList = selectedComponentList,
                                    isSelectedMode = isSelectedMode,
                                    navigateToComponentDetail = navigateToComponentDetail,
                                    onSwitchClick = onSwitchClick,
                                    onStopServiceClick = onStopServiceClick,
                                    onLaunchActivityClick = onLaunchActivityClick,
                                    onCopyNameClick = onCopyNameClick,
                                    onCopyFullNameClick = onCopyFullNameClick,
                                    onSelect = onSelect,
                                    onDeselect = onDeselect,
                                )
                                PullRefreshIndicator(
                                    refreshing = isRefreshing,
                                    state = refreshingState,
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    scale = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@PreviewDevices
private fun AppDetailScreenAppInfoPreview(
    @PreviewParameter(AppListPreviewParameterProvider::class)
    appList: List<AppItem>,
) {
    val tabState = AppDetailTabStatePreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            AppDetailScreen(
                appInfoUiState = AppInfoUiState(
                    appInfo = appList[0],
                ),
                componentListUiState = Success(
                    ComponentSearchResult(appList[0]),
                ),
                tabState = tabState[0],
                topAppBarUiState = AppBarUiState(),
            )
        }
    }
}

@Composable
@PreviewDevices
private fun AppDetailScreenComponentPreview(
    @PreviewParameter(AppListPreviewParameterProvider::class)
    appList: List<AppItem>,
) {
    val tabState = AppDetailTabStatePreviewParameterProvider().values.first()
    val components = ComponentListPreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            AppDetailScreen(
                appInfoUiState = AppInfoUiState(appInfo = appList[0]),
                componentListUiState = Success(
                    ComponentSearchResult(
                        app = appList[0],
                        activity = components.filter { it.type == ACTIVITY },
                    ),
                ),
                topAppBarUiState = AppBarUiState(
                    actions = listOf(
                        SEARCH,
                        MORE,
                    ),
                ),
                tabState = tabState[1],
            )
        }
    }
}

@Composable
@Preview
private fun AppDetailScreenComponentEmptyPreview(
    @PreviewParameter(AppListPreviewParameterProvider::class)
    appList: List<AppItem>,
) {
    val tabState = AppDetailTabStatePreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            AppDetailScreen(
                appInfoUiState = AppInfoUiState(
                    appInfo = appList[0],
                    showOpenInLibChecker = true,
                ),
                componentListUiState = Success(
                    ComponentSearchResult(appList[0]),
                ),
                tabState = tabState[2],
                topAppBarUiState = AppBarUiState(),
            )
        }
    }
}

@Composable
@Preview
private fun AppDetailScreenComponentLoadingPreview() {
    val tabState = AppDetailTabStatePreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            AppDetailScreen(
                appInfoUiState = AppInfoUiState(
                    appInfo = appList[0],
                    error = UiMessage("Error"),
                ),
                componentListUiState = Loading,
                tabState = tabState[1],
                topAppBarUiState = AppBarUiState(),
            )
        }
    }
}

@Composable
@Preview
private fun AppDetailScreenComponentRefreshingPreview() {
    val tabState = AppDetailTabStatePreviewParameterProvider().values.first()
    val components = ComponentListPreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            AppDetailScreen(
                appInfoUiState = AppInfoUiState(
                    appInfo = appList[0],
                    isRefreshing = true,
                ),
                componentListUiState = Success(
                    ComponentSearchResult(
                        app = appList[0],
                        activity = components.filter { it.type == ACTIVITY },
                    ),
                ),
                tabState = tabState[1],
                topAppBarUiState = AppBarUiState(),
            )
        }
    }
}

@Composable
@Preview
private fun AppDetailScreenComponentErrorPreview(
    @PreviewParameter(AppListPreviewParameterProvider::class)
    appList: List<AppItem>,
) {
    val tabState = AppDetailTabStatePreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            AppDetailScreen(
                appInfoUiState = AppInfoUiState(
                    appInfo = appList[0],
                    showOpenInLibChecker = true,
                ),
                componentListUiState = Error(
                    Exception("Error"),
                ),
                tabState = tabState[1],
                topAppBarUiState = AppBarUiState(),
            )
        }
    }
}

@Composable
@Preview
private fun AppDetailScreenSdkPreview(
    @PreviewParameter(AppListPreviewParameterProvider::class)
    appList: List<AppItem>,
) {
    val tabState = AppDetailTabStatePreviewParameterProvider().values.first()
    val rule = RuleListPreviewParameterProvider().values.first().first()
    val components = ComponentListPreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            AppDetailScreen(
                appInfoUiState = AppInfoUiState(
                    appInfo = appList[0],
                    showOpenInLibChecker = true,
                    matchedRuleUiState = Success(
                        data = listOf(
                            MatchedItem(
                                header = MatchedHeaderData(
                                    title = rule.name,
                                    uniqueId = rule.id.toString(),
                                ),
                                componentList = components,
                            ),
                        ),
                    ),
                ),
                componentListUiState = Success(
                    ComponentSearchResult(appList[0]),
                ),
                tabState = tabState[3],
                topAppBarUiState = AppBarUiState(),
            )
        }
    }
}

@Composable
@Preview
private fun AppDetailScreenSdkLoadingPreview(
    @PreviewParameter(AppListPreviewParameterProvider::class)
    appList: List<AppItem>,
) {
    val tabState = AppDetailTabStatePreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            AppDetailScreen(
                appInfoUiState = AppInfoUiState(
                    appInfo = appList[0],
                    showOpenInLibChecker = true,
                    matchedRuleUiState = Loading,
                ),
                componentListUiState = Success(
                    ComponentSearchResult(appList[0]),
                ),
                tabState = tabState[3],
                topAppBarUiState = AppBarUiState(),
            )
        }
    }
}

@Composable
@Preview
private fun AppDetailScreenSdkErrorPreview(
    @PreviewParameter(AppListPreviewParameterProvider::class)
    appList: List<AppItem>,
) {
    val tabState = AppDetailTabStatePreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            AppDetailScreen(
                appInfoUiState = AppInfoUiState(
                    appInfo = appList[0],
                    showOpenInLibChecker = true,
                    matchedRuleUiState = Error(
                        Exception("Error"),
                    ),
                ),
                componentListUiState = Success(
                    ComponentSearchResult(appList[0]),
                ),
                tabState = tabState[3],
                topAppBarUiState = AppBarUiState(),
            )
        }
    }
}

@Composable
@PreviewThemes
private fun AppDetailScreenSearchModePreview(
    @PreviewParameter(AppListPreviewParameterProvider::class)
    appList: List<AppItem>,
) {
    val tabState = AppDetailTabStatePreviewParameterProvider().values.first()
    val components = ComponentListPreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            AppDetailScreen(
                appInfoUiState = AppInfoUiState(appInfo = appList[0]),
                componentListUiState = Success(
                    ComponentSearchResult(
                        app = appList[0],
                        activity = components.filter { it.type == ACTIVITY },
                    ),
                ),
                topAppBarUiState = AppBarUiState(
                    actions = listOf(
                        SEARCH,
                        MORE,
                    ),
                    isSearchMode = true,
                ),
                tabState = tabState[1],
            )
        }
    }
}

@Composable
@PreviewThemes
private fun AppDetailScreenSelectedModePreview(
    @PreviewParameter(AppListPreviewParameterProvider::class)
    appList: List<AppItem>,
) {
    val tabState = AppDetailTabStatePreviewParameterProvider().values.first()
    val components = ComponentListPreviewParameterProvider().values.first()
    val activityComponents = components.filter { it.type == ACTIVITY }
    BlockerTheme {
        Surface {
            AppDetailScreen(
                appInfoUiState = AppInfoUiState(appInfo = appList[0]),
                componentListUiState = Success(
                    ComponentSearchResult(
                        app = appList[0],
                        activity = activityComponents,
                    ),
                ),
                topAppBarUiState = AppBarUiState(
                    actions = listOf(
                        SEARCH,
                        MORE,
                    ),
                    isSearchMode = true,
                    isSelectedMode = true,
                    selectedComponentList = listOf(
                        activityComponents[0],
                    ),
                ),
                tabState = tabState[1],
            )
        }
    }
}
