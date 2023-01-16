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

package com.merxury.blocker.feature.appdetail.cmplist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.feature.appdetail.ErrorAppDetailScreen
import com.merxury.blocker.feature.appdetail.R.string

@Composable
fun ComponentListContentRoute(
    modifier: Modifier = Modifier,
    viewModel: ComponentListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ComponentListContent(
        uiState = uiState,
        onSwitch = viewModel::controlComponent,
        modifier = modifier
    )
}

@Composable
fun ComponentListContent(
    uiState: ComponentListUiState,
    onSwitch: (String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        ComponentListUiState.Loading -> {
            Column(
                modifier = modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BlockerLoadingWheel(
                    modifier = modifier,
                    contentDesc = stringResource(id = string.loading),
                )
            }
        }

        is ComponentListUiState.Success -> {
            ComponentTabContent(
                components = uiState.list,
                onSwitchClick = onSwitch,
                modifier = modifier
            )
        }

        is ComponentListUiState.Error -> ErrorAppDetailScreen(uiState.error.message)
    }
}
