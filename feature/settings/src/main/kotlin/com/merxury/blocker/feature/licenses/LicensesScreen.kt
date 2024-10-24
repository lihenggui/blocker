/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.feature.licenses

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.component.scrollbar.DraggableScrollbar
import com.merxury.blocker.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.merxury.blocker.core.designsystem.component.scrollbar.scrollbarState
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.LicenseGroup
import com.merxury.blocker.core.ui.BlockerSettingItem
import com.merxury.blocker.core.ui.ItemHeader
import com.merxury.blocker.core.ui.PreviewDevices
import com.merxury.blocker.core.ui.TrackScrollJank
import com.merxury.blocker.core.ui.previewparameter.LicensesPreviewParameterProvider
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.licenses.LicensesUiState.Loading
import com.merxury.blocker.feature.licenses.LicensesUiState.Success
import com.merxury.blocker.feature.settings.R

@Composable
fun LicensesScreen(
    onNavigationClick: () -> Unit,
    viewModel: LicensesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.licensesUiState.collectAsStateWithLifecycle()
    LicenseScreen(
        onNavigationClick = onNavigationClick,
        uiState = uiState,
    )
}

@Composable
fun LicenseScreen(
    uiState: LicensesUiState,
    modifier: Modifier = Modifier,
    onNavigationClick: () -> Unit = {},
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BlockerTopAppBar(
            title = stringResource(id = R.string.feature_settings_open_source_licenses),
            hasNavigationIcon = true,
            onNavigationClick = onNavigationClick,
        )

        when (uiState) {
            Loading -> {
                LoadingScreen()
            }

            is Success -> {
                LicenseContent(licenses = uiState.licenses, licensesSize = uiState.licensesSize)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LicenseContent(
    licenses: List<LicenseGroup>,
    licensesSize: Int,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scrollbarState = listState.scrollbarState(
        itemsAvailable = licensesSize,
    )
    TrackScrollJank(scrollableState = listState, stateName = "licenses:list")
    Box(modifier.fillMaxSize()) {
        LazyColumn(state = listState) {
            licenses.forEach { group ->
                stickyHeader {
                    ItemHeader(
                        title = group.id,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                    )
                }
                items(group.artifacts) { artifact ->
                    BlockerSettingItem(
                        title = (artifact.name ?: artifact.artifactId),
                        itemDesc = "${artifact.artifactId} v${artifact.version}",
                        itemDesc1 = artifact.spdxLicenses?.joinToString { it.name },
                    )
                }
            }
        }
        listState.DraggableScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd)
                .testTag("licenses:scrollbar"),
            state = scrollbarState,
            orientation = Orientation.Vertical,
            onThumbMove = listState.rememberDraggableScroller(
                itemsAvailable = licensesSize,
            ),
        )
    }
}

@Composable
@PreviewDevices
private fun LicensesScreenPreview(
    @PreviewParameter(LicensesPreviewParameterProvider::class)
    licenses: List<LicenseGroup>,
) {
    BlockerTheme {
        Surface {
            LicenseScreen(
                uiState = Success(licenses),
            )
        }
    }
}

@Composable
@Preview
private fun LicensesScreenWithLoadingPreview() {
    BlockerTheme {
        Surface {
            LicenseScreen(
                uiState = Loading,
            )
        }
    }
}
