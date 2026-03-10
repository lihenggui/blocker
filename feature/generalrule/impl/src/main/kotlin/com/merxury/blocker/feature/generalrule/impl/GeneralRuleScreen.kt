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

package com.merxury.blocker.feature.generalrule.impl

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarDuration.Short
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.analytics.LocalAnalyticsHelper
import com.merxury.blocker.core.designsystem.component.BlockerAppTopBarMenu
import com.merxury.blocker.core.designsystem.component.BlockerErrorAlertDialog
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBarWithProgress
import com.merxury.blocker.core.designsystem.component.BlockerWarningAlertDialog
import com.merxury.blocker.core.designsystem.component.DropDownMenuItem
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.ui.TrackScreenViewEvent
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.previewparameter.RuleListPreviewParameterProvider
import com.merxury.blocker.core.ui.rule.GeneralRulesList
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.merxury.blocker.core.ui.R.string as uistring
import com.merxury.blocker.feature.generalrule.api.R.string as generalruleString

@Composable
fun GeneralRulesScreen(
    navigateToRuleDetail: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    highlightSelectedRule: Boolean = false,
    viewModel: GeneralRulesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    GeneralRulesScreen(
        highlightSelectedRule = highlightSelectedRule,
        uiState = uiState,
        isProcessing = isProcessing,
        navigateToRuleDetail = {
            viewModel.onRuleClick(it)
            navigateToRuleDetail(it)
        },
        onBlockAllClick = {
            viewModel.controlAllComponents(false) { current, total ->
                showDisableProgress(context, snackbarHostState, scope, current, total)
            }
        },
        onEnableAllClick = {
            viewModel.controlAllComponents(true) { current, total ->
                showEnableProgress(context, snackbarHostState, scope, current, total)
            }
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
    onBlockAllClick: () -> Unit = {},
    onEnableAllClick: () -> Unit = {},
    isProcessing: Boolean = false,
) {
    var showBlockAllDialog by rememberSaveable { mutableStateOf(false) }
    var showEnableAllDialog by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BlockerTopAppBarWithProgress(
            title = stringResource(id = generalruleString.feature_generalrule_api_sdk_trackers),
            progress = if (uiState is GeneralRuleUiState.Success) {
                uiState.matchProgress
            } else {
                null
            },
            actions = {
                if (uiState is GeneralRuleUiState.Success) {
                    GeneralRulesMoreActionMenu(
                        enabled = uiState.matchProgress == 1F && !isProcessing,
                        onBlockAllClick = { showBlockAllDialog = true },
                        onEnableAllClick = { showEnableAllDialog = true },
                    )
                }
            },
        )
        val analyticsHelper = LocalAnalyticsHelper.current
        when (uiState) {
            GeneralRuleUiState.Loading -> {
                LoadingScreen()
            }

            is GeneralRuleUiState.Success -> GeneralRulesList(
                matchedRules = uiState.matchedRules,
                unmatchedRules = uiState.unmatchedRules,
                highlightSelectedRule = highlightSelectedRule,
                selectedRuleId = uiState.selectedRuleId,
                onClick = { id ->
                    navigateToRuleDetail(id)
                    analyticsHelper.logGeneralRuleClicked(id)
                },
            )

            is GeneralRuleUiState.Error -> ErrorScreen(error = uiState.error)
        }
    }
    if (showBlockAllDialog) {
        BlockerWarningAlertDialog(
            title = stringResource(generalruleString.feature_generalrule_api_block_all_matched),
            text = stringResource(generalruleString.feature_generalrule_api_block_all_confirmation),
            onDismissRequest = { showBlockAllDialog = false },
            onConfirmRequest = {
                showBlockAllDialog = false
                onBlockAllClick()
            },
        )
    }
    if (showEnableAllDialog) {
        BlockerWarningAlertDialog(
            title = stringResource(generalruleString.feature_generalrule_api_enable_all_matched),
            text = stringResource(generalruleString.feature_generalrule_api_enable_all_confirmation),
            onDismissRequest = { showEnableAllDialog = false },
            onConfirmRequest = {
                showEnableAllDialog = false
                onEnableAllClick()
            },
        )
    }
    TrackScreenViewEvent(screenName = "GeneralRulesScreen")
}

@Composable
private fun GeneralRulesMoreActionMenu(
    enabled: Boolean,
    onBlockAllClick: () -> Unit,
    onEnableAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val items = listOf(
        DropDownMenuItem(
            generalruleString.feature_generalrule_api_block_all_matched,
            onBlockAllClick,
        ),
        DropDownMenuItem(
            generalruleString.feature_generalrule_api_enable_all_matched,
            onEnableAllClick,
        ),
    )
    BlockerAppTopBarMenu(
        modifier = modifier,
        menuIcon = BlockerIcons.MoreVert,
        menuIconDesc = uistring.core_ui_more_menu,
        menuList = items,
    )
}

private fun showEnableProgress(
    context: Context,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    current: Int,
    total: Int,
) {
    scope.launch {
        if (current == total) {
            snackbarHostState.showSnackbar(
                message = context.getString(uistring.core_ui_operation_completed),
                duration = Short,
                withDismissAction = true,
            )
        } else {
            snackbarHostState.showSnackbar(
                message = context.getString(
                    uistring.core_ui_enabling_component_hint,
                    current,
                    total,
                ),
                duration = Short,
                withDismissAction = false,
            )
        }
    }
}

private fun showDisableProgress(
    context: Context,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    current: Int,
    total: Int,
) {
    scope.launch {
        if (current == total) {
            snackbarHostState.showSnackbar(
                message = context.getString(uistring.core_ui_operation_completed),
                duration = Short,
                withDismissAction = true,
            )
        } else {
            snackbarHostState.showSnackbar(
                message = context.getString(
                    uistring.core_ui_disabling_component_hint,
                    current,
                    total,
                ),
                duration = Short,
                withDismissAction = false,
            )
        }
    }
}

@Composable
@PreviewThemes
private fun GeneralRuleScreenMatchProgressPreview(
    @PreviewParameter(RuleListPreviewParameterProvider::class)
    ruleList: List<GeneralRule>,
) {
    BlockerTheme {
        GeneralRulesScreen(
            uiState = GeneralRuleUiState.Success(
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
            uiState = GeneralRuleUiState.Success(
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
            uiState = GeneralRuleUiState.Success(
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
                uiState = GeneralRuleUiState.Loading,
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
                uiState = GeneralRuleUiState.Error(
                    error = UiMessage("Error"),
                ),
            )
        }
    }
}
