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

package com.merxury.blocker.feature.onlinerules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.ExperimentalLifecycleComposeApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.database.generalrule.GeneralRuleEntity
import com.merxury.blocker.core.designsystem.component.BlockerHomeTopAppBar
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.feature.onlinerules.component.RuleCard
import com.merxury.blocker.feature.onlinerules.model.OnlineRulesUiState
import com.merxury.blocker.feature.onlinerules.model.OnlineRulesViewModel

@OptIn(ExperimentalLifecycleComposeApi::class)
@Composable
fun OnlineRulesRoute(
    viewModel: OnlineRulesViewModel = hiltViewModel()
) {
    val uiState by viewModel.onlineRulesUiState.collectAsStateWithLifecycle()
    OnlineRulesScreen(uiState = uiState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineRulesScreen(
    modifier: Modifier = Modifier,
    uiState: OnlineRulesUiState
) {
    Column {
        BlockerHomeTopAppBar(titleRes = R.string.online_rules, actions = {})
        when (uiState) {
            OnlineRulesUiState.Loading -> {
                Column(
                    modifier = modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    BlockerLoadingWheel(
                        modifier = modifier,
                        contentDesc = stringResource(id = R.string.loading),
                    )
                }
            }

            is OnlineRulesUiState.OnlineRulesResult -> {
                OnlineRulesContent(uiState = uiState)
            }

            is OnlineRulesUiState.Error -> {
                ErrorScreen(message = uiState.message)
            }
        }
    }
}

@Composable
fun ErrorScreen(message: ErrorMessage) {
    Text(text = message.message)
}

@Composable
fun OnlineRulesContent(
    modifier: Modifier = Modifier,
    uiState: OnlineRulesUiState.OnlineRulesResult
) {
    val listContent = remember { uiState.rules }
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier,
        state = listState
    ) {
        items(listContent, key = { it.id }) {
            RuleCard(item = it)
        }
    }
}

@Composable
@Preview
fun OnlineRulesScreenPreview() {
    val uiState = OnlineRulesUiState.OnlineRulesResult(
        listOf(
            GeneralRuleEntity(
                id = 100,
                name = "Blocker",
                iconUrl = null,
                company = "Merxury blocker",
                description = "Merxury Merxury Merxury Merxury Merxury Merxury Merxury Merxury",
                sideEffect = "unknown",
                safeToBlock = true,
                contributors = listOf("blocker")
            ),
            GeneralRuleEntity(
                id = 10,
                name = "Blocker2",
                iconUrl = null,
                company = "Blocker Merxury",
                description = "Blocker Blocker Blocker Blocker Blocker",
                sideEffect = "Blocker",
                safeToBlock = false,
                contributors = listOf("merxury")
            )
        )
    )
    BlockerTheme {
        Surface {
            OnlineRulesScreen(uiState = uiState)
        }
    }
}
