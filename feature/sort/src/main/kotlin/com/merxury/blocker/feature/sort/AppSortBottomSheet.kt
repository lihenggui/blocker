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

package com.merxury.blocker.feature.sort

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.merxury.blocker.core.ui.ItemHeader
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.sort.AppSortInfoUiState.Loading
import com.merxury.blocker.feature.sort.AppSortInfoUiState.Success
import com.merxury.blocker.feature.sort.R.string
import java.lang.Boolean.FALSE
import java.lang.Boolean.TRUE

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
        scrimColor = Color.Transparent,
    ) {
        ComponentSortBottomSheet(
            uiState = uiState,
            modifier = modifier,
            onSortByClick = viewModel::updateAppSorting,
            onSortOrderClick = viewModel::updateAppSortingOrder,
            onChangeShowRunningAppsOnTop = viewModel::updateShowRunningAppsOnTop,
        )
    }
}

@Composable
fun ComponentSortBottomSheet(
    uiState: AppSortInfoUiState,
    modifier: Modifier = Modifier,
    onSortByClick: (AppSorting) -> Unit = {},
    onSortOrderClick: (SortingOrder) -> Unit = {},
    onChangeShowRunningAppsOnTop: (Boolean) -> Unit = {},
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
                onChangeShowRunningAppsOnTop = onChangeShowRunningAppsOnTop,
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
    onChangeShowRunningAppsOnTop: (Boolean) -> Unit = {},
) {
    val sortModeList = listOf(
        NAME to string.feature_sort_name,
        AppSorting.FIRST_INSTALL_TIME to string.feature_sort_install_date,
        AppSorting.LAST_UPDATE_TIME to string.feature_sort_last_updated,
    )
    val sortByRuleList = listOf(
        ASCENDING to string.feature_sort_ascending,
        DESCENDING to string.feature_sort_descending,
    )
    val showRunningAppsOnTopList = listOf(
        TRUE to string.feature_sort_on,
        FALSE to string.feature_sort_off,
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = string.feature_sort_sort_options),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        ItemHeader(title = stringResource(id = string.feature_sort_sort_by))
        SegmentedButtons(
            items = sortModeList,
            selectedValue = uiState.appSortInfo.sorting,
            onItemSelection = onSortByClick,
        )
        ItemHeader(title = stringResource(id = string.feature_sort_order))
        SegmentedButtons(
            items = sortByRuleList,
            selectedValue = uiState.appSortInfo.order,
            onItemSelection = onSortOrderClick,
        )
        ItemHeader(title = stringResource(id = string.feature_sort_show_running_apps_on_top))
        SegmentedButtons(
            items = showRunningAppsOnTopList,
            selectedValue = uiState.appSortInfo.showRunningAppsOnTop,
            onItemSelection = onChangeShowRunningAppsOnTop,
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
