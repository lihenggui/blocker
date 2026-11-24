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

package com.merxury.blocker.feature.appdetail.impl

import android.content.ClipData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.ktx.themeColorOrNull
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.rule.entity.resultMessage
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.feature.appdetail.api.R.string
import com.merxury.blocker.feature.appdetail.impl.bottomsheet.ComponentSortBottomSheetRoute
import com.merxury.blocker.feature.appdetail.impl.componentdetail.ComponentDetailDialog
import com.merxury.blocker.feature.appdetail.impl.ui.ComponentRowActions
import com.merxury.blocker.feature.appdetail.impl.ui.TopBarActions
import com.merxury.blocker.feature.appdetail.impl.ui.text
import kotlinx.coroutines.launch
import com.merxury.blocker.core.rule.R.string as ruleString

/**
 * The detail screen of one app for one user. Rows show what the repositories hold; the switches
 * and batch actions go to [controlViewModel], which reports how each change ended.
 *
 * @param navigateToIfwRuleEditor opens the rule editor of a component of this app
 */
@Composable
fun AppDetailScreen(
    headerViewModel: AppHeaderViewModel,
    listViewModel: ComponentListViewModel,
    controlViewModel: ComponentControlViewModel,
    snackbarHostState: SnackbarHostState,
    updateIconThemingState: (IconThemingState) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    navigateToRuleDetail: (Int) -> Unit = {},
    navigateToIfwRuleEditor: (ComponentRow) -> Unit = {},
    showBackButton: Boolean = true,
) {
    val header by headerViewModel.uiState.collectAsStateWithLifecycle()
    val list by listViewModel.uiState.collectAsStateWithLifecycle()
    val control by controlViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(listViewModel) { listViewModel.keepFresh() }
    var detailOf by rememberSaveable { mutableStateOf<String?>(null) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val cannotLaunch = stringResource(string.feature_appdetail_api_cannot_launch_this_app)
    ScreenMessages(
        header = header,
        list = list,
        control = control,
        snackbarHostState = snackbarHostState,
        onListMessageConsume = listViewModel::onMessageShown,
        onControlMessageConsume = controlViewModel::onMessageShown,
        onRuleWorkConsume = headerViewModel::onRuleWorkShown,
        onShareConsume = headerViewModel::onShareHandled,
    )
    SeedColor(icon = header.app?.icon.takeIf { header.useDynamicColor }, updateIconThemingState = updateIconThemingState)
    val topBarActions = topBarActions(header, list, onBackClick, { showSortSheet = true }, headerViewModel, listViewModel, controlViewModel) {
        val app = header.app
        if (app == null || !context.launchApp(app, header.launch)) {
            scope.launch { snackbarHostState.showSnackbar(cannotLaunch, duration = SnackbarDuration.Short, withDismissAction = true) }
        }
    }
    val rowActions = rowActions({ detailOf = it.id.className }, navigateToIfwRuleEditor, listViewModel, controlViewModel) { text ->
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(text, text))) }
    }
    val actions = appDetailActions(navigateToRuleDetail, headerViewModel, listViewModel, controlViewModel) {
        header.app?.let { context.showInLibChecker(it.packageName) }
    }
    AppDetailContent(
        header = header,
        list = list,
        control = control,
        modifier = modifier,
        showBackButton = showBackButton,
        topBarActions = topBarActions,
        rowActions = rowActions,
        actions = actions,
    )
    detailOf?.let { name -> ComponentDetailDialog(componentName = name, dismissHandler = { detailOf = null }) }
    if (showSortSheet) {
        ComponentSortBottomSheetRoute(dismissHandler = { showSortSheet = false })
    }
    TrackScreenViewEvent(screenName = "AppDetailScreen")
}

private fun topBarActions(
    header: AppHeaderUiState,
    list: ComponentListUiState,
    onBackClick: () -> Unit,
    onSortClick: () -> Unit,
    headerViewModel: AppHeaderViewModel,
    listViewModel: ComponentListViewModel,
    controlViewModel: ComponentControlViewModel,
    onLaunchApp: () -> Unit,
): TopBarActions {
    val tabRows = (list.components as? ComponentsUiState.Loaded)?.components?.of(list.tabs.selectedItem).orEmpty()
    fun controlSelected(goal: ControlGoal) {
        controlViewModel.controlAll(list.selected.toList(), goal)
        listViewModel.setSelectionMode(false)
    }
    return TopBarActions(
        onBack = onBackClick,
        onLaunchApp = onLaunchApp,
        onSearchModeChange = listViewModel::setSearchMode,
        onSearch = listViewModel::search,
        onBlockAllInTab = { controlViewModel.controlAll(tabRows.map { it.id }, ControlGoal.BLOCK) },
        onEnableAllInTab = { controlViewModel.controlAll(tabRows.map { it.id }, ControlGoal.ENABLE) },
        onSortClick = onSortClick,
        onSelectionModeChange = listViewModel::setSelectionMode,
        onSelectAll = listViewModel::toggleSelectAllInTab,
        onBlockSelected = { controlSelected(ControlGoal.BLOCK) },
        onEnableSelected = { controlSelected(ControlGoal.ENABLE) },
        onShareAppRules = headerViewModel::shareAppRules,
        onShareAllRules = headerViewModel::shareAllRules,
    )
}

private fun rowActions(
    showDetail: (ComponentRow) -> Unit,
    navigateToIfwRuleEditor: (ComponentRow) -> Unit,
    listViewModel: ComponentListViewModel,
    controlViewModel: ComponentControlViewModel,
    copy: (String) -> Unit,
) = ComponentRowActions(
    onSwitch = { row, enabled -> controlViewModel.setEnabled(row.id, enabled) },
    onControl = { row, goal -> controlViewModel.control(row.id, goal) },
    onClick = showDetail,
    onToggleSelected = { listViewModel.toggleSelected(it.id) },
    onStopService = { listViewModel.stopService(it.id) },
    onLaunchActivity = { listViewModel.launchActivity(it.id) },
    onCopyName = { copy(it.id.simpleName) },
    onCopyFullName = { copy(it.id.className) },
    onEditIfwRule = navigateToIfwRuleEditor,
)

private fun appDetailActions(
    navigateToRuleDetail: (Int) -> Unit,
    headerViewModel: AppHeaderViewModel,
    listViewModel: ComponentListViewModel,
    controlViewModel: ComponentControlViewModel,
    onShowInLibChecker: () -> Unit,
) = AppDetailActions(
    onSelectTab = listViewModel::selectTab,
    onRefresh = listViewModel::refresh,
    onGrantAccess = listViewModel::grantAccess,
    onShowInLibChecker = onShowInLibChecker,
    onExportRules = headerViewModel::exportBlockerRules,
    onImportRules = headerViewModel::importBlockerRules,
    onExportIfw = headerViewModel::exportIfwRules,
    onImportIfw = headerViewModel::importIfwRules,
    onResetIfw = headerViewModel::resetIfw,
    navigateToRuleDetail = navigateToRuleDetail,
    onBlockAllInRule = { rule -> controlViewModel.controlAll(rule.rows.map { it.id }, ControlGoal.BLOCK, fromMatchedRule = true) },
    onEnableAllInRule = { rule -> controlViewModel.controlAll(rule.rows.map { it.id }, ControlGoal.ENABLE, fromMatchedRule = true) },
)

/** Shows each message of the three states once, then reports that it was shown. */
@Composable
private fun ScreenMessages(
    header: AppHeaderUiState,
    list: ComponentListUiState,
    control: ControlUiState,
    snackbarHostState: SnackbarHostState,
    onListMessageConsume: () -> Unit,
    onControlMessageConsume: (ControlMessage) -> Unit,
    onRuleWorkConsume: () -> Unit,
    onShareConsume: () -> Unit,
) {
    val context = LocalContext.current
    val listMessage = list.message?.text()
    LaunchedEffect(list.message) {
        if (listMessage != null) {
            snackbarHostState.showSnackbar(listMessage, duration = SnackbarDuration.Long, withDismissAction = true)
            onListMessageConsume()
        }
    }
    val controlMessage = control.messages.firstOrNull()
    val controlText = controlMessage?.text()
    LaunchedEffect(controlMessage) {
        if (controlMessage != null && controlText != null) {
            snackbarHostState.showSnackbar(controlText, duration = SnackbarDuration.Long, withDismissAction = true)
            onControlMessageConsume(controlMessage)
        }
    }
    val ruleWork = header.ruleWork
    val ruleWorkText = ruleWork?.let { it.resultMessage(LocalResources.current) ?: stringResource(ruleString.core_rule_processing_please_wait) }
    LaunchedEffect(ruleWork) {
        if (ruleWorkText != null) {
            onRuleWorkConsume()
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(ruleWorkText, duration = SnackbarDuration.Short, withDismissAction = true)
        }
    }
    LaunchedEffect(header.sharedRules) {
        val rules = header.sharedRules ?: return@LaunchedEffect
        onShareConsume()
        context.shareRules(rules)?.let { snackbarHostState.showSnackbar(it) }
    }
}

/** Themes the screen after the app's icon while the user chose dynamic colours; [icon] is null otherwise. */
@Composable
private fun SeedColor(icon: ApkIcon?, updateIconThemingState: (IconThemingState) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(icon) {
        if (icon == null) {
            updateIconThemingState(IconThemingState())
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context).data(icon).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
        updateIconThemingState(IconThemingState(seedColor = result?.image?.toBitmap()?.asImageBitmap()?.themeColorOrNull()))
    }
    DisposableEffect(Unit) {
        onDispose { updateIconThemingState(IconThemingState()) }
    }
}
