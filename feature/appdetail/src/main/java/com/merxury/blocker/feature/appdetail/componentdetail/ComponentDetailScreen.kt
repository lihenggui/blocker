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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.appdetail.componentdetail.ComponentDetailUiState.Success

@Composable
fun ComponentDetailRoute(
    dismissHandler: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComponentDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ComponentDetailScreen(
        uiState = uiState,
        modifier = modifier,
        dismissHandler = dismissHandler,
        onSaveDetailClick = viewModel::save,
    )
}

@Composable
fun ComponentDetailScreen(
    uiState: ComponentDetailUiState,
    modifier: Modifier = Modifier,
    dismissHandler: () -> Unit = {},
    onSaveDetailClick: (ComponentDetail) -> Unit = {},
) {
    when (uiState) {
        is ComponentDetailUiState.Loading -> LoadingScreen()
        is ComponentDetailUiState.Error -> ErrorScreen(error = uiState.message)
        is Success -> ComponentDetailContent(
            name = uiState.detail.name,
            belongToSdk = uiState.detail.sdkName.isNullOrEmpty().not(),
            sdkName = uiState.detail.sdkName,
            description = uiState.detail.description,
            disableEffect = uiState.detail.disableEffect,
            contributor = uiState.detail.contributor,
            addedVersion = uiState.detail.addedVersion,
            recommendToBlock = uiState.detail.recommendToBlock,
            modifier = modifier,
            dismissHandler = dismissHandler,
            onSaveDetailClick = onSaveDetailClick,
        )
    }
}

@Composable
fun ComponentDetailContent(
    name: String,
    modifier: Modifier = Modifier,
    belongToSdk: Boolean = false,
    sdkName: String? = null,
    description: String? = null,
    disableEffect: String? = null,
    contributor: String? = null,
    addedVersion: String? = null,
    recommendToBlock: Boolean = false,
    dismissHandler: () -> Unit = {},
    onSaveDetailClick: (ComponentDetail) -> Unit = {},
) {
    ComponentDetailDialog(
        modifier = modifier,
        name = name,
        belongToSdk = belongToSdk,
        sdkName = sdkName,
        description = description,
        disableEffect = disableEffect,
        contributor = contributor,
        addedVersion = addedVersion,
        recommendToBlock = recommendToBlock,
        onDismiss = dismissHandler,
        onSaveDetailClick = onSaveDetailClick,
    )
}
