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

package com.merxury.blocker.feature.ruledetail

import android.content.ClipData
import android.content.Context
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration.Short
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.BlockerCollapsingTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.component.BlockerTabRow
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.component.MaxToolbarHeight
import com.merxury.blocker.core.designsystem.component.MinToolbarHeight
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.designsystem.theme.IconThemingState
import com.merxury.blocker.core.domain.model.MatchedHeaderData
import com.merxury.blocker.core.domain.model.MatchedItem
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.result.Result.Loading
import com.merxury.blocker.core.result.Result.Success
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.previewparameter.AppListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.ComponentListPreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.RuleDetailTabStatePreviewParameterProvider
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider
import com.merxury.blocker.core.ui.rule.RuleDetailTabs
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.core.ui.state.toolbar.ExitUntilCollapsedState
import com.merxury.blocker.core.ui.state.toolbar.ToolbarState
import com.merxury.blocker.feature.ruledetail.R.string
import com.merxury.blocker.feature.ruledetail.RuleInfoUiState.Error
import com.merxury.blocker.feature.ruledetail.component.RuleDescription
import com.merxury.blocker.feature.ruledetail.component.RuleMatchedAppList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import com.merxury.blocker.core.ui.R.string as uistring

@Composable
fun RuleDetailScreen(
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    navigateToAppDetail: (String) -> Unit,
    updateIconThemingState: (IconThemingState) -> Unit,
    showBackButton: Boolean = true,
    viewModel: RuleDetailViewModel = hiltViewModel(),
) {
    val ruleInfoUiState by viewModel.ruleInfoUiState.collectAsStateWithLifecycle()
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()
    val appBarUiState by viewModel.appBarUiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    RuleDetailScreen(
        ruleInfoUiState = ruleInfoUiState,
        showBackButton = showBackButton,
        onBackClick = onBackClick,
        tabState = tabState,
        switchTab = viewModel::switchTab,
        appBarUiState = appBarUiState,
        onStopServiceClick = viewModel::stopService,
        onLaunchActivityClick = viewModel::launchActivity,
        onCopyNameClick = {
            scope.launch {
                clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText(it, it)))
            }
        },
        onCopyFullNameClick = {
            scope.launch {
                clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText(it, it)))
            }
        },
        onBlockAllInItemClick = {
            handleBlockAllInItemClick(context, viewModel, it, scope, snackbarHostState)
        },
        onEnableAllInItemClick = {
            handleEnableAllInItemClick(viewModel, it, scope, snackbarHostState, context)
        },
        onBlockAllInPageClick = {
            handleBlockAllInPageClick(viewModel, scope, snackbarHostState, context)
        },
        onEnableAllInPageClick = {
            handleEnableAllInPageClick(viewModel, scope, snackbarHostState, context)
        },
        onSwitch = viewModel::controlComponent,
        navigateToAppDetail = navigateToAppDetail,
        updateIconThemingState = updateIconThemingState,
    )
    if (errorState != null) {
        BlockerErrorAlertDialog(
            title = errorState?.title.orEmpty(),
            text = errorState?.content.orEmpty(),
            onDismissRequest = viewModel::dismissAlert,
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            updateIconThemingState(IconThemingState())
        }
    }
}

private fun handleEnableAllInPageClick(
    viewModel: RuleDetailViewModel,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    context: Context,
) {
    viewModel.controlAllComponentsInPage(true) { current, total ->
        showEnableProgress(context, snackbarHostState, scope, current, total)
    }
}

private fun handleBlockAllInPageClick(
    viewModel: RuleDetailViewModel,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    context: Context,
) {
    viewModel.controlAllComponentsInPage(false) { current, total ->
        showDisableProgress(
            context,
            snackbarHostState,
            scope,
            current,
            total,
        )
    }
}

private fun handleEnableAllInItemClick(
    viewModel: RuleDetailViewModel,
    it: List<ComponentInfo>,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    context: Context,
) {
    viewModel.controlAllComponents(it, true) { current, total ->
        showEnableProgress(context, snackbarHostState, scope, current, total)
    }
}

private fun handleBlockAllInItemClick(
    context: Context,
    viewModel: RuleDetailViewModel,
    it: List<ComponentInfo>,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    viewModel.controlAllComponents(it, false) { current, total ->
        showDisableProgress(context, snackbarHostState, scope, current, total)
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

@Composable
fun RuleDetailScreen(
    ruleInfoUiState: RuleInfoUiState,
    tabState: TabState<RuleDetailTabs>,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    appBarUiState: AppBarUiState = AppBarUiState(),
    onBackClick: () -> Unit = {},
    switchTab: (RuleDetailTabs) -> Unit = { _ -> },
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
    onBlockAllInItemClick: (List<ComponentInfo>) -> Unit = { _ -> },
    onEnableAllInItemClick: (List<ComponentInfo>) -> Unit = { _ -> },
    onBlockAllInPageClick: () -> Unit = { },
    onEnableAllInPageClick: () -> Unit = { },
    onSwitch: (ComponentInfo, Boolean) -> Unit = { _, _ -> },
    navigateToAppDetail: (String) -> Unit = { _ -> },
    updateIconThemingState: (IconThemingState) -> Unit = { _ -> },
) {
    when (ruleInfoUiState) {
        RuleInfoUiState.Loading -> {
            LoadingScreen()
        }

        is RuleInfoUiState.Success -> {
            RuleDetailContent(
                modifier = modifier,
                showBackButton = showBackButton,
                ruleMatchedAppListUiState = ruleInfoUiState.matchedAppsUiState,
                ruleInfoUiState = ruleInfoUiState,
                onBackClick = onBackClick,
                appBarUiState = appBarUiState,
                tabState = tabState,
                switchTab = switchTab,
                onStopServiceClick = onStopServiceClick,
                onLaunchActivityClick = onLaunchActivityClick,
                onCopyNameClick = onCopyNameClick,
                onCopyFullNameClick = onCopyFullNameClick,
                onBlockAllInItemClick = onBlockAllInItemClick,
                onEnableAllInItemClick = onEnableAllInItemClick,
                onBlockAllInPageClick = onBlockAllInPageClick,
                onEnableAllInPageClick = onEnableAllInPageClick,
                onSwitch = onSwitch,
                navigateToAppDetail = navigateToAppDetail,
                updateIconThemingState = updateIconThemingState,
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
    ruleMatchedAppListUiState: Result<List<MatchedItem>>,
    ruleInfoUiState: RuleInfoUiState.Success,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    appBarUiState: AppBarUiState = AppBarUiState(),
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
    onBlockAllInItemClick: (List<ComponentInfo>) -> Unit = { _ -> },
    onEnableAllInItemClick: (List<ComponentInfo>) -> Unit = { _ -> },
    onBlockAllInPageClick: () -> Unit = { },
    onEnableAllInPageClick: () -> Unit = { },
    onSwitch: (ComponentInfo, Boolean) -> Unit = { _, _ -> },
    navigateToAppDetail: (String) -> Unit = { _ -> },
    updateIconThemingState: (IconThemingState) -> Unit = { _ -> },
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
    updateIconThemingState(
        IconThemingState(seedColor = ruleInfoUiState.seedColor),
    )
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
                        blockAllComponents = onBlockAllInPageClick,
                        enableAllComponents = onEnableAllInPageClick,
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
            ruleMatchedAppListUiState = ruleMatchedAppListUiState,
            ruleInfoUiState = ruleInfoUiState,
            tabState = tabState,
            switchTab = switchTab,
            onStopServiceClick = onStopServiceClick,
            onLaunchActivityClick = onLaunchActivityClick,
            onCopyNameClick = onCopyNameClick,
            onCopyFullNameClick = onCopyFullNameClick,
            onBlockAllInItemClick = onBlockAllInItemClick,
            onEnableAllInItemClick = onEnableAllInItemClick,
            onSwitch = onSwitch,
            navigateToAppDetail = navigateToAppDetail,
        )
    }
}

@Composable
fun RuleDetailAppBarActions(
    modifier: Modifier = Modifier,
    appBarUiState: AppBarUiState = AppBarUiState(),
    blockAllComponents: () -> Unit = {},
    enableAllComponents: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val actions = appBarUiState.actions
        if (actions.isEmpty()) return
        if (actions.contains(MORE)) {
            MoreActionMenu(
                blockAllComponents = blockAllComponents,
                enableAllComponents = enableAllComponents,
            )
        }
    }
}

@Composable
fun MoreActionMenu(
    modifier: Modifier = Modifier,
    blockAllComponents: () -> Unit = {},
    enableAllComponents: () -> Unit = {},
) {
    val items = listOf(
        DropDownMenuItem(
            string.feature_ruledetail_block_all_of_this_page,
            blockAllComponents,
        ),
        DropDownMenuItem(
            string.feature_ruledetail_enable_all_of_this_page,
            enableAllComponents,
        ),
    )
    BlockerAppTopBarMenu(
        modifier = modifier,
        menuIcon = BlockerIcons.MoreVert,
        menuIconDesc = com.merxury.blocker.core.ui.R.string.core_ui_more_menu,
        menuList = items,
    )
}

@Composable
private fun rememberToolbarState(toolbarHeightRange: IntRange): ToolbarState = rememberSaveable(saver = ExitUntilCollapsedState.Saver) {
    ExitUntilCollapsedState(heightRange = toolbarHeightRange)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RuleDetailTabContent(
    ruleMatchedAppListUiState: Result<List<MatchedItem>>,
    ruleInfoUiState: RuleInfoUiState.Success,
    tabState: TabState<RuleDetailTabs>,
    switchTab: (RuleDetailTabs) -> Unit,
    modifier: Modifier = Modifier,
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
    navigateToAppDetail: (String) -> Unit = { _ -> },
    onBlockAllInItemClick: (List<ComponentInfo>) -> Unit = { _ -> },
    onEnableAllInItemClick: (List<ComponentInfo>) -> Unit = { _ -> },
    onSwitch: (ComponentInfo, Boolean) -> Unit = { _, _ -> },
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
                    ruleMatchedAppListUiState = ruleMatchedAppListUiState,
                    onStopServiceClick = onStopServiceClick,
                    onLaunchActivityClick = onLaunchActivityClick,
                    onCopyNameClick = onCopyNameClick,
                    onCopyFullNameClick = onCopyFullNameClick,
                    navigateToAppDetail = navigateToAppDetail,
                    onBlockAllInItemClick = onBlockAllInItemClick,
                    onEnableAllInItemClick = onEnableAllInItemClick,
                    onSwitch = onSwitch,
                )

                1 -> RuleDescription(rule = ruleInfoUiState.ruleInfo)
            }
        }
    }
}

@Composable
@PreviewThemes
private fun RuleDetailScreenPreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    val components = ComponentListPreviewParameterProvider().values.first()
    val appList = AppListPreviewParameterProvider().values.first()
    val tabState = RuleDetailTabStatePreviewParameterProvider().values.first()

    BlockerTheme {
        Surface {
            RuleDetailScreen(
                showBackButton = false,
                ruleInfoUiState = RuleInfoUiState.Success(
                    ruleInfo = ruleList.first(),
                    matchedAppsUiState = Success(
                        listOf(
                            MatchedItem(
                                header = MatchedHeaderData(
                                    title = appList.first().label,
                                    uniqueId = appList.first().packageName,
                                ),
                                componentList = components,
                            ),
                        ),
                    ),
                ),
                tabState = tabState[0],
                appBarUiState = AppBarUiState(
                    actions = listOf(
                        MORE,
                    ),
                ),
            )
        }
    }
}

@Composable
@PreviewThemes
private fun RuleDetailScreenSelectedDescriptionPreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    val components = ComponentListPreviewParameterProvider().values.first()
    val appList = AppListPreviewParameterProvider().values.first()
    val tabState = RuleDetailTabStatePreviewParameterProvider().values.first()

    BlockerTheme {
        Surface {
            RuleDetailScreen(
                ruleInfoUiState = RuleInfoUiState.Success(
                    ruleInfo = ruleList.first(),
                    matchedAppsUiState = Success(
                        listOf(
                            MatchedItem(
                                header = MatchedHeaderData(
                                    title = appList.first().label,
                                    uniqueId = appList.first().packageName,
                                ),
                                componentList = components,
                            ),
                        ),
                    ),
                ),
                tabState = tabState[1],
            )
        }
    }
}

@Composable
@Preview
private fun RuleDetailScreenWithApplicableLoadingPreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    val tabState = RuleDetailTabStatePreviewParameterProvider().values.first()

    BlockerTheme {
        Surface {
            RuleDetailScreen(
                ruleInfoUiState = RuleInfoUiState.Success(
                    ruleInfo = ruleList.first(),
                    matchedAppsUiState = Loading,
                ),
                tabState = tabState[0],
            )
        }
    }
}

@Composable
@Preview
private fun RuleDetailScreenLoadingPreview() {
    val tabState = RuleDetailTabStatePreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            RuleDetailScreen(
                ruleInfoUiState = RuleInfoUiState.Loading,
                tabState = tabState[0],
            )
        }
    }
}

@Composable
@Preview
private fun RuleDetailScreenErrorPreview() {
    val tabState = RuleDetailTabStatePreviewParameterProvider().values.first()
    BlockerTheme {
        Surface {
            RuleDetailScreen(
                ruleInfoUiState = Error(
                    error = UiMessage("Error"),
                ),
                tabState = tabState[0],
            )
        }
    }
}
