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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.ItemHeader
import com.merxury.blocker.core.designsystem.segmentedbuttons.SegmentedButtons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentShowPriority.DISABLED_COMPONENTS_FIRST
import com.merxury.blocker.core.model.preference.ComponentShowPriority.ENABLED_COMPONENTS_FIRST
import com.merxury.blocker.core.model.preference.ComponentShowPriority.NONE
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.ComponentSorting.COMPONENT_NAME
import com.merxury.blocker.core.model.preference.ComponentSorting.PACKAGE_NAME
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.SortingOrder.ASCENDING
import com.merxury.blocker.core.model.preference.SortingOrder.DESCENDING
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.sort.viewmodel.ComponentSortInfo
import com.merxury.blocker.feature.sort.viewmodel.ComponentSortInfoUiState
import com.merxury.blocker.feature.sort.viewmodel.ComponentSortInfoUiState.Loading
import com.merxury.blocker.feature.sort.viewmodel.ComponentSortInfoUiState.Success
import com.merxury.blocker.feature.sort.viewmodel.SortViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentSortBottomSheetRoute(
    dismissHandler: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SortViewModel = hiltViewModel(),
) {
    val uiState by viewModel.componentSortInfoUiState.collectAsStateWithLifecycle()
    val skipPartiallyExpanded by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
    )
    ModalBottomSheet(
        onDismissRequest = { dismissHandler() },
        sheetState = bottomSheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        scrimColor = Color.Transparent,
    ) {
        ComponentSortBottomSheet(
            uiState = uiState,
            modifier = modifier,
            onSortByClick = viewModel::updateComponentSorting,
            onSortOrderClick = viewModel::updateComponentSortingOrder,
            onShowPriorityClick = viewModel::updateComponentShowPriority,
        )
    }
}

@Composable
fun ComponentSortBottomSheet(
    uiState: ComponentSortInfoUiState,
    modifier: Modifier = Modifier,
    onSortByClick: (ComponentSorting) -> Unit,
    onSortOrderClick: (SortingOrder) -> Unit,
    onShowPriorityClick: (ComponentShowPriority) -> Unit,
) {
    when (uiState) {
        Loading -> {
            LoadingScreen()
        }

        is Success -> {
            SortOptionsContent(
                uiState = uiState,
                modifier = modifier,
                onSortByClick = onSortByClick,
                onSortOrderClick = onSortOrderClick,
                onShowPriorityClick = onShowPriorityClick,
            )
        }
    }
}

@Composable
fun SortOptionsContent(
    uiState: Success,
    modifier: Modifier = Modifier,
    onSortByClick: (ComponentSorting) -> Unit,
    onSortOrderClick: (SortingOrder) -> Unit,
    onShowPriorityClick: (ComponentShowPriority) -> Unit,
) {
    val sortModeList = listOf(
        COMPONENT_NAME to R.string.component_name,
        PACKAGE_NAME to R.string.package_name,
    )
    val sortByRuleList = listOf(
        ASCENDING to R.string.ascending,
        DESCENDING to R.string.descending,
    )
    val priorityList = listOf(
        NONE to R.string.none,
        DISABLED_COMPONENTS_FIRST to R.string.disabled_first,
        ENABLED_COMPONENTS_FIRST to R.string.enabled_first,
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = R.string.sort_options),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth(),
        )
        ItemHeader(title = stringResource(id = R.string.sort_by))
        SegmentedButtons(
            items = sortModeList,
            selectedValue = uiState.componentSortInfo.sorting,
            onItemSelection = onSortByClick,
        )
        ItemHeader(title = stringResource(id = R.string.order))
        SegmentedButtons(
            items = sortByRuleList,
            selectedValue = uiState.componentSortInfo.order,
            onItemSelection = onSortOrderClick,
        )
        ItemHeader(title = stringResource(id = R.string.priority))
        SegmentedButtons(
            items = priorityList,
            selectedValue = uiState.componentSortInfo.priority,
            onItemSelection = onShowPriorityClick,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
@Preview
fun SortOptionsBottomSheetPreview() {
    val uiState = Success(ComponentSortInfo())
    BlockerTheme {
        Surface {
            SortOptionsContent(
                uiState = uiState,
                onSortByClick = {},
                onSortOrderClick = {},
                onShowPriorityClick = {},
            )
        }
    }
}
