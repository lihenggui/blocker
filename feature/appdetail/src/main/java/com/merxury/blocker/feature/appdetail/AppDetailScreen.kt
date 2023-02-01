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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerCollapsingTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.designsystem.component.BlockerScrollableTabRow
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.component.MaxToolbarHeight
import com.merxury.blocker.core.designsystem.component.MinToolbarHeight
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.state.toolbar.ExitUntilCollapsedState
import com.merxury.blocker.core.ui.state.toolbar.ToolbarState
import com.merxury.blocker.feature.appdetail.AppInfoUiState.Success
import com.merxury.blocker.feature.appdetail.R.string
import com.merxury.blocker.feature.appdetail.cmplist.ComponentListContentRoute
import com.merxury.blocker.feature.appdetail.navigation.Screen.Activity
import com.merxury.blocker.feature.appdetail.navigation.Screen.Detail
import com.merxury.blocker.feature.appdetail.navigation.Screen.Provider
import com.merxury.blocker.feature.appdetail.navigation.Screen.Receiver
import com.merxury.blocker.feature.appdetail.navigation.Screen.Service
import com.merxury.blocker.feature.appdetail.summary.SummaryContent
import kotlinx.datetime.Clock.System

@Composable
fun AppDetailRoute(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppDetailViewModel = hiltViewModel(),
) {
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val toolbarHeightRange = with(LocalDensity.current) {
        MinToolbarHeight.roundToPx()..MaxToolbarHeight.roundToPx()
    }
    val toolbarState = rememberToolbarState(toolbarHeightRange)
    toolbarState.scrollValue = scrollState.value

    AppDetailScreen(
        uiState = uiState,
        tabState = tabState,
        modifier = modifier,
        progress = toolbarState.progress,
        onLaunchAppClick = viewModel::launchApp,
        switchTab = viewModel::switchTab,
        onBackClick = onBackClick,
    )
}

@Composable
fun AppDetailScreen(
    uiState: AppInfoUiState,
    progress: Float,
    tabState: TabState,
    onBackClick: () -> Unit,
    onLaunchAppClick: (String) -> Unit,
    switchTab: (Int) -> Unit,
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
                    progress = progress,
                    onBackClick = onBackClick,
                    onLaunchAppClick = onLaunchAppClick,
                    switchTab = switchTab,
                    modifier = modifier,
                )
            }

            is AppInfoUiState.Error -> ErrorAppDetailScreen(uiState.error.message)
        }
    }
}

@Composable
fun AppDetailContent(
    app: Application,
    tabState: TabState,
    progress: Float,
    onBackClick: () -> Unit,
    onLaunchAppClick: (String) -> Unit,
    switchTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AppDetailTabContent(
            app = app,
            tabState = tabState,
            switchTab = switchTab,
            modifier = modifier,
        )
        BlockerCollapsingTopAppBar(
            progress = progress,
            onNavigationClick = onBackClick,
            title = app.label,
            actions = {
                IconButton(
                    onClick = {},
                    modifier = Modifier.then(Modifier.size(24.dp)),
                ) {
                    Icon(
                        imageVector = BlockerIcons.More,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            subtitle = app.packageName,
            summary = app.versionCode.toString(),
            icon = BlockerIcons.Find,
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
    tabState: TabState,
    switchTab: (Int) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
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
            Detail.tabPosition -> SummaryContent(app)
            Receiver.tabPosition -> ComponentListContentRoute(
                packageName = app.packageName,
                type = RECEIVER,
            )

            Service.tabPosition -> ComponentListContentRoute(
                packageName = app.packageName,
                type = SERVICE,
            )

            Activity.tabPosition -> ComponentListContentRoute(
                packageName = app.packageName,
                type = ACTIVITY,
            )

            Provider.tabPosition -> ComponentListContentRoute(
                packageName = app.packageName,
                type = PROVIDER,
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
                progress = 0f,
                onBackClick = {},
                switchTab = {},
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
                progress = 1f,
                onBackClick = {},
                switchTab = {},
            )
        }
    }
}
