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

package com.merxury.blocker.feature.generalrules

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.analytics.LocalAnalyticsHelper
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBarWithProgress
import com.merxury.blocker.core.designsystem.component.ThemePreviews
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
fun GeneralRulesRoute(
    navigateToRuleDetail: (String) -> Unit,
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

@Composable
fun GeneralRulesScreen(
    modifier: Modifier = Modifier,
    uiState: GeneralRuleUiState,
    navigateToRuleDetail: (String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            BlockerTopAppBarWithProgress(
                title = stringResource(id = R.string.feature_generalrule_rules),
                progress = if (uiState is Success) {
                    uiState.matchProgress
                } else {
                    null
                },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
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
            val analyticsHelper = LocalAnalyticsHelper.current
            when (uiState) {
                Loading -> {
                    LoadingScreen()
                }

                is Success -> GeneralRulesList(
                    matchedRules = uiState.matchedRules,
                    unmatchedRules = uiState.unmatchedRules,
                    onClick = { id ->
                        navigateToRuleDetail(id)
                        analyticsHelper.logGeneralRuleClicked(id)
                    },
                    modifier = modifier,
                )

                is Error -> ErrorScreen(error = uiState.error)
            }
        }
    }
    TrackScreenViewEvent(screenName = "GeneralRulesScreen")
}

@Composable
@ThemePreviews
fun GeneralRuleScreenMatchProgressPreview(
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
fun GeneralRuleScreenMatchedCompletedPreview(
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
fun GeneralRuleScreenMatchStartPreview(
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
@ThemePreviews
fun GeneralRuleScreenLoading() {
    BlockerTheme {
        Surface {
            GeneralRulesScreen(
                uiState = Loading,
            )
        }
    }
}

@Composable
@ThemePreviews
fun GeneralRuleScreenError() {
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
