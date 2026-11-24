/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.feature.debloater.impl

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerFilterChip
import com.merxury.blocker.core.designsystem.component.BlockerSearchTextField
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.control.ControlMessageSnackbar
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.feature.appdebloater.api.R

@Composable
fun DebloaterScreen(
    snackbarHostState: SnackbarHostState,
    navigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DebloaterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val noRootPermissionWarning = stringResource(R.string.feature_debloater_api_no_root_permission)

    LaunchedEffect(uiState.hasRootPermission) {
        if (!uiState.hasRootPermission) {
            snackbarHostState.showSnackbar(
                message = noRootPermissionWarning,
                duration = SnackbarDuration.Long,
            )
        }
    }

    ControlMessageSnackbar(
        messages = uiState.messages,
        snackbarHostState = snackbarHostState,
        onShow = viewModel::onMessageShown,
        onGrantAccess = viewModel::grantAccess,
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            BlockerTopAppBar(
                title = stringResource(R.string.feature_debloater_api_title),
                hasNavigationIcon = true,
                onNavigationClick = navigateBack,
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.onTestShare()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, "Test share message from Blocker")
                                type = "*/*"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(shareIntent)
                        },
                    ) {
                        Icon(
                            imageVector = BlockerIcons.Share,
                            contentDescription = stringResource(R.string.feature_debloater_api_test_share),
                        )
                    }
                },
            )
        },
    ) { padding ->
        DebloaterScreenContent(
            modifier = Modifier.padding(padding),
            debloatableUiState = uiState.content,
            searchQuery = uiState.searchQuery,
            componentTypeFilter = uiState.componentTypeFilter,
            errorState = uiState.alert,
            inFlight = uiState.inFlight,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onComponentTypeFilterChange = viewModel::updateComponentTypeFilter,
            onSwitchClick = viewModel::setEnabled,
            onBlockAllInItemClick = { viewModel.setAllEnabled(it.app, it.targets, enabled = false) },
            onEnableAllInItemClick = { viewModel.setAllEnabled(it.app, it.targets, enabled = true) },
            onDismissError = viewModel::dismissError,
        )
    }
}

@Composable
internal fun DebloaterScreenContent(
    modifier: Modifier = Modifier,
    debloatableUiState: Result<List<MatchedTarget>> = Result.Loading,
    searchQuery: String = "",
    componentTypeFilter: Set<ComponentClassification> = emptySet(),
    errorState: UiMessage? = null,
    inFlight: Set<ComponentKey> = emptySet(),
    onSearchQueryChange: (String) -> Unit = {},
    onComponentTypeFilterChange: (Set<ComponentClassification>) -> Unit = {},
    onSwitchClick: (DebloatableComponentUiItem, Boolean) -> Unit = { _, _ -> },
    onBlockAllInItemClick: (MatchedTarget) -> Unit = {},
    onEnableAllInItemClick: (MatchedTarget) -> Unit = {},
    onDismissError: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BlockerSearchTextField(
                modifier = Modifier.weight(1f),
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onSearchTrigger = {},
                placeholder = {
                    Text(text = stringResource(R.string.feature_debloater_api_search_hint))
                },
            )
        }
        ComponentTypeFilterChips(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            selectedTypes = componentTypeFilter,
            onTypeToggle = { type ->
                val newSet = if (type in componentTypeFilter) {
                    componentTypeFilter - type
                } else {
                    componentTypeFilter + type
                }
                onComponentTypeFilterChange(newSet)
            },
        )
        DebloaterContent(
            modifier = Modifier.weight(1f),
            data = debloatableUiState,
            inFlight = inFlight,
            onBlockAllInItemClick = onBlockAllInItemClick,
            onEnableAllInItemClick = onEnableAllInItemClick,
            onSwitch = onSwitchClick,
        )
    }

    if (errorState != null) {
        BlockerErrorAlertDialog(
            title = errorState.title,
            text = errorState.content.orEmpty(),
            onDismissRequest = onDismissError,
        )
    }
}

@Composable
private fun ComponentTypeFilterChips(
    selectedTypes: Set<ComponentClassification>,
    onTypeToggle: (ComponentClassification) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ComponentClassification.entries.filter { it != ComponentClassification.LAUNCHER }.forEach { type ->
            val labelRes = when (type) {
                ComponentClassification.SHAREABLE -> R.string.feature_debloater_api_filter_chip_shareable
                ComponentClassification.DEEPLINK -> R.string.feature_debloater_api_filter_chip_deeplink
                ComponentClassification.LAUNCHER -> R.string.feature_debloater_api_filter_chip_launcher
                ComponentClassification.WAKELOCK -> R.string.feature_debloater_api_filter_chip_wakelock
                ComponentClassification.AUTO_START -> R.string.feature_debloater_api_filter_chip_auto_start
                ComponentClassification.EXPORTED_NO_PERM -> R.string.feature_debloater_api_filter_chip_exported_no_perm
                ComponentClassification.FOREGROUND_SERVICE -> R.string.feature_debloater_api_filter_chip_foreground_service
                ComponentClassification.PUSH_SERVICE -> R.string.feature_debloater_api_filter_chip_push_service
                ComponentClassification.DANGEROUS_PROVIDER -> R.string.feature_debloater_api_filter_chip_dangerous_provider
            }
            BlockerFilterChip(
                selected = type in selectedTypes,
                onSelectedChange = { onTypeToggle(type) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@PreviewThemes
@Composable
private fun ComponentTypeFilterChipsPreviewAllSelected() {
    BlockerTheme {
        Surface {
            ComponentTypeFilterChips(
                selectedTypes = setOf(
                    ComponentClassification.SHAREABLE,
                    ComponentClassification.DEEPLINK,
                    ComponentClassification.LAUNCHER,
                ),
                onTypeToggle = {},
            )
        }
    }
}

@PreviewThemes
@Composable
private fun ComponentTypeFilterChipsPreviewPartialSelection() {
    BlockerTheme {
        Surface {
            ComponentTypeFilterChips(
                selectedTypes = setOf(
                    ComponentClassification.SHAREABLE,
                    ComponentClassification.LAUNCHER,
                ),
                onTypeToggle = {},
            )
        }
    }
}

@Composable
@PreviewThemes
private fun DebloaterScreenContentPreview(
    @PreviewParameter(DebloaterPreviewParameterProvider::class)
    list: List<MatchedTarget>,
) {
    BlockerTheme {
        Surface {
            DebloaterScreenContent(
                debloatableUiState = Result.Success(list),
                searchQuery = "",
            )
        }
    }
}

@Composable
@Preview
private fun DebloaterScreenContentWithSearchPreview() {
    BlockerTheme {
        Surface {
            DebloaterScreenContent(
                debloatableUiState = Result.Success(DebloaterPreviewParameterData.debloaterList),
                searchQuery = "Main",
            )
        }
    }
}

@Composable
@Preview
private fun DebloaterScreenContentWithErrorDialogPreview() {
    BlockerTheme {
        Surface {
            DebloaterScreenContent(
                debloatableUiState = Result.Success(DebloaterPreviewParameterData.debloaterList),
                searchQuery = "",
                errorState = UiMessage(
                    title = "Error",
                    content = "Failed to disable component",
                ),
            )
        }
    }
}

@Composable
@Preview
private fun DebloaterScreenContentLoadingPreview() {
    BlockerTheme {
        Surface {
            DebloaterScreenContent(
                debloatableUiState = Result.Loading,
                searchQuery = "",
            )
        }
    }
}
