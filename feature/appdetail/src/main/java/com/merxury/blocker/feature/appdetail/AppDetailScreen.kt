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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerCollapsingTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerScrollableTabRow
import com.merxury.blocker.core.designsystem.component.BlockerSearchTextField
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.component.MaxToolbarHeight
import com.merxury.blocker.core.designsystem.component.MinToolbarHeight
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.AppDetailTabs.Activity
import com.merxury.blocker.core.ui.AppDetailTabs.Info
import com.merxury.blocker.core.ui.AppDetailTabs.Provider
import com.merxury.blocker.core.ui.AppDetailTabs.Receiver
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.applist.model.AppItem
import com.merxury.blocker.core.ui.component.ComponentList
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SEARCH
import com.merxury.blocker.core.ui.state.toolbar.ExitUntilCollapsedState
import com.merxury.blocker.core.ui.state.toolbar.ToolbarState
import com.merxury.blocker.feature.appdetail.AppInfoUiState.Success
import com.merxury.blocker.feature.appdetail.R.string
import com.merxury.blocker.feature.appdetail.summary.SummaryContent
import com.merxury.blocker.feature.appdetail.ui.MoreActionMenu
import com.merxury.blocker.feature.appdetail.ui.SearchActionMenu
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
    val appInfoUiState by viewModel.appInfoUiState.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()
    val topAppBarUiState by viewModel.appBarUiState.collectAsStateWithLifecycle()
    val componentListUiState by viewModel.componentListUiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    AppDetailScreen(
        appInfoUiState = appInfoUiState,
        topAppBarUiState = topAppBarUiState,
        componentListUiState = componentListUiState,
        tabState = tabState,
        modifier = modifier.fillMaxSize(),
        onLaunchAppClick = { packageName ->
            viewModel.launchApp(context, packageName)
        },
        switchTab = viewModel::switchTab,
        onBackClick = onBackClick,
        onSearchTextChanged = viewModel::search,
        onSearchModeChanged = viewModel::changeSearchMode,
        blockAllComponents = { viewModel.controlAllComponents(false) },
        enableAllComponents = { viewModel.controlAllComponents(true) },
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
    )
    if (errorState != null) {
        BlockerErrorAlertDialog(
            title = errorState?.title.orEmpty(),
            text = errorState?.content.orEmpty(),
            onDismissRequest = viewModel::dismissAlert,
        )
    }
}

@Composable
fun AppDetailScreen(
    appInfoUiState: AppInfoUiState,
    topAppBarUiState: AppBarUiState,
    componentListUiState: ComponentListUiState,
    tabState: TabState<AppDetailTabs>,
    onBackClick: () -> Unit,
    onLaunchAppClick: (String) -> Unit,
    switchTab: (AppDetailTabs) -> Unit,
    modifier: Modifier = Modifier,
    onSearchTextChanged: (TextFieldValue) -> Unit = {},
    onSearchModeChanged: (Boolean) -> Unit = {},
    blockAllComponents: () -> Unit = {},
    enableAllComponents: () -> Unit = {},
    onExportRules: (String) -> Unit = {},
    onImportRules: (String) -> Unit = {},
    onExportIfw: (String) -> Unit = {},
    onImportIfw: (String) -> Unit = {},
    onResetIfw: (String) -> Unit = {},
    onSwitchClick: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
) {
    when (appInfoUiState) {
        is AppInfoUiState.Loading -> {
            LoadingScreen()
        }

        is Success -> {
            AppDetailContent(
                app = appInfoUiState.appInfo,
                topAppBarUiState = topAppBarUiState,
                componentListUiState = componentListUiState,
                tabState = tabState,
                onBackClick = onBackClick,
                onLaunchAppClick = onLaunchAppClick,
                switchTab = switchTab,
                modifier = modifier,
                onSearchTextChanged = onSearchTextChanged,
                onSearchModeChanged = onSearchModeChanged,
                enableAllComponents = enableAllComponents,
                blockAllComponents = blockAllComponents,
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
            )
        }

        is AppInfoUiState.Error -> ErrorScreen(appInfoUiState.error)
    }
}

@Composable
fun AppDetailContent(
    app: AppItem,
    tabState: TabState<AppDetailTabs>,
    componentListUiState: ComponentListUiState,
    onBackClick: () -> Unit,
    onLaunchAppClick: (String) -> Unit,
    switchTab: (AppDetailTabs) -> Unit,
    modifier: Modifier = Modifier,
    topAppBarUiState: AppBarUiState,
    onSearchTextChanged: (TextFieldValue) -> Unit = {},
    onSearchModeChanged: (Boolean) -> Unit = {},
    blockAllComponents: () -> Unit = {},
    enableAllComponents: () -> Unit = {},
    onExportRules: (String) -> Unit = {},
    onImportRules: (String) -> Unit = {},
    onExportIfw: (String) -> Unit = {},
    onImportIfw: (String) -> Unit = {},
    onResetIfw: (String) -> Unit = {},
    onSwitchClick: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
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
                actions = {
                    AppDetailAppBarActions(
                        appBarUiState = topAppBarUiState,
                        onSearchTextChanged = onSearchTextChanged,
                        onSearchModeChange = onSearchModeChanged,
                        blockAllComponents = blockAllComponents,
                        enableAllComponents = enableAllComponents,
                    )
                },
                subtitle = app.packageName,
                summary = stringResource(
                    id = string.data_with_explanation,
                    app.versionName,
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
            componentListUiState = componentListUiState,
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
        )
    }
}

@Composable
fun AppDetailAppBarActions(
    appBarUiState: AppBarUiState,
    onSearchTextChanged: (TextFieldValue) -> Unit = {},
    onSearchModeChange: (Boolean) -> Unit = {},
    blockAllComponents: () -> Unit = {},
    enableAllComponents: () -> Unit = {},
) {
    val actions = appBarUiState.actions
    if (actions.contains(SEARCH)) {
        if (appBarUiState.isSearchMode) {
            BlockerSearchTextField(
                keyword = appBarUiState.keyword,
                onValueChange = onSearchTextChanged,
                placeholder = {
                    Text(text = stringResource(id = string.search_components))
                },
                onClearClick = {
                    if (appBarUiState.keyword.text.isEmpty()) {
                        onSearchModeChange(false)
                        return@BlockerSearchTextField
                    }
                    onSearchTextChanged(TextFieldValue())
                },
            )
        } else {
            SearchActionMenu(onSearchModeChange = onSearchModeChange)
        }
    }
    if (actions.contains(MORE)) {
        MoreActionMenu(
            blockAllComponents = blockAllComponents,
            enableAllComponents = enableAllComponents,
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
    app: AppItem,
    componentListUiState: ComponentListUiState,
    tabState: TabState<AppDetailTabs>,
    switchTab: (AppDetailTabs) -> Unit,
    onExportRules: (String) -> Unit = {},
    onImportRules: (String) -> Unit = {},
    onExportIfw: (String) -> Unit = {},
    onImportIfw: (String) -> Unit = {},
    onResetIfw: (String) -> Unit = {},
    onSwitchClick: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
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
        when (tabState.selectedItem) {
            Info -> SummaryContent(
                app = app,
                onExportRules = onExportRules,
                onImportRules = onImportRules,
                onExportIfw = onExportIfw,
                onImportIfw = onImportIfw,
                onResetIfw = onResetIfw,
            )

            is Receiver -> ComponentList(
                components = componentListUiState.receiver,
                onSwitchClick = onSwitchClick,
                onStopServiceClick = onStopServiceClick,
                onLaunchActivityClick = onLaunchActivityClick,
                onCopyNameClick = onCopyNameClick,
                onCopyFullNameClick = onCopyFullNameClick,
            )

            is Service -> ComponentList(
                components = componentListUiState.service,
                onSwitchClick = onSwitchClick,
                onStopServiceClick = onStopServiceClick,
                onLaunchActivityClick = onLaunchActivityClick,
                onCopyNameClick = onCopyNameClick,
                onCopyFullNameClick = onCopyFullNameClick,
            )

            is Activity -> ComponentList(
                components = componentListUiState.activity,
                onSwitchClick = onSwitchClick,
                onStopServiceClick = onStopServiceClick,
                onLaunchActivityClick = onLaunchActivityClick,
                onCopyNameClick = onCopyNameClick,
                onCopyFullNameClick = onCopyFullNameClick,
            )

            is Provider -> ComponentList(
                components = componentListUiState.provider,
                onSwitchClick = onSwitchClick,
                onStopServiceClick = onStopServiceClick,
                onLaunchActivityClick = onLaunchActivityClick,
                onCopyNameClick = onCopyNameClick,
                onCopyFullNameClick = onCopyFullNameClick,
            )
        }
    }
}

@Composable
@Preview
fun AppDetailScreenPreview() {
    val app = AppItem(
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
        itemCount = mapOf(
            Info to 1,
            Receiver to 2,
            Service to 3,
            Activity to 4,
            Provider to 5,
        ),
    )
    BlockerTheme {
        Surface {
            AppDetailScreen(
                appInfoUiState = Success(appInfo = app),
                componentListUiState = ComponentListUiState(),
                tabState = tabState,
                onLaunchAppClick = {},
                onBackClick = {},
                switchTab = {},
                topAppBarUiState = AppBarUiState(),
                onSearchTextChanged = {},
                onSearchModeChanged = {},
            )
        }
    }
}

@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun AppDetailScreenCollapsedPreview() {
    val app = AppItem(
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
        itemCount = mapOf(
            Info to 1,
            Receiver to 2,
            Service to 3,
            Activity to 4,
            Provider to 5,
        ),
    )
    BlockerTheme {
        Surface {
            AppDetailScreen(
                appInfoUiState = Success(appInfo = app),
                componentListUiState = ComponentListUiState(),
                tabState = tabState,
                onLaunchAppClick = {},
                onBackClick = {},
                switchTab = {},
                topAppBarUiState = AppBarUiState(),
                onSearchTextChanged = {},
                onSearchModeChanged = {},
            )
        }
    }
}
