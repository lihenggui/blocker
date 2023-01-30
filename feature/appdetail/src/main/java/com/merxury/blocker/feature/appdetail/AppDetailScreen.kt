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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.feature.appdetail.AppInfoUiState.Success
import com.merxury.blocker.feature.appdetail.R.string
import com.merxury.blocker.feature.appdetail.cmplist.ComponentListContentRoute
import com.merxury.blocker.feature.appdetail.component.AppInfoCard
import com.merxury.blocker.feature.appdetail.navigation.Screen
import com.merxury.blocker.feature.appdetail.summary.SummaryContent
import kotlinx.datetime.Clock.System

@Composable
fun AppDetailRoute(
    onBackClick: () -> Unit,
    packageName: String,
    screen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppDetailViewModel = hiltViewModel(),
) {
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppDetailScreen(
        uiState = uiState,
        tabState = tabState,
        modifier = modifier,
        screen = screen,
        onLaunchAppClick = viewModel::launchApp,
        onNavigate = onNavigate,
        onBackClick = onBackClick,
    )
}

@Composable
fun AppDetailScreen(
    uiState: AppInfoUiState,
    tabState: TabState,
    screen: Screen,
    onBackClick: () -> Unit,
    onLaunchAppClick: (String) -> Unit,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        when (uiState) {
            is AppInfoUiState.Loading -> {
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
                    app = uiState.appInfo,
                    tabState = tabState,
                    screen = screen,
                    onBackClick = onBackClick,
                    onLaunchAppClick = onLaunchAppClick,
                    onNavigate = onNavigate,
                    modifier = modifier,
                )
            }

            is AppInfoUiState.Error -> ErrorAppDetailScreen(uiState.error.message)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailContent(
    app: Application,
    tabState: TabState,
    screen: Screen,
    onBackClick: () -> Unit,
    onLaunchAppClick: (String) -> Unit,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val isCollapsed by remember { derivedStateOf { scrollBehavior.state.collapsedFraction > 0.5 } }
    val screenState by remember { mutableStateOf(screen) }
    Scaffold(
        topBar = {
            BlockerCollapsingTopAppBar(
                title = app.label,
                content = {
                    AppInfoCard(
                        label = app.label,
                        packageName = app.packageName,
                        versionCode = app.versionCode,
                        versionName = app.versionName,
                        packageInfo = app.packageInfo,
                        onAppIconClick = { onLaunchAppClick(app.packageName) },
                    )
                },
                isCollapsed = isCollapsed,
                scrollBehavior = scrollBehavior,
                onNavigationClick = onBackClick,
                actions = { },
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
                selectedTabIndex = screenState.tabPosition,
            ) {
                tabState.titles.forEachIndexed { index, titleRes ->
                    val newScreen = Screen.fromPosition(index)
                    BlockerTab(
                        selected = index == tabState.currentIndex,
                        onClick = { onNavigate(newScreen) },
                        text = { Text(text = stringResource(id = titleRes)) },
                    )
                }
            }
            when (screenState.tabPosition) {
                0 -> SummaryContent(app)
                1 -> ComponentListContentRoute(type = RECEIVER)
                2 -> ComponentListContentRoute(type = SERVICE)
                3 -> ComponentListContentRoute(type = ACTIVITY)
                4 -> ComponentListContentRoute(type = PROVIDER)
            }
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
        titles = listOf(
            string.app_info,
            string.service,
            string.service,
            string.activity,
            string.content_provider,
        ),
        currentIndex = 0,
    )
    BlockerTheme {
        Surface {
            AppDetailScreen(
                uiState = Success(appInfo = app),
                tabState = tabState,
                onLaunchAppClick = {},
                onNavigate = {},
                screen = Screen.Detail,
                onBackClick = {},
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
        titles = listOf(
            string.app_info,
            string.receiver,
            string.service,
            string.activity,
            string.content_provider,
        ),
        currentIndex = 0,
    )
    BlockerTheme {
        Surface {
            AppDetailScreen(
                uiState = Success(appInfo = app),
                tabState = tabState,
                onLaunchAppClick = {},
                onNavigate = {},
                screen = Screen.Detail,
                onBackClick = {},
            )
        }
    }
}
