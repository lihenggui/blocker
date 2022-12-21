package com.merxury.blocker.feature.appdetail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.feature.appdetail.ErrorAppDetailScreen
import com.merxury.blocker.feature.appdetail.R.string
import com.merxury.blocker.feature.appdetail.model.AppDetailCommonUiState
import com.merxury.blocker.feature.appdetail.model.AppDetailCommonViewModel

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun AppDetailCommonTabContentRoute(
    modifier: Modifier = Modifier,
    viewModel: AppDetailCommonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppDetailCommonTabContent(
        uiState = uiState,
        isRefreshing = uiState is AppDetailCommonUiState.Loading,
        onRefresh = { viewModel.onRefresh() },
        onSwitch = { _, _, _ -> true },
        modifier = modifier
    )
}

@Composable
fun AppDetailCommonTabContent(
    uiState: AppDetailCommonUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSwitch: (String, String, Boolean) -> Boolean,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        AppDetailCommonUiState.Loading -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BlockerLoadingWheel(
                    contentDesc = stringResource(id = string.loading),
                )
            }
        }

        is AppDetailCommonUiState.Success -> {
            ComponentTabContent(
                components = uiState.eComponentList,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onSwitchClick = onSwitch,
                modifier = modifier
            )
        }

        is AppDetailCommonUiState.Error -> ErrorAppDetailScreen(uiState.error.message)
    }
}
