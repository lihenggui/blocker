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

package com.merxury.blocker.feature.ruledetail

import android.content.res.Configuration
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animateDecay
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerCollapsingTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.component.BlockerTabRow
import com.merxury.blocker.core.designsystem.component.MaxToolbarHeight
import com.merxury.blocker.core.designsystem.component.MinToolbarHeight
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.applist.model.AppItem
import com.merxury.blocker.core.ui.component.ComponentItem
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.rule.RuleDetailTabs
import com.merxury.blocker.core.ui.rule.RuleDetailTabs.Applicable
import com.merxury.blocker.core.ui.rule.RuleDetailTabs.Description
import com.merxury.blocker.core.ui.rule.RuleMatchedApp
import com.merxury.blocker.core.ui.rule.RuleMatchedAppList
import com.merxury.blocker.core.ui.rule.RuleMatchedAppListUiState
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.core.ui.state.toolbar.ExitUntilCollapsedState
import com.merxury.blocker.core.ui.state.toolbar.ToolbarState
import com.merxury.blocker.feature.ruledetail.component.RuleDescription
import com.merxury.blocker.feature.ruledetail.model.RuleDetailViewModel
import com.merxury.blocker.feature.ruledetail.model.RuleInfoUiState
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

@Composable
fun RuleDetailRoute(
    onBackClick: () -> Unit,
    viewModel: RuleDetailViewModel = hiltViewModel(),
) {
    val ruleInfoUiState by viewModel.ruleInfoUiState.collectAsStateWithLifecycle()
    val ruleMatchedAppListUiState by viewModel.ruleMatchedAppListUiState.collectAsStateWithLifecycle()
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    RuleDetailScreen(
        ruleMatchedAppListUiState = ruleMatchedAppListUiState,
        ruleInfoUiState = ruleInfoUiState,
        onBackClick = onBackClick,
        tabState = tabState,
        switchTab = viewModel::switchTab,
        onStopServiceClick = viewModel::stopServiceClick,
        onLaunchActivityClick = viewModel::launchActivityClick,
        onCopyNameClick = viewModel::copyNameClick,
        onCopyFullNameClick = viewModel::copyFullNameClick,
        onSwitch = viewModel::switchComponent,
    )
}

@Composable
fun RuleDetailScreen(
    modifier: Modifier = Modifier,
    ruleMatchedAppListUiState: RuleMatchedAppListUiState,
    ruleInfoUiState: RuleInfoUiState,
    onBackClick: () -> Unit,
    tabState: TabState<RuleDetailTabs>,
    switchTab: (RuleDetailTabs) -> Unit,
    onStopServiceClick: (String, String) -> Unit,
    onLaunchActivityClick: (String, String) -> Unit,
    onCopyNameClick: (String) -> Unit,
    onCopyFullNameClick: (String) -> Unit,
    onSwitch: (String, String, Boolean) -> Unit,
) {
    when (ruleInfoUiState) {
        RuleInfoUiState.Loading -> {
            LoadingScreen()
        }

        is RuleInfoUiState.Success -> {
            RuleDetailContent(
                modifier = modifier,
                ruleMatchedAppListUiState = ruleMatchedAppListUiState,
                ruleInfoUiState = ruleInfoUiState,
                onBackClick = onBackClick,
                tabState = tabState,
                switchTab = switchTab,
                onStopServiceClick = onStopServiceClick,
                onLaunchActivityClick = onLaunchActivityClick,
                onCopyNameClick = onCopyNameClick,
                onCopyFullNameClick = onCopyFullNameClick,
                onSwitch = onSwitch,
            )
        }

        is RuleInfoUiState.Error -> {
            ErrorScreen(error = ruleInfoUiState.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleDetailContent(
    modifier: Modifier = Modifier,
    ruleMatchedAppListUiState: RuleMatchedAppListUiState,
    ruleInfoUiState: RuleInfoUiState.Success,
    onBackClick: () -> Unit,
    tabState: TabState<RuleDetailTabs>,
    switchTab: (RuleDetailTabs) -> Unit,
    onStopServiceClick: (String, String) -> Unit,
    onLaunchActivityClick: (String, String) -> Unit,
    onCopyNameClick: (String) -> Unit,
    onCopyFullNameClick: (String) -> Unit,
    onSwitch: (String, String, Boolean) -> Unit,
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
                title = ruleInfoUiState.ruleInfo.name,
                subtitle = ruleInfoUiState.ruleInfo.company.toString(),
                summary = "",
                iconSource = ruleInfoUiState.ruleInfo.iconUrl,
                onIconClick = { },
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
            onSwitch = onSwitch,
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
fun RuleDetailTabContent(
    modifier: Modifier = Modifier,
    ruleMatchedAppListUiState: RuleMatchedAppListUiState,
    ruleInfoUiState: RuleInfoUiState.Success,
    tabState: TabState<RuleDetailTabs>,
    switchTab: (RuleDetailTabs) -> Unit,
    onStopServiceClick: (String, String) -> Unit,
    onLaunchActivityClick: (String, String) -> Unit,
    onCopyNameClick: (String) -> Unit,
    onCopyFullNameClick: (String) -> Unit,
    onSwitch: (String, String, Boolean) -> Unit,
) {
    Column(
        modifier = modifier,
    ) {
        BlockerTabRow(
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
            Description -> RuleDescription(rule = ruleInfoUiState.ruleInfo)

            Applicable -> RuleMatchedAppList(
                ruleMatchedAppListUiState = ruleMatchedAppListUiState,
                onStopServiceClick = onStopServiceClick,
                onLaunchActivityClick = onLaunchActivityClick,
                onCopyNameClick = onCopyNameClick,
                onCopyFullNameClick = onCopyFullNameClick,
                onSwitch = onSwitch,
            )
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
fun RuleDetailScreenPreView() {
    val componentInfo = ComponentItem(
        name = "component",
        simpleName = "com",
        packageName = "blocker",
        type = ACTIVITY,
        pmBlocked = false,
    )
    val ruleMatchedApp = RuleMatchedApp(
        app = AppItem(
            packageName = "com.merxury.blocker",
            label = "Blocker",
            isSystem = false,
        ),
        componentList = listOf(componentInfo),
    )
    val ruleMatchedAppListUiState = RuleMatchedAppListUiState.Success(
        list = listOf(ruleMatchedApp),
    )
    val item = GeneralRule(
        id = 2,
        name = "Android WorkerManager",
        iconUrl = null,
        company = "Google",
        description = "WorkManager is the recommended solution for persistent work. " +
            "Work is persistent when it remains scheduled through app restarts and " +
            "system reboots. Because most background processing is best accomplished " +
            "through persistent work, WorkManager is the primary recommended API for " +
            "background processing.",
        sideEffect = "Background works won't be able to execute",
        safeToBlock = false,
        contributors = listOf("Google"),
        searchKeyword = listOf("androidx.work.", "androidx.work.impl"),
    )
    val ruleInfoUiState = RuleInfoUiState.Success(
        ruleInfo = item,
    )
    val tabState = TabState(
        items = listOf(
            Description,
            Applicable,
        ),
        selectedItem = Description,
    )
    BlockerTheme {
        Surface {
            RuleDetailScreen(
                ruleMatchedAppListUiState = ruleMatchedAppListUiState,
                ruleInfoUiState = ruleInfoUiState,
                onBackClick = {},
                tabState = tabState,
                switchTab = {},
                onStopServiceClick = { _, _ -> },
                onLaunchActivityClick = { _, _ -> },
                onCopyNameClick = { _ -> },
                onCopyFullNameClick = { _ -> },
                onSwitch = { _, _, _ -> },
            )
        }
    }
}

@Composable
@Preview
fun RuleDetailScreenLoadingPreView() {
    val componentInfo = ComponentItem(
        name = "component",
        simpleName = "com",
        packageName = "blocker",
        type = ACTIVITY,
        pmBlocked = false,
    )
    val ruleMatchedApp = RuleMatchedApp(
        app = AppItem(
            packageName = "com.merxury.blocker",
            label = "Blocker",
            isSystem = false,
        ),
        componentList = listOf(componentInfo),
    )
    val ruleMatchedAppListUiState = RuleMatchedAppListUiState.Success(
        list = listOf(ruleMatchedApp),
    )
    val ruleInfoUiState = RuleInfoUiState.Loading
    val tabState = TabState(
        items = listOf(
            Description,
            Applicable,
        ),
        selectedItem = Description,
    )
    BlockerTheme {
        Surface {
            RuleDetailScreen(
                ruleMatchedAppListUiState = ruleMatchedAppListUiState,
                ruleInfoUiState = ruleInfoUiState,
                onBackClick = {},
                tabState = tabState,
                switchTab = {},
                onStopServiceClick = { _, _ -> },
                onLaunchActivityClick = { _, _ -> },
                onCopyNameClick = { _ -> },
                onCopyFullNameClick = { _ -> },
                onSwitch = { _, _, _ -> },
            )
        }
    }
}

@Composable
@Preview
fun RuleDetailScreenErrorPreView() {
    val componentInfo = ComponentItem(
        name = "component",
        simpleName = "com",
        packageName = "blocker",
        type = ACTIVITY,
        pmBlocked = false,
    )
    val ruleMatchedApp = RuleMatchedApp(
        app = AppItem(
            packageName = "com.merxury.blocker",
            label = "Blocker",
            isSystem = false,
        ),
        componentList = listOf(componentInfo),
    )
    val ruleMatchedAppListUiState = RuleMatchedAppListUiState.Success(
        list = listOf(ruleMatchedApp),
    )
    val ruleInfoUiState = RuleInfoUiState.Error(UiMessage("Error"))
    val tabState = TabState(
        items = listOf(
            Description,
            Applicable,
        ),
        selectedItem = Description,
    )
    BlockerTheme {
        Surface {
            RuleDetailScreen(
                ruleMatchedAppListUiState = ruleMatchedAppListUiState,
                ruleInfoUiState = ruleInfoUiState,
                onBackClick = {},
                tabState = tabState,
                switchTab = {},
                onStopServiceClick = { _, _ -> },
                onLaunchActivityClick = { _, _ -> },
                onCopyNameClick = { _ -> },
                onCopyFullNameClick = { _ -> },
                onSwitch = { _, _, _ -> },
            )
        }
    }
}
