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

package com.merxury.blocker.feature.applist.impl.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.segmentedbuttons.SegmentedButtons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.AppSortInfo
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.AppSorting.NAME
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.SortingOrder.ASCENDING
import com.merxury.blocker.core.model.preference.SortingOrder.DESCENDING
import com.merxury.blocker.core.model.preference.TopAppType
import com.merxury.blocker.core.ui.ItemHeader
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.applist.api.R.string
import com.merxury.blocker.feature.applist.impl.AppSortInfoUiState
import com.merxury.blocker.feature.applist.impl.AppSortInfoUiState.Loading
import com.merxury.blocker.feature.applist.impl.AppSortInfoUiState.Success
import com.merxury.blocker.feature.applist.impl.AppSortViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSortBottomSheetRoute(
    dismissHandler: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppSortViewModel = hiltViewModel(),
) {
    val uiState by viewModel.appSortInfoUiState.collectAsStateWithLifecycle()
    val skipPartiallyExpanded by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
    )
    ModalBottomSheet(
        onDismissRequest = { dismissHandler() },
        sheetState = bottomSheetState,
    ) {
        ComponentSortBottomSheet(
            uiState = uiState,
            modifier = modifier,
            onSortByClick = viewModel::updateAppSorting,
            onSortOrderClick = viewModel::updateAppSortingOrder,
            onTopAppTypeChange = viewModel::updateTopAppType,
        )
    }
}

@Composable
fun ComponentSortBottomSheet(
    uiState: AppSortInfoUiState,
    modifier: Modifier = Modifier,
    onSortByClick: (AppSorting) -> Unit = {},
    onSortOrderClick: (SortingOrder) -> Unit = {},
    onTopAppTypeChange: (TopAppType) -> Unit = {},
) {
    when (uiState) {
        Loading -> {
            LoadingScreen(modifier = modifier.height(340.dp))
        }

        is Success -> {
            AppSortOptionsContent(
                uiState = uiState,
                modifier = modifier,
                onSortByClick = onSortByClick,
                onSortOrderClick = onSortOrderClick,
                onTopAppTypeChange = onTopAppTypeChange,
            )
        }
    }
}

@Composable
fun AppSortOptionsContent(
    uiState: Success,
    modifier: Modifier = Modifier,
    onSortByClick: (AppSorting) -> Unit = {},
    onSortOrderClick: (SortingOrder) -> Unit = {},
    onTopAppTypeChange: (TopAppType) -> Unit = {},
) {
    val sortModeList = listOf(
        NAME to string.feature_applist_api_name,
        AppSorting.FIRST_INSTALL_TIME to string.feature_applist_api_install_date,
        AppSorting.LAST_UPDATE_TIME to string.feature_applist_api_last_updated,
    )
    val sortByRuleList = listOf(
        ASCENDING to string.feature_applist_api_ascending,
        DESCENDING to string.feature_applist_api_descending,
    )
    val topAppTypeList = listOf(
        TopAppType.NONE to string.feature_applist_api_none,
        TopAppType.RUNNING to string.feature_applist_api_running_apps,
        TopAppType.DISABLED to string.feature_applist_api_disabled_apps,
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = string.feature_applist_api_sort_options),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        ItemHeader(title = stringResource(id = string.feature_applist_api_sort_by))
        SegmentedButtons(
            items = sortModeList,
            selectedValue = uiState.appSortInfo.sorting,
            onItemSelection = onSortByClick,
        )
        ItemHeader(title = stringResource(id = string.feature_applist_api_order))
        SegmentedButtons(
            items = sortByRuleList,
            selectedValue = uiState.appSortInfo.order,
            onItemSelection = onSortOrderClick,
        )
        ItemHeader(title = stringResource(id = string.feature_applist_api_show_apps_on_top))
        SegmentedButtons(
            items = topAppTypeList,
            selectedValue = uiState.appSortInfo.topAppType,
            onItemSelection = onTopAppTypeChange,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
    TrackScreenViewEvent(screenName = "AppListSortBottomSheet")
}

@Composable
@PreviewThemes
private fun AppSortOptionsBottomSheetPreview() {
    BlockerTheme {
        Surface {
            ComponentSortBottomSheet(
                uiState = Success(AppSortInfo()),
            )
        }
    }
}

@Composable
@PreviewThemes
private fun AppSortOptionsBottomSheetLoadingPreview() {
    BlockerTheme {
        Surface {
            ComponentSortBottomSheet(
                uiState = Loading,
            )
        }
    }
}
