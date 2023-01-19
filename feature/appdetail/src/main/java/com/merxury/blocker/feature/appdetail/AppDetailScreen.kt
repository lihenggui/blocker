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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerCollapsingTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.designsystem.component.BlockerScrollableTabRow
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.feature.appdetail.R.string
import com.merxury.blocker.feature.appdetail.cmplist.ComponentListContentRoute
import com.merxury.blocker.feature.appdetail.component.AppInfoTabContent
import com.merxury.blocker.feature.appdetail.component.CollapseImageSection
import com.merxury.blocker.feature.appdetail.component.CollapseTextSection
import com.merxury.blocker.feature.appdetail.component.TopAppBarMoreMenu
import com.merxury.blocker.feature.appdetail.model.AppInfoUiState
import com.merxury.blocker.feature.appdetail.model.AppInfoUiState.Success
import com.merxury.blocker.feature.appdetail.model.AppInfoViewModel
import kotlinx.datetime.Clock.System

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailRoute(
    viewModel: AppInfoViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
) {
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val isCollapsed by remember { derivedStateOf { scrollBehavior.state.collapsedFraction > 0.5 } }
    AppDetailScreen(
        uiState = uiState,
        tabState = tabState,
        onRefresh = { viewModel.onRefresh() },
        switchTab = viewModel::switchTab,
        onBackClick = onBackClick,
        onShare = viewModel::onShare,
        onFindInPage = viewModel::onFindInPage,
        onEnableApp = viewModel::onEnableApp,
        onEnableAll = viewModel::onEnableAll,
        onBlockAll = viewModel::onBlockAll,
        isCollapsed = isCollapsed,
        scrollBehavior = scrollBehavior,
        onExportRules = viewModel::onExportRules,
        onImportRules = viewModel::onImportRules,
        onExportIfw = viewModel::onExportIfw,
        onImportIfw = viewModel::onImportIfw,
        onResetIfw = viewModel::onResetIfw,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    uiState: AppInfoUiState,
    tabState: TabState,
    onRefresh: () -> Unit,
    switchTab: (Int) -> Unit,
    onBackClick: () -> Unit,
    onShare: () -> Unit,
    onFindInPage: () -> Unit,
    onEnableApp: () -> Unit,
    onEnableAll: () -> Unit,
    onBlockAll: () -> Unit,
    isCollapsed: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
    onExportIfw: () -> Unit,
    onImportIfw: () -> Unit,
    onResetIfw: () -> Unit,
) {
    Column(modifier) {
        when (uiState) {
            AppInfoUiState.Loading -> {
                Column(
                    modifier = modifier.fillMaxSize(),
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
                    uiState = uiState,
                    tabState = tabState,
                    onRefresh = onRefresh,
                    switchTab = switchTab,
                    onBackClick = onBackClick,
                    onShare = onShare,
                    onFindInPage = onFindInPage,
                    onEnableApp = onEnableApp,
                    onEnableAll = onEnableAll,
                    onBlockAll = onBlockAll,
                    isCollapsed = isCollapsed,
                    scrollBehavior = scrollBehavior,
                    modifier = modifier,
                    onExportRules = onExportRules,
                    onImportRules = onImportRules,
                    onExportIfw = onExportIfw,
                    onImportIfw = onImportIfw,
                    onResetIfw = onResetIfw,
                )
            }

            is AppInfoUiState.Error -> ErrorAppDetailScreen(uiState.error.message)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailContent(
    uiState: Success,
    tabState: TabState,
    onRefresh: () -> Unit,
    switchTab: (Int) -> Unit,
    onBackClick: () -> Unit,
    onShare: () -> Unit,
    onFindInPage: () -> Unit,
    onEnableApp: () -> Unit,
    onEnableAll: () -> Unit,
    onBlockAll: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    isCollapsed: Boolean,
    modifier: Modifier = Modifier,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
    onExportIfw: () -> Unit,
    onImportIfw: () -> Unit,
    onResetIfw: () -> Unit,
) {
    Scaffold(
        topBar = {
            BlockerCollapsingTopAppBar(
                title = uiState.appInfo.label,
                collapseTextSection = { CollapseTextSection(app = uiState.appInfo) },
                collapseImageSection = { CollapseImageSection(info = uiState.appInfo.packageInfo) },
                isCollapsed = isCollapsed,
                scrollBehavior = scrollBehavior,
                onNavigationClick = onBackClick,
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = BlockerIcons.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onFindInPage) {
                        Icon(
                            imageVector = BlockerIcons.Find,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (tabState.currentIndex != 0) {
                        TopAppBarMoreMenu(
                            onEnableApp = onEnableApp,
                            onRefresh = onRefresh,
                            onEnableAll = onEnableAll,
                            onBlockAll = onBlockAll,
                        )
                    }
                },
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .fillMaxWidth(),
        ) {
            BlockerScrollableTabRow(
                selectedTabIndex = tabState.currentIndex,
            ) {
                tabState.titles.forEachIndexed { index, titleRes ->
                    BlockerTab(
                        selected = index == tabState.currentIndex,
                        onClick = { switchTab(index) },
                        text = { Text(text = stringResource(id = titleRes)) },
                    )
                }
            }
            when (tabState.currentIndex) {
                0 -> AppInfoTabContent(
                    app = uiState.appInfo,
                    onExportRules = onExportRules,
                    onImportRules = onImportRules,
                    onExportIfw = onExportIfw,
                    onImportIfw = onImportIfw,
                    onResetIfw = onResetIfw,
                )

                1 -> ComponentListContentRoute()
                2 -> ComponentListContentRoute()
                3 -> ComponentListContentRoute()
                4 -> ComponentListContentRoute()
            }
        }
    }
}

@Composable
fun ErrorAppDetailScreen(message: String) {
    Text(text = message)
}

@OptIn(ExperimentalMaterial3Api::class)
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
        titles = listOf(
            string.app_info,
            string.service,
            string.service,
            string.activity,
            string.content_provider,
        ),
        currentIndex = 0,
    )
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    BlockerTheme {
        Surface {
            AppDetailScreen(
                uiState = Success(appInfo = app),
                tabState = tabState,
                onRefresh = {},
                switchTab = {},
                onBackClick = {},
                onShare = {},
                onFindInPage = {},
                onEnableApp = {},
                onEnableAll = {},
                onBlockAll = {},
                isCollapsed = false,
                scrollBehavior = scrollBehavior,
                onExportRules = {},
                onImportRules = {},
                onExportIfw = {},
                onImportIfw = {},
                onResetIfw = {},
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        titles = listOf(
            string.app_info,
            string.receiver,
            string.service,
            string.activity,
            string.content_provider,
        ),
        currentIndex = 0,
    )
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    BlockerTheme {
        Surface {
            AppDetailScreen(
                uiState = Success(appInfo = app),
                tabState = tabState,
                onRefresh = {},
                switchTab = {},
                onBackClick = {},
                onShare = {},
                onFindInPage = {},
                onEnableApp = {},
                onEnableAll = {},
                onBlockAll = {},
                isCollapsed = true,
                scrollBehavior = scrollBehavior,
                onExportRules = {},
                onImportRules = {},
                onExportIfw = {},
                onImportIfw = {},
                onResetIfw = {},
            )
        }
    }
}
