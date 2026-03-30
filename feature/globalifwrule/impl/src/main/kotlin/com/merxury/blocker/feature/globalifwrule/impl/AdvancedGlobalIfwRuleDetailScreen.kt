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

package com.merxury.blocker.feature.globalifwrule.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.AdvancedRuleDetailUiState
import com.merxury.blocker.core.ui.previewparameter.AdvancedRuleDetailPreviewParameterProvider
import com.merxury.blocker.feature.globalifwrule.api.R

@Composable
internal fun AdvancedGlobalIfwRuleDetailScreen(
    detail: AdvancedRuleDetailUiState,
    onBack: () -> Unit,
    onCopyAsNew: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val detailTitle = detail.presentation.title
        ?: stringResource(R.string.feature_globalifwrule_api_advanced_rule)

    Scaffold(
        topBar = {
            BlockerTopAppBar(
                title = detailTitle,
                hasNavigationIcon = true,
                onNavigationClick = onBack,
            )
        },
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AdvancedRuleOverviewSection(detail = detail)
            }
            if (detail.draft.hasReadOnlyIntentFilters) {
                item {
                    AdvancedRuleReadOnlyNotice()
                }
            }
            item {
                AdvancedRuleConditionsSection(detail = detail)
            }
            item {
                AdvancedRuleDetailActions(
                    onCopyAsNew = onCopyAsNew,
                    onDelete = { showDeleteDialog = true },
                )
            }
            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = onDelete,
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedRuleOverviewSection(
    detail: AdvancedRuleDetailUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        detail.presentation.targetPath?.let { targetPath ->
            RulePrimaryMetadataText(
                text = targetPath,
                maxLines = 2,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RuleMetaBadge(
                text = stringResource(detail.componentType.labelRes),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (detail.block) {
                RuleMetaBadge(
                    text = stringResource(R.string.feature_globalifwrule_api_block),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (detail.log) {
                RuleMetaBadge(
                    text = stringResource(R.string.feature_globalifwrule_api_log),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        RuleSecondaryMetadataText(
            text = stringResource(
                R.string.feature_globalifwrule_api_advanced_detail_summary,
                detail.storagePackageName,
            ),
            maxLines = 2,
        )
    }
}

@Composable
private fun AdvancedRuleReadOnlyNotice(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = stringResource(R.string.feature_globalifwrule_api_intent_filters_preserved),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun AdvancedRuleConditionsSection(
    detail: AdvancedRuleDetailUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.feature_globalifwrule_api_conditions),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
        ) {
            SelectionContainer {
                Text(
                    text = detail.presentation.conditionLines.joinToString(separator = "\n"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun AdvancedRuleDetailActions(
    onCopyAsNew: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            BlockerTextButton(onClick = onCopyAsNew) {
                Text(
                    text = stringResource(R.string.feature_globalifwrule_api_copy_as_new_advanced_rule),
                )
            }
            BlockerTextButton(onClick = onDelete) {
                Text(
                    text = stringResource(R.string.feature_globalifwrule_api_delete_rule),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AdvancedGlobalIfwRuleDetailScreenPreviewContainer(
    content: @Composable () -> Unit,
) {
    BlockerTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
@PreviewThemes
private fun AdvancedGlobalIfwRuleDetailScreenPreview(
    @PreviewParameter(AdvancedRuleDetailPreviewParameterProvider::class)
    detail: AdvancedRuleDetailUiState,
) {
    AdvancedGlobalIfwRuleDetailScreenPreviewContainer {
        AdvancedGlobalIfwRuleDetailScreen(
            detail = detail,
            onBack = {},
            onCopyAsNew = {},
            onDelete = {},
        )
    }
}
