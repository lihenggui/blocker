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

package com.merxury.blocker.feature.ruledetail.impl

import android.content.ClipData
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerCollapsingTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.component.BlockerTabRow
import com.merxury.blocker.core.designsystem.component.MaxToolbarHeight
import com.merxury.blocker.core.designsystem.component.MinToolbarHeight
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.ui.ProcessingProgressSnackbar
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.core.ui.control.ActivityLaunchFailedSnackbar
import com.merxury.blocker.core.ui.control.ControlMessageSnackbar
import com.merxury.blocker.core.ui.rule.RuleDetailTabs
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.core.ui.state.toolbar.ExitUntilCollapsedState
import com.merxury.blocker.core.ui.state.toolbar.ToolbarState
import com.merxury.blocker.feature.ruledetail.RuleDetailSortType
import com.merxury.blocker.feature.ruledetail.impl.RuleInfoUiState.Error
import com.merxury.blocker.feature.ruledetail.impl.component.MatchedAppActions
import com.merxury.blocker.feature.ruledetail.impl.component.RuleDescription
import com.merxury.blocker.feature.ruledetail.impl.component.RuleMatchedAppList
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * Opens the detail screen of an app.
 *
 * @param userId the user whose copy to open; null for the user Blocker runs in
 */
typealias OpenAppDetail = (packageName: String, userId: Int?) -> Unit

/** The user to open [this] app for: null for the user Blocker runs in, [ownUser]. */
internal fun AppKey.userIdFor(ownUser: UserId): Int? = userId.value.takeIf { userId != ownUser }

@Composable
fun RuleDetailScreen(
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    navigateToAppDetail: OpenAppDetail,
    updateIconThemingState: (IconThemingState) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    viewModel: RuleDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() }
    }
    val actions = rememberMatchedAppActions(viewModel, uiState, navigateToAppDetail)
    Box(modifier = modifier.fillMaxSize()) {
        RuleDetailScreen(
            ruleInfoUiState = uiState.ruleInfo,
            showBackButton = showBackButton,
            onBackClick = onBackClick,
            tabState = uiState.tabState,
            switchTab = viewModel::switchTab,
            appBarUiState = uiState.appBarUiState,
            sortType = uiState.sortType,
            onSortTypeChange = viewModel::updateSortType,
            inFlight = uiState.inFlight,
            actions = actions,
            onBlockAllInPageClick = { viewModel.controlAllComponentsInPage(false) },
            onEnableAllInPageClick = { viewModel.controlAllComponentsInPage(true) },
        )
        val bottomInset = WindowInsets.systemBars.asPaddingValues()
            .calculateBottomPadding()
        ProcessingProgressSnackbar(
            progress = uiState.batch,
            onDismiss = {},
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp + bottomInset,
                ),
        )
    }
    ControlMessageSnackbar(
        messages = uiState.messages,
        snackbarHostState = snackbarHostState,
        onShow = viewModel::onMessageShown,
        onGrantAccess = viewModel::grantAccess,
    )
    ActivityLaunchFailedSnackbar(
        failure = uiState.actionFailure,
        snackbarHostState = snackbarHostState,
        onShow = viewModel::onActionFailureShown,
        onGrantAccess = viewModel::grantAccess,
    )
    RuleSeedColor(
        icon = (uiState.ruleInfo as? RuleInfoUiState.Success)?.seedIcon,
        updateIconThemingState = updateIconThemingState,
    )
}

/** The actions of the matched apps, one object for the life of the screen. */
@Composable
private fun rememberMatchedAppActions(
    viewModel: RuleDetailViewModel,
    uiState: RuleDetailUiState,
    navigateToAppDetail: OpenAppDetail,
): MatchedAppActions {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val currentState by rememberUpdatedState(uiState)
    val openApp by rememberUpdatedState(navigateToAppDetail)
    return remember(viewModel) {
        fun copy(text: String) {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(text, text))) }
        }
        MatchedAppActions(
            onSwitch = viewModel::setEnabled,
            onLaunchActivity = viewModel::launchActivity,
            onCopyName = ::copy,
            onCopyFullName = ::copy,
            onBlockAll = { viewModel.controlAllComponents(it, enable = false) },
            onEnableAll = { viewModel.controlAllComponents(it, enable = true) },
            onOpenApp = { app -> openApp(app.packageName, app.userIdFor(currentState.ownUser)) },
        )
    }
}

@Composable
fun RuleDetailScreen(
    ruleInfoUiState: RuleInfoUiState,
    tabState: TabState<RuleDetailTabs>,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    appBarUiState: AppBarUiState = AppBarUiState(),
    sortType: RuleDetailSortType = RuleDetailSortType.NAME,
    onSortTypeChange: (RuleDetailSortType) -> Unit = {},
    onBackClick: () -> Unit = {},
    switchTab: (RuleDetailTabs) -> Unit = { _ -> },
    inFlight: Set<ComponentId> = emptySet(),
    actions: MatchedAppActions = MatchedAppActions(),
    onBlockAllInPageClick: () -> Unit = { },
    onEnableAllInPageClick: () -> Unit = { },
) {
    when (ruleInfoUiState) {
        RuleInfoUiState.Loading -> {
            LoadingScreen()
        }

        is RuleInfoUiState.Success -> {
            RuleDetailContent(
                modifier = modifier,
                showBackButton = showBackButton,
                ruleInfoUiState = ruleInfoUiState,
                onBackClick = onBackClick,
                appBarUiState = appBarUiState,
                sortType = sortType,
                onSortTypeChange = onSortTypeChange,
                tabState = tabState,
                switchTab = switchTab,
                inFlight = inFlight,
                actions = actions,
                onBlockAllInPageClick = onBlockAllInPageClick,
                onEnableAllInPageClick = onEnableAllInPageClick,
            )
        }

        is Error -> {
            ErrorScreen(error = ruleInfoUiState.error)
        }
    }
    TrackScreenViewEvent(screenName = "RuleDetailScreen")
}

@Composable
fun RuleDetailContent(
    tabState: TabState<RuleDetailTabs>,
    switchTab: (RuleDetailTabs) -> Unit,
    ruleInfoUiState: RuleInfoUiState.Success,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    appBarUiState: AppBarUiState = AppBarUiState(),
    sortType: RuleDetailSortType = RuleDetailSortType.NAME,
    onSortTypeChange: (RuleDetailSortType) -> Unit = {},
    inFlight: Set<ComponentId> = emptySet(),
    actions: MatchedAppActions = MatchedAppActions(),
    onBlockAllInPageClick: () -> Unit = { },
    onEnableAllInPageClick: () -> Unit = { },
) {
    val listState = rememberLazyListState()
    val systemStatusHeight = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val toolbarHeightRange = with(LocalDensity.current) {
        MinToolbarHeight.roundToPx() + systemStatusHeight.roundToPx()..MaxToolbarHeight.roundToPx() + systemStatusHeight.roundToPx()
    }
    val toolbarState = rememberToolbarState(toolbarHeightRange)
    val nestedScrollConnection = rememberToolbarScrollConnection(toolbarState, listState)
    Scaffold(
        topBar = {
            BlockerCollapsingTopAppBar(
                showBackButton = showBackButton,
                progress = toolbarState.progress,
                onNavigationClick = onBackClick,
                title = ruleInfoUiState.ruleInfo.name,
                subtitle = ruleInfoUiState.ruleInfo.company.toString(),
                summary = "",
                iconSource = ruleInfoUiState.ruleInfo.iconUrl,
                onIconClick = { },
                actions = {
                    RuleDetailAppBarActions(
                        appBarUiState = appBarUiState,
                        sortType = sortType,
                        onSortTypeChange = onSortTypeChange,
                        blockAllComponents = onBlockAllInPageClick,
                        enableAllComponents = onEnableAllInPageClick,
                        batchActionsEnabled = ruleInfoUiState.batchActionsEnabled,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { toolbarState.height.toDp() }),
            )
        },
        modifier = modifier.nestedScroll(nestedScrollConnection),
    ) { innerPadding ->
        RuleDetailTabContent(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            ruleInfoUiState = ruleInfoUiState,
            tabState = tabState,
            switchTab = switchTab,
            inFlight = inFlight,
            actions = actions,
        )
    }
}

/** Collapses and expands the toolbar with the scroll of [listState]. */
@Composable
private fun rememberToolbarScrollConnection(toolbarState: ToolbarState, listState: LazyListState): NestedScrollConnection {
    val scope = rememberCoroutineScope()
    return remember {
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
}

@Composable
private fun rememberToolbarState(toolbarHeightRange: IntRange): ToolbarState = rememberSaveable(saver = ExitUntilCollapsedState.Saver) {
    ExitUntilCollapsedState(heightRange = toolbarHeightRange)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RuleDetailTabContent(
    ruleInfoUiState: RuleInfoUiState.Success,
    tabState: TabState<RuleDetailTabs>,
    switchTab: (RuleDetailTabs) -> Unit,
    modifier: Modifier = Modifier,
    inFlight: Set<ComponentId> = emptySet(),
    actions: MatchedAppActions = MatchedAppActions(),
) {
    val pagerState = rememberPagerState(initialPage = tabState.currentIndex) { tabState.items.size }
    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = modifier,
    ) {
        BlockerTabRow(
            selectedTabIndex = pagerState.currentPage,
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
                            ),
                        )
                    },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
        ) {
            when (it) {
                0 -> RuleMatchedAppList(
                    ruleMatchedAppListUiState = ruleInfoUiState.matchedAppsUiState,
                    inFlight = inFlight,
                    actions = actions,
                )

                1 -> RuleDescription(rule = ruleInfoUiState.ruleInfo)
            }
        }
    }
}
