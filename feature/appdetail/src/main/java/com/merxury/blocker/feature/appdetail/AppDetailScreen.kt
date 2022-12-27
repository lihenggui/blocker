/*
 * Copyright 2022 Blocker
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.component.BlockerTabRow
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.feature.appdetail.R.string
import com.merxury.blocker.feature.appdetail.component.AppBasicInfoCard
import com.merxury.blocker.feature.appdetail.component.AppDetailCommonTabContentRoute
import com.merxury.blocker.feature.appdetail.component.AppInfoTabContent
import com.merxury.blocker.feature.appdetail.model.AppInfoUiState
import com.merxury.blocker.feature.appdetail.model.AppInfoUiState.Success
import com.merxury.blocker.feature.appdetail.model.AppInfoViewModel
import kotlinx.datetime.Clock.System

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun AppDetailRoute(
    viewModel: AppInfoViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
) {
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppDetailScreen(
        uiState = uiState,
        tabState = tabState,
        isRefreshing = uiState is AppInfoUiState.Loading,
        onRefresh = { viewModel.onRefresh() },
        switchTab = viewModel::switchTab,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    uiState: AppInfoUiState,
    tabState: TabState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    switchTab: (Int) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        when (uiState) {
            AppInfoUiState.Loading -> {
                Column(
                    modifier = modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    BlockerLoadingWheel(
                        modifier = modifier,
                        contentDesc = stringResource(id = string.loading),
                    )
                }
            }

            is Success -> {
                BlockerTopAppBar(
                    title = uiState.appInfo.label,
                    navigationIcon = BlockerIcons.Back,
                    navigationIconContentDescription = null,
                    actionIconFirst = BlockerIcons.Search,
                    actionIconContentDescriptionFirst = null,
                    actionIconSecond = BlockerIcons.MoreVert,
                    actionIconContentDescriptionSecond = null,
                    onNavigationClick = onBackClick
                )
                AppDetailContent(
                    uiState = uiState,
                    tabState = tabState,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    switchTab = switchTab
                )
            }

            is AppInfoUiState.Error -> ErrorAppDetailScreen(uiState.error.message)
        }
    }
}

@Composable
fun AppDetailContent(
    uiState: Success,
    tabState: TabState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    switchTab: (Int) -> Unit
) {
    AppBasicInfoCard(app = uiState.appInfo)
    BlockerTabRow(selectedTabIndex = tabState.currentIndex) {
        tabState.titles.forEachIndexed { index, titleRes ->
            BlockerTab(
                selected = index == tabState.currentIndex,
                onClick = { switchTab(index) },
                text = { Text(text = stringResource(id = titleRes)) }
            )
        }
    }
    when (tabState.currentIndex) {
        0 -> {
            AppInfoTabContent(
                app = uiState.appInfo,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh
            )
        }

        1 -> {
            AppDetailCommonTabContentRoute()
        }

        2 -> {
            AppDetailCommonTabContentRoute()
        }

        3 -> {
            AppDetailCommonTabContentRoute()
        }

        4 -> {
            AppDetailCommonTabContentRoute()
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
            string.content_provider
        ),
        currentIndex = 0
    )
    BlockerTheme {
        AppDetailScreen(
            uiState = Success(appInfo = app),
            tabState = tabState,
            isRefreshing = false,
            onRefresh = {},
            switchTab = {},
            onBackClick = {}
        )
    }
}
