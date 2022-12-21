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
import com.merxury.blocker.feature.appdetail.model.AppActivityUiState
import com.merxury.blocker.feature.appdetail.model.AppActivityViewModel

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun AppActivityTabContentRoute(
    modifier: Modifier = Modifier,
    viewModel: AppActivityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppActivityTabContent(
        uiState = uiState,
        isRefreshing = uiState is AppActivityUiState.Loading,
        onRefresh = { viewModel.onRefresh() },
        onSwitch = { _, _, _ -> true },
        modifier = modifier
    )
}

@Composable
fun AppActivityTabContent(
    uiState: AppActivityUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSwitch: (String, String, Boolean) -> Boolean,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        AppActivityUiState.Loading -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BlockerLoadingWheel(
                    contentDesc = stringResource(id = string.loading),
                )
            }
        }

        is AppActivityUiState.Success -> {
            ComponentTabContent(
                components = uiState.activity,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onSwitchClick = onSwitch,
                modifier = modifier
            )
        }

        is AppActivityUiState.Error -> ErrorAppDetailScreen(uiState.error.message)
    }
}
