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
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.ktx.themeColorOrNull
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.core.rule.entity.resultMessage
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.core.ui.component.ComponentRow
import com.merxury.blocker.core.ui.control.AccessMessageSnackbar
import com.merxury.blocker.core.ui.control.ControlMessageSnackbar
import com.merxury.blocker.core.ui.control.text
import com.merxury.blocker.feature.appdetail.api.R.string
import com.merxury.blocker.feature.appdetail.impl.bottomsheet.ComponentSortBottomSheetRoute
import com.merxury.blocker.feature.appdetail.impl.componentdetail.ComponentDetailDialog
import com.merxury.blocker.feature.appdetail.impl.ui.ComponentRowActions
import com.merxury.blocker.feature.appdetail.impl.ui.TopBarActions
import com.merxury.blocker.feature.appdetail.impl.ui.needsAccess
import com.merxury.blocker.feature.appdetail.impl.ui.text
import kotlinx.coroutines.launch
import com.merxury.blocker.core.rule.R.string as ruleString
import com.merxury.blocker.core.ui.R.string as uiString

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
    onShowBackgroundActivity: () -> Unit = {},
    showBackButton: Boolean = true,
) {
    val header by headerViewModel.uiState.collectAsStateWithLifecycle()
    val list by listViewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by listViewModel.searchQuery.collectAsStateWithLifecycle()
    val control by controlViewModel.uiState.collectAsStateWithLifecycle()
    KeepFreshWhileStarted(keepFresh = listViewModel::keepFresh, refresh = listViewModel::refresh)
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
        onGrantAccess = listViewModel::grantAccess,
        onRuleWorkConsume = headerViewModel::onRuleWorkShown,
        onShareConsume = headerViewModel::onShareHandled,
    )
    SeedColor(icon = header.app?.icon.takeIf { header.useDynamicColor }, updateIconThemingState = updateIconThemingState)
    val topBarActions = rememberTopBarActions(headerViewModel, listViewModel, controlViewModel, onBackClick, { showSortSheet = true }) {
        val app = header.app
        if (app == null || !context.launchApp(app, header.launch)) {
            scope.launch { snackbarHostState.showSnackbar(cannotLaunch, duration = SnackbarDuration.Short, withDismissAction = true) }
        }
    }
    val rowActions = rememberRowActions(listViewModel, controlViewModel, { detailOf = it.id.className }, navigateToIfwRuleEditor) { text ->
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(text, text))) }
    }
    val actions = rememberAppDetailActions(
        headerViewModel,
        listViewModel,
        controlViewModel,
        navigateToRuleDetail,
        onShowBackgroundActivity,
    ) {
        header.app?.let { context.showInLibChecker(it.packageName) }
    }
    AppDetailContent(
        header = header,
        list = list,
        control = control,
        searchQuery = searchQuery,
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

/**
 * Runs [keepFresh] while the screen is at least started, so nothing is read while the app is in
 * the background, and calls [refresh] each time the screen comes back from there, as the system
 * may have changed the app meanwhile. A screen composed again, such as after a rotation, does not
 * refresh.
 */
@Composable
internal fun KeepFreshWhileStarted(keepFresh: suspend () -> Unit, refresh: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentKeepFresh by rememberUpdatedState(keepFresh)
    val currentRefresh by rememberUpdatedState(refresh)
    LaunchedEffect(lifecycleOwner) {
        var returning = false
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (returning) currentRefresh()
            returning = true
            currentKeepFresh()
        }
    }
}

/**
 * The top bar's actions, one object for the life of the screen. The batch actions act on what the
 * list shows when chosen. Enabling uses the same control goal as an individual switch.
 */
@Composable
private fun rememberTopBarActions(
    headerViewModel: AppHeaderViewModel,
    listViewModel: ComponentListViewModel,
    controlViewModel: ComponentControlViewModel,
    onBackClick: () -> Unit,
    onSortClick: () -> Unit,
    onLaunchApp: () -> Unit,
): TopBarActions {
    val currentBack by rememberUpdatedState(onBackClick)
    val currentSort by rememberUpdatedState(onSortClick)
    val currentLaunch by rememberUpdatedState(onLaunchApp)
    return remember(headerViewModel, listViewModel, controlViewModel) {
        fun page() = listViewModel.uiState.value.componentsOfSelectedPage()
        fun takeSelected() = listViewModel.uiState.value.selected.toList().also { listViewModel.setSelectionMode(false) }
        TopBarActions(
            onBack = { currentBack() },
            onLaunchApp = { currentLaunch() },
            onSearchModeChange = listViewModel::setSearchMode,
            onSearch = listViewModel::search,
            onBlockAllInTab = { controlViewModel.blockAll(page()) },
            onEnableAllInTab = { controlViewModel.enableAll(page()) },
            onSortClick = { currentSort() },
            onSelectionModeChange = listViewModel::setSelectionMode,
            onSelectAll = listViewModel::toggleSelectAllInTab,
            onBlockSelected = { controlViewModel.blockAll(takeSelected()) },
            onEnableSelected = { controlViewModel.enableAll(takeSelected()) },
            onShareAppRules = headerViewModel::shareAppRules,
            onShareAllRules = headerViewModel::shareAllRules,
        )
    }
}

/** The actions of every row, one object for the life of the screen so unchanged rows are skipped. */
@Composable
internal fun rememberRowActions(
    listViewModel: ComponentListViewModel,
    controlViewModel: ComponentControlViewModel,
    showDetail: (ComponentRow) -> Unit,
    navigateToIfwRuleEditor: (ComponentRow) -> Unit,
    copy: (String) -> Unit,
): ComponentRowActions {
    val currentShowDetail by rememberUpdatedState(showDetail)
    val currentEditor by rememberUpdatedState(navigateToIfwRuleEditor)
    val currentCopy by rememberUpdatedState(copy)
    return remember(listViewModel, controlViewModel) {
        ComponentRowActions(
            onSwitch = { row, enabled -> controlViewModel.setEnabled(row.id, enabled) },
            onClick = { currentShowDetail(it) },
            onToggleSelected = { listViewModel.toggleSelected(it.id) },
            onStopService = { listViewModel.stopService(it.id) },
            onLaunchActivity = { listViewModel.launchActivity(it.id) },
            onCopyName = { currentCopy(it.id.simpleName) },
            onCopyFullName = { currentCopy(it.id.className) },
            onEditIfwRule = { currentEditor(it) },
        )
    }
}

@Composable
private fun rememberAppDetailActions(
    headerViewModel: AppHeaderViewModel,
    listViewModel: ComponentListViewModel,
    controlViewModel: ComponentControlViewModel,
    navigateToRuleDetail: (Int) -> Unit,
    onShowBackgroundActivity: () -> Unit,
    onShowInLibChecker: () -> Unit,
): AppDetailActions {
    val currentRuleDetail by rememberUpdatedState(navigateToRuleDetail)
    val currentLibChecker by rememberUpdatedState(onShowInLibChecker)
    val currentBackgroundActivity by rememberUpdatedState(onShowBackgroundActivity)
    return remember(headerViewModel, listViewModel, controlViewModel) {
        AppDetailActions(
            onSelectTab = listViewModel::selectTab,
            onRefresh = listViewModel::refresh,
            onGrantAccess = listViewModel::grantAccess,
            onShowInLibChecker = { currentLibChecker() },
            onShowBackgroundActivity = { currentBackgroundActivity() },
            onExportRules = headerViewModel::exportBlockerRules,
            onImportRules = headerViewModel::importBlockerRules,
            onExportIfw = headerViewModel::exportIfwRules,
            onImportIfw = headerViewModel::importIfwRules,
            onResetIfw = headerViewModel::resetIfw,
            navigateToRuleDetail = { currentRuleDetail(it) },
            onBlockAllInRule = { rule -> controlViewModel.blockAll(rule.rows.map { it.id }, fromMatchedRule = true) },
            onEnableAllInRule = { rule -> controlViewModel.enableAll(rule.rows.map { it.id }, fromMatchedRule = true) },
        )
    }
}

/**
 * Shows each message of the three states for its whole duration, then reports that it was
 * shown, so each is told once. Queued control messages are told apart by their ids, so a message
 * equal to the one shown before is shown too.
 */
@Composable
internal fun ScreenMessages(
    header: AppHeaderUiState,
    list: ComponentListUiState,
    control: ControlUiState,
    snackbarHostState: SnackbarHostState,
    onListMessageConsume: () -> Unit,
    onControlMessageConsume: (Long) -> Unit,
    onGrantAccess: () -> Unit,
    onRuleWorkConsume: () -> Unit,
    onShareConsume: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AccessMessageSnackbar(
        key = list.message,
        text = list.message?.text(),
        needsAccess = list.message?.needsAccess == true,
        snackbarHostState = snackbarHostState,
        onShow = onListMessageConsume,
        onGrantAccess = onGrantAccess,
    )
    ControlMessageSnackbar(
        messages = control.messages,
        snackbarHostState = snackbarHostState,
        onShow = onControlMessageConsume,
        onGrantAccess = onGrantAccess,
    )
    val ruleWork = header.ruleWork
    val ruleWorkText = ruleWork?.let { it.resultMessage(LocalResources.current) ?: stringResource(ruleString.core_rule_processing_please_wait) }
    LaunchedEffect(ruleWork) {
        if (ruleWorkText != null) {
            // The latest result replaces whatever is shown.
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(ruleWorkText, duration = SnackbarDuration.Short, withDismissAction = true)
            onRuleWorkConsume()
        }
    }
    LaunchedEffect(header.sharedRules) {
        val rules = header.sharedRules ?: return@LaunchedEffect
        // Consumed at once so the chooser never opens twice; the error is told outside this effect,
        // which consuming ends.
        val error = context.shareRules(rules)
        onShareConsume()
        if (error != null) scope.launch { snackbarHostState.showSnackbar(error) }
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
