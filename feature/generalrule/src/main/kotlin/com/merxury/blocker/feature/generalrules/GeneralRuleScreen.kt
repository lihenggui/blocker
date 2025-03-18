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

package com.merxury.blocker.feature.generalrules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.analytics.LocalAnalyticsHelper
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBarWithProgress
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider
import com.merxury.blocker.core.ui.rule.GeneralRulesList
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.generalrule.R
import com.merxury.blocker.feature.generalrules.GeneralRuleUiState.Error
import com.merxury.blocker.feature.generalrules.GeneralRuleUiState.Loading
import com.merxury.blocker.feature.generalrules.GeneralRuleUiState.Success

@Composable
fun GeneralRulesScreen(
    navigateToRuleDetail: (String) -> Unit,
    highlightSelectedRule: Boolean = false,
    viewModel: GeneralRulesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()
    GeneralRulesScreen(
        highlightSelectedRule = highlightSelectedRule,
        uiState = uiState,
        navigateToRuleDetail = {
            viewModel.onRuleClick(it)
            navigateToRuleDetail(it)
        },
    )
    if (errorState != null) {
        BlockerErrorAlertDialog(
            title = errorState?.title.orEmpty(),
            text = errorState?.content.orEmpty(),
            onDismissRequest = viewModel::dismissAlert,
        )
    }
}

@Composable
fun GeneralRulesScreen(
    uiState: GeneralRuleUiState,
    modifier: Modifier = Modifier,
    highlightSelectedRule: Boolean = false,
    navigateToRuleDetail: (String) -> Unit = {},
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BlockerTopAppBarWithProgress(
            title = stringResource(id = R.string.feature_generalrule_sdk_trackers),
            progress = if (uiState is Success) {
                uiState.matchProgress
            } else {
                null
            },
        )
        val analyticsHelper = LocalAnalyticsHelper.current
        when (uiState) {
            Loading -> {
                LoadingScreen()
            }

            is Success -> GeneralRulesList(
                matchedRules = uiState.matchedRules,
                unmatchedRules = uiState.unmatchedRules,
                highlightSelectedRule = highlightSelectedRule,
                selectedRuleId = uiState.selectedRuleId,
                onClick = { id ->
                    navigateToRuleDetail(id)
                    analyticsHelper.logGeneralRuleClicked(id)
                },
            )

            is Error -> ErrorScreen(error = uiState.error)
        }
    }
    TrackScreenViewEvent(screenName = "GeneralRulesScreen")
}

@Composable
@PreviewThemes
private fun GeneralRuleScreenMatchProgressPreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    BlockerTheme {
        GeneralRulesScreen(
            uiState = Success(
                matchedRules = ruleList.filter { it.matchedAppCount > 0 },
                unmatchedRules = ruleList.filter { it.matchedAppCount == 0 },
                matchProgress = 0.5F,
            ),
        )
    }
}

@Composable
@Preview
private fun GeneralRuleScreenMatchedCompletedPreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    BlockerTheme {
        GeneralRulesScreen(
            uiState = Success(
                matchedRules = ruleList.filter { it.matchedAppCount > 0 },
                unmatchedRules = ruleList.filter { it.matchedAppCount == 0 },
                matchProgress = 1F,
            ),
        )
    }
}

@Composable
@Preview
private fun GeneralRuleScreenMatchStartPreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    BlockerTheme {
        GeneralRulesScreen(
            uiState = Success(
                matchedRules = ruleList.filter { it.matchedAppCount > 0 },
                unmatchedRules = ruleList.filter { it.matchedAppCount == 0 },
                matchProgress = 0F,
            ),
        )
    }
}

@Composable
@PreviewThemes
private fun GeneralRuleScreenLoading() {
    BlockerTheme {
        Surface {
            GeneralRulesScreen(
                uiState = Loading,
            )
        }
    }
}

@Composable
@PreviewThemes
private fun GeneralRuleScreenError() {
    BlockerTheme {
        Surface {
            GeneralRulesScreen(
                uiState = Error(
                    error = UiMessage("Error"),
                ),
            )
        }
    }
}
