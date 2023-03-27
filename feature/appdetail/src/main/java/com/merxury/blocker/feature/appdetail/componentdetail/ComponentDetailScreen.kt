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

package com.merxury.blocker.feature.appdetail.componentdetail

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.appdetail.componentdetail.ComponentDetailUiState.Success

@Composable
fun ComponentDetailRoute(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: ComponentDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ComponentDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
fun ComponentDetailScreen(
    uiState: ComponentDetailUiState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        is ComponentDetailUiState.Loading -> LoadingScreen()
        is ComponentDetailUiState.Error -> ErrorScreen(error = uiState.message)
        is Success -> ComponentDetailContent(
            name = uiState.detail.name,
            detail = uiState.detail,
            modifier = modifier,
        )
    }
}

@Composable
fun ComponentDetailContent(
    name: String,
    detail: UserEditableComponentDetail,
    modifier: Modifier = Modifier,
) {
    ComponentDetailDialog(name = name, detail = detail, modifier = modifier)
}
