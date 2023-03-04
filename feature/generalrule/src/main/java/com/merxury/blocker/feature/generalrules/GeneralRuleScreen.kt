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

package com.merxury.blocker.feature.generalrules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.rule.GeneralRulesList
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.generalrule.R
import com.merxury.blocker.feature.generalrules.model.GeneralRuleUiState
import com.merxury.blocker.feature.generalrules.model.GeneralRuleUiState.Error
import com.merxury.blocker.feature.generalrules.model.GeneralRuleUiState.Loading
import com.merxury.blocker.feature.generalrules.model.GeneralRuleUiState.Success
import com.merxury.blocker.feature.generalrules.model.GeneralRulesViewModel

@Composable
fun GeneralRulesRoute(
    navigateToRuleDetail: (Int) -> Unit,
    viewModel: GeneralRulesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()
    GeneralRulesScreen(
        uiState = uiState,
        navigateToRuleDetail = navigateToRuleDetail,
    )
    if (errorState != null) {
        BlockerErrorAlertDialog(
            title = errorState?.title.orEmpty(),
            text = errorState?.content.orEmpty(),
            onDismissRequest = viewModel::dismissAlert,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeneralRulesScreen(
    modifier: Modifier = Modifier,
    uiState: GeneralRuleUiState,
    navigateToRuleDetail: (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            BlockerTopAppBar(title = stringResource(id = R.string.rules))
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .consumeWindowInsets(padding)
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

                is Success -> GeneralRulesList(
                    rules = uiState.rules,
                    onClick = navigateToRuleDetail,
                )

                is Error -> ErrorScreen(error = uiState.error)
            }
        }
    }
}

@Composable
@Preview
fun GeneralRuleScreenPreview() {
    val uiState = Success(
        listOf(
            GeneralRule(
                id = 1,
                name = "AWS SDK for Kotlin (Developer Preview)",
                iconUrl = null,
                company = "Amazon",
                description = "The AWS SDK for Kotlin simplifies the use of AWS services by " +
                    "providing a set of libraries that are consistent and familiar for " +
                    "Kotlin developers. All AWS SDKs support API lifecycle considerations " +
                    "such as credential management, retries, data marshaling, and serialization.",
                sideEffect = "Unknown",
                safeToBlock = true,
                contributors = listOf("Online contributor"),
                searchKeyword = listOf("androidx.google.example1"),
            ),
            GeneralRule(
                id = 2,
                name = "Android WorkerManager",
                iconUrl = null,
                company = "Google",
                description = "WorkManager is the recommended solution for persistent work. " +
                    "Work is persistent when it remains scheduled through app restarts and " +
                    "system reboots. Because most background processing is best accomplished " +
                    "through persistent work, WorkManager is the primary recommended API for " +
                    "background processing.",
                sideEffect = "Background works won't be able to execute",
                safeToBlock = false,
                contributors = listOf("Google"),
                searchKeyword = listOf(
                    "androidx.google.example1",
                    "androidx.google.example2",
                    "androidx.google.example3",
                    "androidx.google.example4",
                ),
            ),
        ),
    )
    BlockerTheme {
        GeneralRulesScreen(uiState = uiState, navigateToRuleDetail = {})
    }
}
