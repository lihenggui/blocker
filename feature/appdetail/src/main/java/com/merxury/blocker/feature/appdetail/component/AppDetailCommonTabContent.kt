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
