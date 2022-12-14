package com.merxury.blocker.feature.applist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.feature.applist.R.string

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun AppListRoute(
    navigateToAppDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppListScreen(
        uiState = uiState,
        navigateToAppDetail = navigateToAppDetail,
        isRefreshing = uiState is AppListUiState.Loading,
        onRefresh = { viewModel.onRefresh() },
        modifier = modifier
    )
}

@Composable
fun AppListScreen(
    uiState: AppListUiState,
    navigateToAppDetail: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (uiState) {
            AppListUiState.Loading -> {
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

            is AppListUiState.Success -> {
                AppListContent(
                    appList = uiState.appList,
                    navigateToAppDetail = navigateToAppDetail,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = modifier
                )
            }

            is AppListUiState.Error -> ErrorAppListScreen(uiState.errorMessage)
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppListContent(
    appList: SnapshotStateList<AppInfo>,
    navigateToAppDetail: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listContent = remember { appList }
    val listState = rememberLazyListState()
    val refreshing by remember { mutableStateOf(isRefreshing) }
    val refreshingState = rememberPullRefreshState(refreshing, onRefresh)
    Box(modifier.pullRefresh(refreshingState)) {
        LazyColumn(
            modifier = modifier,
            state = listState
        ) {
            items(listContent, key = { it.packageName }) {
                AppListItem(
                    appIcon = it.appIcon,
                    packageName = it.packageName,
                    versionName = it.versionName,
                    appServiceStatus = it.appServiceStatus,
                    onClick = navigateToAppDetail
                )
            }
        }
        PullRefreshIndicator(
            refreshing = refreshing,
            state = refreshingState,
            modifier = Modifier.align(Alignment.TopCenter),
            scale = true
        )
    }
}

@Composable
fun ErrorAppListScreen(message: String) {
    Text(text = message)
}
