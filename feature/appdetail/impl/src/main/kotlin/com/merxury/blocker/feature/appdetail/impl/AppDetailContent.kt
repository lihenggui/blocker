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

package com.merxury.blocker.feature.appdetail.impl

import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerButton
import com.merxury.blocker.core.designsystem.component.BlockerScrollableTabRow
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.component.MaxToolbarHeight
import com.merxury.blocker.core.designsystem.component.MinToolbarHeight
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.ProcessingProgress
import com.merxury.blocker.core.ui.ProcessingProgressSnackbar
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.core.ui.state.toolbar.ExitUntilCollapsedState
import com.merxury.blocker.core.ui.state.toolbar.ToolbarState
import com.merxury.blocker.feature.appdetail.api.R.string
import com.merxury.blocker.feature.appdetail.impl.sdk.SdkContent
import com.merxury.blocker.feature.appdetail.impl.summary.SummaryContent
import com.merxury.blocker.feature.appdetail.impl.ui.AppDetailTopBar
import com.merxury.blocker.feature.appdetail.impl.ui.ComponentRowActions
import com.merxury.blocker.feature.appdetail.impl.ui.ComponentTabContent
import com.merxury.blocker.feature.appdetail.impl.ui.TopBarActions
import com.merxury.blocker.feature.appdetail.impl.ui.text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import com.merxury.blocker.core.ui.R.string as uiString

/** What the app info tab and the matched rules can ask for, apart from the rows. */
class AppDetailActions(
    val onSelectTab: (AppDetailTabs) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onGrantAccess: () -> Unit = {},
    val onShowInLibChecker: () -> Unit = {},
    val onShowBackgroundActivity: () -> Unit = {},
    val onExportRules: () -> Unit = {},
    val onImportRules: () -> Unit = {},
    val onExportIfw: () -> Unit = {},
    val onImportIfw: () -> Unit = {},
    val onResetIfw: () -> Unit = {},
    val navigateToRuleDetail: (Int) -> Unit = {},
    val onBlockAllInRule: (MatchedRule) -> Unit = {},
    val onEnableAllInRule: (MatchedRule) -> Unit = {},
)

/**
 * The detail screen of one app for one user, drawn from the three view models' states: the
 * header, the component lists, and the changes in flight.
 */
@Composable
internal fun AppDetailContent(
    header: AppHeaderUiState,
    list: ComponentListUiState,
    control: ControlUiState,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    searchQuery: String? = null,
    topBarActions: TopBarActions = TopBarActions(),
    rowActions: ComponentRowActions = ComponentRowActions(),
    actions: AppDetailActions = AppDetailActions(),
) {
    val systemStatusHeight = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val toolbarHeightRange = with(LocalDensity.current) {
        MinToolbarHeight.roundToPx() + systemStatusHeight.roundToPx()..MaxToolbarHeight.roundToPx() + systemStatusHeight.roundToPx()
    }
    val toolbarState = rememberSaveable(saver = ExitUntilCollapsedState.Saver) { ExitUntilCollapsedState(heightRange = toolbarHeightRange) }
    val scope = rememberCoroutineScope()
    val nestedScrollConnection = remember(toolbarState) { CollapsingToolbarConnection(toolbarState, scope) }
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                AppDetailTopBar(
                    header = header,
                    list = list,
                    searchQuery = searchQuery,
                    toolbarState = toolbarState,
                    actions = topBarActions,
                    showBackButton = showBackButton,
                    batchRunning = !control.batchActionsEnabled,
                )
            },
            modifier = Modifier.nestedScroll(nestedScrollConnection),
        ) { innerPadding ->
            val contentModifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onPress = { scope.coroutineContext.cancelChildren() }) }
            val app = header.app
            if (app == null) {
                // Not cached for the user: the header or the first refresh is still loading, or
                // nothing could be read, perhaps because the app is not installed for the user.
                AppNotShown(list = list, onGrantAccess = actions.onGrantAccess, modifier = contentModifier)
            } else {
                AppDetailTabs(
                    app = app,
                    header = header,
                    list = list,
                    inFlight = control.inFlight,
                    batchRunning = !control.batchActionsEnabled,
                    rowActions = rowActions,
                    actions = actions,
                    modifier = contentModifier,
                )
            }
        }
        val bottomInset = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        ProcessingProgressSnackbar(
            progress = control.batch?.let {
                ProcessingProgress(current = it.done ?: 0, total = it.total, isEnabling = it.goal != ControlGoal.BLOCK, isIndeterminate = it.done == null)
            },
            onDismiss = {},
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp + bottomInset),
        )
    }
}

/**
 * What shows while the app is not cached: loading, or why it could not be read. Where access to
 * root or Shizuku would let it be read, access can be granted from here, since without the app
 * there is nothing to act on.
 */
@Composable
private fun AppNotShown(list: ComponentListUiState, onGrantAccess: () -> Unit, modifier: Modifier = Modifier) {
    val failure = list.refreshFailure
    val text = when {
        failure != null -> failure.text()
        list.accessCheckFailed -> stringResource(string.feature_appdetail_api_access_not_checked)
        else -> null
    }
    if (text == null) {
        LoadingScreen(modifier = modifier)
        return
    }
    val accessWouldHelp = list.accessCheckFailed || failure is ReadFailure.UserNotReachable || failure == ReadFailure.ChannelUnavailable
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ErrorScreen(error = UiMessage(text), modifier = Modifier.weight(1f))
        if (accessWouldHelp) {
            BlockerButton(onClick = onGrantAccess, modifier = Modifier.padding(bottom = 32.dp)) {
                Text(text = stringResource(uiString.core_ui_grant_access))
            }
        }
    }
}

@Composable
private fun AppDetailTabs(
    app: HeaderApp,
    header: AppHeaderUiState,
    list: ComponentListUiState,
    inFlight: Set<ComponentId>,
    batchRunning: Boolean,
    rowActions: ComponentRowActions,
    actions: AppDetailActions,
    modifier: Modifier = Modifier,
) {
    val tabState = list.tabs
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = tabState.currentIndex) { tabState.items.size }
    LaunchedEffect(tabState) { pagerState.animateScrollToPage(tabState.currentIndex) }
    LaunchedEffect(pagerState.targetPage) {
        tabState.items.getOrNull(pagerState.targetPage)?.let(actions.onSelectTab)
    }
    Column(modifier = modifier) {
        BlockerScrollableTabRow(
            selectedTabIndex = tabState.currentIndex,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            tabState.items.forEachIndexed { index, tab ->
                BlockerTab(
                    selected = index == pagerState.currentPage,
                    onClick = {
                        actions.onSelectTab(tab)
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = { Text(text = stringResource(id = tab.title, tabState.itemCount[tab] ?: 0)) },
                )
            }
        }
        HorizontalPager(state = pagerState) { page ->
            when (val tab = tabState.items[page]) {
                AppDetailTabs.Info -> SummaryContent(
                    app = app,
                    isLibCheckerInstalled = header.showLibChecker,
                    onShowAppInfoClick = actions.onShowInLibChecker,
                    showBackgroundActivity = header.launch == LaunchMode.OWN_USER && header.backgroundActivityAvailable,
                    onShowBackgroundActivity = actions.onShowBackgroundActivity,
                    onExportRules = actions.onExportRules,
                    onImportRules = actions.onImportRules,
                    onExportIfw = actions.onExportIfw,
                    onImportIfw = actions.onImportIfw,
                    onResetIfw = actions.onResetIfw,
                )

                AppDetailTabs.Sdk -> SdkContent(
                    data = list.matchedRules,
                    inFlight = inFlight,
                    batchRunning = batchRunning,
                    actions = rowActions,
                    navigateToRuleDetail = actions.navigateToRuleDetail,
                    onBlockAll = actions.onBlockAllInRule,
                    onEnableAll = actions.onEnableAllInRule,
                )

                else -> when (val components = list.components) {
                    ComponentsUiState.Loading -> LoadingScreen()

                    is ComponentsUiState.Loaded -> ComponentTabContent(
                        rows = components.components.of(tab),
                        inFlight = inFlight,
                        isRefreshing = list.isRefreshing,
                        actions = rowActions,
                        isSelectionMode = list.isSelectionMode,
                        selected = list.selected,
                        onRefresh = actions.onRefresh,
                    )
                }
            }
        }
    }
}

/**
 * Collapses the header as the content scrolls up and expands it as it scrolls or flings back
 * down, before the content itself scrolls.
 */
private class CollapsingToolbarConnection(
    private val toolbarState: ToolbarState,
    private val scope: CoroutineScope,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        toolbarState.scrollTopLimitReached = true
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
                    toolbarState.scrollTopLimitReached = true
                    toolbarState.scrollOffset -= (value - (toolbarState.height + toolbarState.offset))
                    if (toolbarState.scrollOffset == 0f) scope.coroutineContext.cancelChildren()
                }
            }
        }
        return super.onPostFling(consumed, available)
    }
}
