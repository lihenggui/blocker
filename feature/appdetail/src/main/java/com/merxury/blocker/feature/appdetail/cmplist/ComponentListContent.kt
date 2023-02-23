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

import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerSearchTextField
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.icon.BlockerActionIcon
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.ui.component.ComponentItem
import com.merxury.blocker.core.ui.component.ComponentList
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.core.ui.state.toolbar.AppBarActionState
import com.merxury.blocker.feature.appdetail.AppBarUiState
import com.merxury.blocker.feature.appdetail.R.string
import dagger.hilt.android.EntryPointAccessors

@Composable
fun ComponentListContentRoute(
    modifier: Modifier = Modifier,
    packageName: String,
    type: ComponentType,
    viewModel: ComponentListViewModel = componentListViewModel(
        packageName = packageName,
        type = type,
    ),
    topAppBarUiState: AppBarUiState,
    onSearchTextChanged: (TextFieldValue) -> Unit = {},
    onSearchModeChanged: (Boolean) -> Unit,
    onAppBarActionUpdated: (AppBarActionState) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()
    val componentList = viewModel.componentListFlow.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    ComponentListContent(
        uiState = uiState,
        list = componentList,
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
    )
    if (errorState != null) {
        BlockerErrorAlertDialog(
            title = errorState?.message.orEmpty(),
            text = errorState?.stackTrace.orEmpty(),
            onDismissRequest = viewModel::dismissAlert,
        )
    }
    LaunchedEffect(topAppBarUiState.keyword, topAppBarUiState.isSearchMode) {
        updateAppBarActions(
            topAppBarUiState = topAppBarUiState,
            onSearchTextChanged = { newSearchText ->
                onSearchTextChanged(newSearchText)
                viewModel.filter(newSearchText.text)
            },
            onToolbarActionUpdated = onAppBarActionUpdated,
            onSearchModeChanged = onSearchModeChanged,
            blockAllComponents = viewModel::blockAllComponents,
            enableAllComponents = viewModel::enableAllComponents,
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
    list: State<List<ComponentItem>>,
    modifier: Modifier = Modifier,
    onStopServiceClick: (String, String) -> Unit = { _, _ -> },
    onLaunchActivityClick: (String, String) -> Unit = { _, _ -> },
    onCopyNameClick: (String) -> Unit = { _ -> },
    onCopyFullNameClick: (String) -> Unit = { _ -> },
    onSwitch: (String, String, Boolean) -> Unit = { _, _, _ -> },
) {
    when (uiState) {
        ComponentListUiState.Loading -> {
            LoadingScreen()
        }

        is ComponentListUiState.Success -> {
            ComponentList(
                components = list.value,
                modifier = modifier,
                onSwitchClick = onSwitch,
                onStopServiceClick = onStopServiceClick,
                onLaunchActivityClick = onLaunchActivityClick,
                onCopyNameClick = onCopyNameClick,
                onCopyFullNameClick = onCopyFullNameClick,
            )
        }

        is ComponentListUiState.Error -> ErrorScreen(uiState.error)
    }
}

fun updateAppBarActions(
    topAppBarUiState: AppBarUiState,
    onSearchTextChanged: (TextFieldValue) -> Unit = {},
    onToolbarActionUpdated: (AppBarActionState) -> Unit,
    onSearchModeChanged: (Boolean) -> Unit,
    blockAllComponents: () -> Unit,
    enableAllComponents: () -> Unit,
) {
    onToolbarActionUpdated(
        AppBarActionState(
            actions = {
                if (topAppBarUiState.isSearchMode) {
                    BlockerSearchTextField(
                        keyword = topAppBarUiState.keyword,
                        onValueChange = onSearchTextChanged,
                        placeholder = {
                            Text(text = stringResource(id = string.search_components))
                        },
                        onClearClick = {
                            if (topAppBarUiState.keyword.text.isEmpty()) {
                                onSearchModeChanged(false)
                                return@BlockerSearchTextField
                            }
                            onSearchTextChanged(TextFieldValue())
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    IconButton(
                        onClick = { onSearchModeChanged(true) },
                    ) {
                        BlockerActionIcon(
                            imageVector = BlockerIcons.Search,
                            contentDescription = null,
                        )
                    }
                }
                MoreActionMenu(
                    blockAllComponents = blockAllComponents,
                    enableAllComponents = enableAllComponents,
                )
            },
        ),
    )
}

@Composable
fun MoreActionMenu(
    blockAllComponents: () -> Unit,
    enableAllComponents: () -> Unit,
) {
    val items = listOf(
        DropDownMenuItem(
            string.block_all_of_this_page,
            blockAllComponents,
        ),
        DropDownMenuItem(
            string.enable_all_of_this_page,
            enableAllComponents,
        ),
    )
    BlockerAppTopBarMenu(
        menuIcon = BlockerIcons.MoreVert,
        menuIconDesc = string.more_menu,
        menuList = items,
    )
}
