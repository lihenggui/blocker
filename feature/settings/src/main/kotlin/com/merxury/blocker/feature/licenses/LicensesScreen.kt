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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.PreviewDevices
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.licenses.LicensesUiState.Loading
import com.merxury.blocker.feature.licenses.LicensesUiState.Success
import com.merxury.blocker.feature.settings.R

@Composable
fun LicensesRoute(
    onNavigationClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: LicensesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.licensesUiState.collectAsStateWithLifecycle()
    LicenseScreen(
        onNavigationClick = onNavigationClick,
        snackbarHostState = snackbarHostState,
        uiState = uiState,
    )
}

@Composable
fun LicenseScreen(
    uiState: LicensesUiState,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    onNavigationClick: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            BlockerTopAppBar(
                title = stringResource(id = R.string.feature_settings_open_source_licenses),
                hasNavigationIcon = true,
                onNavigationClick = onNavigationClick,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal,
                    ),
                ),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (uiState) {
                Loading -> {
                    LoadingScreen()
                }

                is Success -> {
                    LicenseContent(uiState)
                }
            }
        }
    }
}

@Composable
fun LicenseContent(
    uiState: LicensesUiState,
) {
}

@Composable
@PreviewDevices
private fun LicensesScreenPreview() {
    BlockerTheme {
        Surface {
            LicenseScreen(
                uiState = Loading,
            )
        }
    }
}
