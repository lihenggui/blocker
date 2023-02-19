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

package com.merxury.blocker.feature.appdetail

import android.content.res.Configuration
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerCollapsingTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.designsystem.component.BlockerScrollableTabRow
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.component.MaxToolbarHeight
import com.merxury.blocker.core.designsystem.component.MinToolbarHeight
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.AppDetailTabs.Activity
import com.merxury.blocker.core.ui.AppDetailTabs.Info
import com.merxury.blocker.core.ui.AppDetailTabs.Provider
import com.merxury.blocker.core.ui.AppDetailTabs.Receiver
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.state.toolbar.AppBarActionState
import com.merxury.blocker.core.ui.state.toolbar.ExitUntilCollapsedState
import com.merxury.blocker.core.ui.state.toolbar.ToolbarState
import com.merxury.blocker.feature.appdetail.AppInfoUiState.Success
import com.merxury.blocker.feature.appdetail.R.string
import com.merxury.blocker.feature.appdetail.cmplist.ComponentListContentRoute
import com.merxury.blocker.feature.appdetail.summary.SummaryContent
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock.System

@Composable
fun AppDetailRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppDetailViewModel = hiltViewModel(),
) {
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topAppBarUiState by viewModel.appBarUiState.collectAsStateWithLifecycle()

    AppDetailScreen(
        uiState = uiState,
        tabState = tabState,
        modifier = modifier.fillMaxSize(),
        onLaunchAppClick = viewModel::launchApp,
        switchTab = viewModel::switchTab,
        onBackClick = onBackClick,
        topAppBarUiState = topAppBarUiState,
        onSearchTextChanged = viewModel::search,
        onSearchModeChanged = viewModel::changeSearchMode,
        onToolbarActionUpdated = viewModel::updateAppBarAction,
    )
}

@Composable
fun AppDetailScreen(
    uiState: AppInfoUiState,
    tabState: TabState<AppDetailTabs>,
    onBackClick: () -> Unit,
    onLaunchAppClick: (String) -> Unit,
    switchTab: (AppDetailTabs) -> Unit,
    modifier: Modifier = Modifier,
    topAppBarUiState: AppBarUiState,
    onSearchTextChanged: (TextFieldValue) -> Unit = {},
    onSearchModeChanged: (Boolean) -> Unit,
    onToolbarActionUpdated: (AppBarActionState) -> Unit = {},
) {
    when (uiState) {
        is AppInfoUiState.Loading -> {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BlockerLoadingWheel(
                    modifier = modifier,
                    contentDesc = stringResource(id = string.loading),
                )
            }
        }

        is Success -> {
            AppDetailContent(
                app = uiState.appInfo,
                tabState = tabState,
                onBackClick = onBackClick,
                onLaunchAppClick = onLaunchAppClick,
                switchTab = switchTab,
                modifier = modifier,
                topAppBarUiState = topAppBarUiState,
                onSearchTextChanged = onSearchTextChanged,
                onSearchModeChanged = onSearchModeChanged,
                onToolbarActionUpdated = onToolbarActionUpdated,
            )
        }

        is AppInfoUiState.Error -> ErrorAppDetailScreen(uiState.error.message)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailContent(
    app: Application,
    tabState: TabState<AppDetailTabs>,
    onBackClick: () -> Unit,
    onLaunchAppClick: (String) -> Unit,
    switchTab: (AppDetailTabs) -> Unit,
    modifier: Modifier = Modifier,
    topAppBarUiState: AppBarUiState,
    onSearchTextChanged: (TextFieldValue) -> Unit = {},
    onSearchModeChanged: (Boolean) -> Unit,
    onToolbarActionUpdated: (AppBarActionState) -> Unit = {},
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
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                toolbarState.scrollOffset = toolbarState.scrollOffset - available.y
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
                                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                            toolbarState.scrollOffset =
                                toolbarState.scrollOffset - (value - (toolbarState.height + toolbarState.offset))
                            if (toolbarState.scrollOffset == 0f) scope.coroutineContext.cancelChildren()
                        }
                    }
                }
                return super.onPostFling(consumed, available)
            }
        }
    }
    Scaffold(
        topBar = {
            BlockerCollapsingTopAppBar(
                progress = toolbarState.progress,
                onNavigationClick = onBackClick,
                title = app.label,
                actions = { topAppBarUiState.actions.actions?.invoke(this) },
                subtitle = app.packageName,
                summary = stringResource(
                    id = string.data_with_explanation,
                    app.versionName.orEmpty(),
                    app.versionCode,
                ),
                iconSource = app.packageInfo,
                onIconClick = { onLaunchAppClick(app.packageName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { toolbarState.height.toDp() }),
            )
        },
        modifier = modifier.nestedScroll(nestedScrollConnection),
    ) { innerPadding ->
        AppDetailTabContent(
            app = app,
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
            topAppBarUiState = topAppBarUiState,
            onSearchTextChanged = onSearchTextChanged,
            onSearchModeChanged = onSearchModeChanged,
            onToolbarActionUpdated = onToolbarActionUpdated,
        )
    }
}

@Composable
private fun rememberToolbarState(toolbarHeightRange: IntRange): ToolbarState {
    return rememberSaveable(saver = ExitUntilCollapsedState.Saver) {
        ExitUntilCollapsedState(heightRange = toolbarHeightRange)
    }
}

@Composable
fun AppDetailTabContent(
    modifier: Modifier = Modifier,
    app: Application,
    tabState: TabState<AppDetailTabs>,
    switchTab: (AppDetailTabs) -> Unit,
    topAppBarUiState: AppBarUiState,
    onSearchTextChanged: (TextFieldValue) -> Unit = {},
    onSearchModeChanged: (Boolean) -> Unit,
    onToolbarActionUpdated: (AppBarActionState) -> Unit = {},
) {
    Column(
        modifier = modifier,
    ) {
        BlockerScrollableTabRow(
            selectedTabIndex = tabState.currentIndex,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            tabState.items.forEachIndexed { index, tabItem ->
                BlockerTab(
                    selected = index == tabState.currentIndex,
                    onClick = { switchTab(tabItem) },
                    text = { Text(text = stringResource(id = tabItem.title)) },
                )
            }
        }
        when (tabState.selectedItem) {
            Info ->
                SummaryContent(
                    app = app,
                    onToolbarActionUpdated = onToolbarActionUpdated,
                )

            Receiver -> ComponentListContentRoute(
                packageName = app.packageName,
                type = RECEIVER,
                topAppBarUiState = topAppBarUiState,
                onSearchTextChanged = onSearchTextChanged,
                onSearchModeChanged = onSearchModeChanged,
                onAppBarActionUpdated = onToolbarActionUpdated,
            )

            Service -> ComponentListContentRoute(
                packageName = app.packageName,
                type = SERVICE,
                topAppBarUiState = topAppBarUiState,
                onSearchTextChanged = onSearchTextChanged,
                onSearchModeChanged = onSearchModeChanged,
                onAppBarActionUpdated = onToolbarActionUpdated,
            )

            Activity -> ComponentListContentRoute(
                packageName = app.packageName,
                type = ACTIVITY,
                topAppBarUiState = topAppBarUiState,
                onSearchTextChanged = onSearchTextChanged,
                onSearchModeChanged = onSearchModeChanged,
                onAppBarActionUpdated = onToolbarActionUpdated,
            )

            Provider -> ComponentListContentRoute(
                packageName = app.packageName,
                type = PROVIDER,
                topAppBarUiState = topAppBarUiState,
                onSearchTextChanged = onSearchTextChanged,
                onSearchModeChanged = onSearchModeChanged,
                onAppBarActionUpdated = onToolbarActionUpdated,
            )
        }
    }
}

@Composable
fun ErrorAppDetailScreen(message: String) {
    Text(text = message)
}

@Composable
@Preview
fun AppDetailScreenPreview() {
    val app = Application(
        label = "Blocker",
        packageName = "com.mercury.blocker",
        versionName = "1.2.69-alpha",
        isEnabled = false,
        firstInstallTime = System.now(),
        lastUpdateTime = System.now(),
        packageInfo = null,
    )
    val tabState = TabState(
        items = listOf(
            Info,
            Receiver,
            Service,
            Activity,
            Provider,
        ),
        selectedItem = Info,
    )
    BlockerTheme {
        Surface {
            AppDetailScreen(
                uiState = Success(appInfo = app),
                tabState = tabState,
                onLaunchAppClick = {},
                onBackClick = {},
                switchTab = {},
                topAppBarUiState = AppBarUiState(),
                onSearchTextChanged = {},
                onToolbarActionUpdated = {},
                onSearchModeChanged = {},
            )
        }
    }
}

@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun AppDetailScreenCollapsedPreview() {
    val app = Application(
        label = "Blocker",
        packageName = "com.mercury.blocker",
        versionName = "1.2.69-alpha",
        isEnabled = false,
        firstInstallTime = System.now(),
        lastUpdateTime = System.now(),
        packageInfo = null,
    )
    val tabState = TabState(
        items = listOf(
            Info,
            Receiver,
            Service,
            Activity,
            Provider,
        ),
        selectedItem = Info,
    )
    BlockerTheme {
        Surface {
            AppDetailScreen(
                uiState = Success(appInfo = app),
                tabState = tabState,
                onLaunchAppClick = {},
                onBackClick = {},
                switchTab = {},
                topAppBarUiState = AppBarUiState(),
                onSearchTextChanged = {},
                onToolbarActionUpdated = {},
                onSearchModeChanged = {},
            )
        }
    }
}
