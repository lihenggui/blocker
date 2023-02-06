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
import androidx.compose.foundation.layout.consumedWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.feature.generalrule.R
import com.merxury.blocker.feature.generalrules.component.RuleCard
import com.merxury.blocker.feature.generalrules.model.GeneralRuleUiState
import com.merxury.blocker.feature.generalrules.model.GeneralRuleUiState.Error
import com.merxury.blocker.feature.generalrules.model.GeneralRuleUiState.Loading
import com.merxury.blocker.feature.generalrules.model.GeneralRuleUiState.Success
import com.merxury.blocker.feature.generalrules.model.GeneralRulesViewModel

@Composable
fun GeneralRulesRoute(
    viewModel: GeneralRulesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()
    GeneralRulesScreen(uiState = uiState)
    if (errorState != null) {
        BlockerErrorAlertDialog(
            title = errorState?.message.orEmpty(),
            text = errorState?.stackTrace.orEmpty(),
            onDismissRequest = viewModel::dismissAlert,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeneralRulesScreen(
    modifier: Modifier = Modifier,
    uiState: GeneralRuleUiState,
) {
    Scaffold(
        topBar = {
            BlockerTopAppBar(title = stringResource(id = R.string.general_rules))
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .consumedWindowInsets(padding)
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
                    Column(
                        modifier = modifier
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        BlockerLoadingWheel(
                            modifier = modifier,
                            contentDesc = stringResource(id = R.string.loading),
                        )
                    }
                }

                is Success -> {
                    GeneralRulesContent(uiState = uiState)
                }

                is Error -> {
                    ErrorScreen(message = uiState.message)
                }
            }
        }
    }
}

@Composable
fun ErrorScreen(message: ErrorMessage) {
    Text(text = message.message)
}

@Composable
fun GeneralRulesContent(
    modifier: Modifier = Modifier,
    uiState: Success,
) {
    val listContent = remember { uiState.rules }
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier,
        state = listState,
    ) {
        items(listContent, key = { it.id }) {
            RuleCard(item = it)
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
            ),
        ),
    )
    BlockerTheme {
        GeneralRulesScreen(uiState = uiState)
    }
}
