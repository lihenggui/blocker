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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerDropdownMenu
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.core.ui.screen.LoadingScreen

@Composable
fun GlobalIfwRuleScreen(
    onAddRuleClick: () -> Unit,
    onEditRuleClick: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GlobalIfwRuleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    GlobalIfwRuleScreen(
        uiState = uiState,
        onAddRuleClick = onAddRuleClick,
        onEditRuleClick = onEditRuleClick,
        onDeleteRule = viewModel::deleteRule,
        modifier = modifier,
    )
}

@Composable
internal fun GlobalIfwRuleScreen(
    uiState: GlobalIfwRuleUiState,
    onAddRuleClick: () -> Unit,
    onEditRuleClick: (String, Int) -> Unit,
    onDeleteRule: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            BlockerTopAppBar(
                title = stringResource(R.string.feature_globalifwrule_impl_title),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRuleClick) {
                Icon(
                    imageVector = BlockerIcons.Add,
                    contentDescription = stringResource(R.string.feature_globalifwrule_impl_add_rule),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        when (uiState) {
            is GlobalIfwRuleUiState.Loading -> {
                LoadingScreen(modifier = Modifier.padding(padding))
            }

            is GlobalIfwRuleUiState.Error -> {
                ErrorContent(
                    message = uiState.message,
                    modifier = Modifier.padding(padding),
                )
            }

            is GlobalIfwRuleUiState.Success -> {
                if (uiState.groups.isEmpty()) {
                    EmptyContent(modifier = Modifier.padding(padding))
                } else {
                    RuleListContent(
                        groups = uiState.groups,
                        onEditRuleClick = onEditRuleClick,
                        onDeleteRule = onDeleteRule,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.feature_globalifwrule_impl_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.feature_globalifwrule_impl_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun RuleListContent(
    groups: List<PackageRuleGroup>,
    onEditRuleClick: (String, Int) -> Unit,
    onDeleteRule: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        groups.forEach { group ->
            item(key = "header_${group.packageName}") {
                PackageHeader(group = group)
            }
            items(
                items = group.rules,
                key = { "${group.packageName}_${it.ruleIndex}" },
            ) { rule ->
                RuleItem(
                    packageName = group.packageName,
                    rule = rule,
                    onClick = {
                        if (!rule.isAdvancedRule) {
                            onEditRuleClick(group.packageName, rule.ruleIndex)
                        }
                    },
                    onDelete = { onDeleteRule(group.packageName, rule.ruleIndex) },
                )
            }
            item(key = "divider_${group.packageName}") {
                HorizontalDivider()
            }
        }
        item {
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
        }
    }
}

@Composable
private fun PackageHeader(
    group: PackageRuleGroup,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            info = group.packageInfo,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.appLabel ?: group.packageName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (group.appLabel != null) {
                Text(
                    text = group.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun RuleItem(
    packageName: String,
    rule: RuleItemUiState,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { },
                label = {
                    Text(
                        text = stringResource(rule.componentType.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
            if (rule.block) {
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = stringResource(R.string.feature_globalifwrule_impl_block),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
            if (rule.log) {
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = stringResource(R.string.feature_globalifwrule_impl_log),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
            if (rule.isAdvancedRule) {
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = stringResource(R.string.feature_globalifwrule_impl_advanced_rule),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        }
        if (rule.filtersSummary.isNotBlank()) {
            Text(
                text = rule.filtersSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        BlockerDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            items = listOf(R.string.feature_globalifwrule_impl_delete_rule),
            onItemClick = {
                showMenu = false
                showDeleteDialog = true
            },
            itemText = { item -> Text(stringResource(item)) },
        )
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_globalifwrule_impl_delete_confirm)) },
        text = { Text(stringResource(R.string.feature_globalifwrule_impl_delete_confirm_message)) },
        confirmButton = {
            BlockerTextButton(onClick = onConfirm) {
                Text(stringResource(R.string.feature_globalifwrule_impl_delete))
            }
        },
        dismissButton = {
            BlockerTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.feature_globalifwrule_impl_cancel))
            }
        },
        modifier = modifier,
    )
}
