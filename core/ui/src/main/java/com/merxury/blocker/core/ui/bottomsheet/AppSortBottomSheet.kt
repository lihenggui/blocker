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
package com.merxury.blocker.core.ui.bottomsheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.ItemHeader
import com.merxury.blocker.core.designsystem.segmentedbuttons.SegmentedButtons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.AppSorting.NAME
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.SortingOrder.ASCENDING
import com.merxury.blocker.core.model.preference.SortingOrder.DESCENDING
import com.merxury.blocker.core.ui.R
import com.merxury.blocker.core.ui.bottomsheet.AppSortInfoUiState.Loading
import com.merxury.blocker.core.ui.bottomsheet.AppSortInfoUiState.Success
import com.merxury.blocker.core.ui.screen.LoadingScreen
import java.lang.Boolean.FALSE
import java.lang.Boolean.TRUE

@Composable
fun AppSortBottomSheet(
    uiState: AppSortInfoUiState,
    modifier: Modifier = Modifier,
    onSortByClick: (AppSorting) -> Unit,
    onSortOrderClick: (SortingOrder) -> Unit,
    onChangeShowRunningAppsOnTop: (Boolean) -> Unit,
) {
    when (uiState) {
        Loading -> {
            LoadingScreen()
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
    onSortByClick: (AppSorting) -> Unit,
    onSortOrderClick: (SortingOrder) -> Unit,
    onChangeShowRunningAppsOnTop: (Boolean) -> Unit,
) {
    val sortModeList = listOf(
        NAME to R.string.name,
        AppSorting.FIRST_INSTALL_TIME to R.string.install_date,
        AppSorting.LAST_UPDATE_TIME to R.string.last_updated,
    )
    val sortByRuleList = listOf(
        ASCENDING to R.string.ascending,
        DESCENDING to R.string.descending,
    )
    val showRunningAppsOnTopList = listOf(
        TRUE to R.string.on,
        FALSE to R.string.off,
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
            selectedValue = uiState.appSortInfo.sorting,
            onItemSelection = onSortByClick,
        )
        ItemHeader(title = stringResource(id = R.string.order))
        SegmentedButtons(
            items = sortByRuleList,
            selectedValue = uiState.appSortInfo.order,
            onItemSelection = onSortOrderClick,
        )
        ItemHeader(title = stringResource(id = R.string.show_running_apps_on_top))
        SegmentedButtons(
            items = showRunningAppsOnTopList,
            selectedValue = uiState.appSortInfo.showRunningAppsOnTop,
            onItemSelection = onChangeShowRunningAppsOnTop,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
@Preview
fun AppSortOptionsBottomSheetPreview() {
    val uiState = Success(AppSortInfo())
    BlockerTheme {
        Surface {
            AppSortOptionsContent(
                uiState = uiState,
                onSortByClick = {},
                onSortOrderClick = {},
                onChangeShowRunningAppsOnTop = {},
            )
        }
    }
}
