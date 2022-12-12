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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.designsystem.component.BlockerTab
import com.merxury.blocker.core.designsystem.component.BlockerTabRow
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.feature.appdetail.AppDetailUiState.Success
import com.merxury.blocker.feature.appdetail.component.AppInfoTabContent
import com.merxury.blocker.feature.appdetail.component.ComponentTabContent

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun AppDetailRoute(
    viewModel: AppDetailViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
) {
    val tabState by viewModel.tabState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppDetailScreen(
        uiState = uiState,
        tabState = tabState,
        switchTab = viewModel::switchTab,
        onSwitch = viewModel::onSwitch,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    uiState: AppDetailUiState,
    tabState: TabState,
    switchTab: (Int) -> Unit,
    onSwitch: (ComponentInfo) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        when (uiState) {
            AppDetailUiState.Loading -> {
                Column(
                    modifier = modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    BlockerLoadingWheel(
                        modifier = modifier,
                        contentDesc = stringResource(id = R.string.loading),
                    )
                }
            }

            is Success -> {
                BlockerTopAppBar(
                    title = uiState.appInfo.appName,
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
                    switchTab = switchTab,
                    onSwitch = onSwitch
                )
            }

            is AppDetailUiState.Error -> ErrorAppDetailScreen(uiState.errorMessage)
        }

    }
}

@Composable
fun AppDetailContent(
    uiState: Success,
    tabState: TabState,
    switchTab: (Int) -> Unit,
    onSwitch: (ComponentInfo) -> Unit,
) {
    BlockerTabRow(selectedTabIndex = tabState.currentIndex) {
        tabState.titles.forEachIndexed { index, title ->
            BlockerTab(
                selected = index == tabState.currentIndex,
                onClick = { switchTab(index) },
                text = { Text(text = title) }
            )
        }
    }
    when (tabState.currentIndex) {
        0 -> {
            AppInfoTabContent(appDetailInfo = uiState.appInfo)
        }

        1 -> {
            ComponentTabContent(componentList = uiState.service, onSwitch = onSwitch)
        }

        2 -> {
            ComponentTabContent(componentList = uiState.receiver, onSwitch = onSwitch)
        }

        3 -> {
            ComponentTabContent(componentList = uiState.activity, onSwitch = onSwitch)
        }

        4 -> {
            ComponentTabContent(componentList = uiState.contentProvider, onSwitch = onSwitch)
        }
    }
}

@Composable
fun ErrorAppDetailScreen(message: String) {
    Text(text = message)
}
