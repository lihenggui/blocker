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
import com.merxury.blocker.feature.appdetail.model.AppContentProviderUiState
import com.merxury.blocker.feature.appdetail.model.AppContentProviderViewModel

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun AppContentProviderTabContentRoute(
    modifier: Modifier = Modifier,
    viewModel: AppContentProviderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppContentProviderTabContent(
        uiState = uiState,
        isRefreshing = uiState is AppContentProviderUiState.Loading,
        onRefresh = { viewModel.onRefresh() },
        onSwitch = { _, _, _ -> true },
        modifier = modifier
    )
}

@Composable
fun AppContentProviderTabContent(
    uiState: AppContentProviderUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSwitch: (String, String, Boolean) -> Boolean,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        AppContentProviderUiState.Loading -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BlockerLoadingWheel(
                    contentDesc = stringResource(id = string.loading),
                )
            }
        }

        is AppContentProviderUiState.Success -> {
            ComponentTabContent(
                components = uiState.contentProvider,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onSwitchClick = onSwitch,
                modifier = modifier
            )
        }

        is AppContentProviderUiState.Error -> ErrorAppDetailScreen(uiState.error.message)
    }
}