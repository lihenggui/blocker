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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.feature.appdetail.ErrorAppDetailScreen
import com.merxury.blocker.feature.appdetail.R.string
import dagger.hilt.android.EntryPointAccessors

@Composable
fun ComponentListContentRoute(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    packageName: String,
    type: ComponentType,
    viewModel: ComponentListViewModel = componentListViewModel(
        packageName = packageName,
        type = type,
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ComponentListContent(
        uiState = uiState,
        onSwitch = viewModel::controlComponent,
        modifier = modifier,
        listState = listState,
    )
}

@Composable
fun componentListViewModel(packageName: String, type: ComponentType): ComponentListViewModel {
    val factory = EntryPointAccessors.fromActivity(
        LocalContext.current as android.app.Activity,
        ViewModelFactoryProvider::class.java,
    ).componentLiveViewModelFactory()
    val key = "$packageName+${type.name}"
    return viewModel(
        key = key,
        factory = ComponentListViewModel.provideFactory(factory, packageName, type),
    )
}

@Composable
fun ComponentListContent(
    uiState: ComponentListUiState,
    onSwitch: (String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    when (uiState) {
        ComponentListUiState.Loading -> {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                item {
                    BlockerLoadingWheel(
                        modifier = modifier,
                        contentDesc = stringResource(id = string.loading),
                    )
                }

            }
        }

        is ComponentListUiState.Success -> {
            ComponentTabContent(
                components = uiState.list,
                onSwitchClick = onSwitch,
                modifier = modifier,
                listState = listState,
            )
        }

        is ComponentListUiState.Error -> ErrorAppDetailScreen(uiState.error.message)
    }
}
