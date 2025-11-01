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

package com.merxury.blocker.feature.sharefilter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.database.sharetarget.ShareTargetActivityEntity
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerSearchTextField
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.data.UiMessage

@Composable
fun ShareFilterScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: ShareFilterViewModel = hiltViewModel(),
) {
    val shareTargetsUiState by viewModel.shareTargetsUiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()
    val hasRootPermission by viewModel.hasRootPermission.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val noRootPermissionWarning = stringResource(R.string.feature_sharefilter_no_root_permission)

    LaunchedEffect(hasRootPermission) {
        if (!hasRootPermission) {
            snackbarHostState.showSnackbar(
                message = noRootPermissionWarning,
                duration = SnackbarDuration.Long,
            )
        }
    }

    ShareFilterScreenContent(
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        shareTargetsUiState = shareTargetsUiState,
        searchQuery = searchQuery,
        errorState = errorState,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onSwitchClick = viewModel::controlComponent,
        onBlockAllInItemClick = { entities ->
            viewModel.controlAllComponents(entities, enable = false)
        },
        onEnableAllInItemClick = { entities ->
            viewModel.controlAllComponents(entities, enable = true)
        },
        onDismissError = viewModel::dismissError,
        onTestShareClick = {
            viewModel.triggerTestShare(context)
        },
    )
}

@Composable
private fun ShareFilterScreenContent(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    shareTargetsUiState: Result<List<MatchedShareTarget>> = Result.Loading,
    searchQuery: String = "",
    errorState: UiMessage? = null,
    onSearchQueryChange: (String) -> Unit = {},
    onSwitchClick: (ShareTargetActivityEntity, Boolean) -> Unit = { _, _ -> },
    onBlockAllInItemClick: (List<ShareTargetActivityEntity>) -> Unit = {},
    onEnableAllInItemClick: (List<ShareTargetActivityEntity>) -> Unit = {},
    onDismissError: () -> Unit = {},
    onTestShareClick: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            BlockerSearchTextField(
                modifier = Modifier.weight(1f),
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onSearchTrigger = {},
                placeholder = {
                    Text(text = stringResource(R.string.feature_sharefilter_search_hint))
                },
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onTestShareClick) {
                Icon(
                    imageVector = BlockerIcons.Share,
                    contentDescription = stringResource(R.string.feature_sharefilter_test_share),
                )
            }
        }
        ShareFilterContent(
            modifier = Modifier.weight(1f),
            data = shareTargetsUiState,
            onBlockAllInItemClick = onBlockAllInItemClick,
            onEnableAllInItemClick = onEnableAllInItemClick,
            onSwitch = onSwitchClick,
        )
    }

    if (errorState != null) {
        BlockerErrorAlertDialog(
            title = errorState.title.orEmpty(),
            text = errorState.content.orEmpty(),
            onDismissRequest = onDismissError,
        )
    }
}
