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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.designsystem.component.BlockerTextField
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.ui.state.toolbar.AppBarActionState
import com.merxury.blocker.feature.appdetail.ErrorAppDetailScreen
import com.merxury.blocker.feature.appdetail.R.string
import com.merxury.blocker.feature.appdetail.SearchBoxUiState
import dagger.hilt.android.EntryPointAccessors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentListContentRoute(
    modifier: Modifier = Modifier,
    onComposing: (AppBarActionState) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    packageName: String,
    type: ComponentType,
    viewModel: ComponentListViewModel = componentListViewModel(
        packageName = packageName,
        type = type,
    ),
    searchBoxUiState: SearchBoxUiState,
    onSearchTextChanged: (TextFieldValue) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    ComponentListContent(
        uiState = uiState,
        onSwitch = viewModel::controlComponent,
        modifier = modifier,
        onStopServiceClick = viewModel::stopService,
        onLaunchActivityClick = viewModel::launchActivity,
        onCopyNameClick = { name ->
            clipboardManager.setText(AnnotatedString(name))
        },
        onCopyFullNameClick = { fullName ->
            clipboardManager.setText(AnnotatedString(fullName))
        },
        listState = listState,
    )
    if (errorState != null) {
        BlockerErrorAlertDialog(
            title = errorState?.message.orEmpty(),
            text = errorState?.stackTrace.orEmpty(),
            onDismissRequest = viewModel::dismissAlert,
        )
    }
    LaunchedEffect(true) {
        onComposing(
            AppBarActionState(
                actions = {
                    IconButton(
                        onClick = {
                            onComposing(
                                AppBarActionState(
                                    actions = {
                                        BlockerTextField(
                                            keyword = searchBoxUiState.keyword,
                                            onSearchTextChanged = onSearchTextChanged,
                                            onClearClick = {
                                                onSearchTextChanged(
                                                    TextFieldValue(),
                                                )
                                            },
                                        )
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier.then(Modifier.size(24.dp)),
                                        ) {
                                            Icon(
                                                imageVector = BlockerIcons.More,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.then(Modifier.size(24.dp)),
                    ) {
                        Icon(
                            imageVector = BlockerIcons.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(
                        onClick = {},
                        modifier = Modifier.then(Modifier.size(24.dp)),
                    ) {
                        Icon(
                            imageVector = BlockerIcons.More,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            ),
        )
    }
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
    onStopServiceClick: (String, String) -> Unit,
    onLaunchActivityClick: (String, String) -> Unit,
    onCopyNameClick: (String) -> Unit,
    onCopyFullNameClick: (String) -> Unit,
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
                onStopServiceClick = onStopServiceClick,
                onLaunchActivityClick = onLaunchActivityClick,
                onCopyNameClick = onCopyNameClick,
                onCopyFullNameClick = onCopyFullNameClick,
                modifier = modifier,
                listState = listState,
            )
        }

        is ComponentListUiState.Error -> ErrorAppDetailScreen(uiState.error.message)
    }
}
