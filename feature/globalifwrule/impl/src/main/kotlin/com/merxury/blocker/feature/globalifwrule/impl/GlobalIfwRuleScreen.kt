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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerDropdownMenu
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.GlobalIfwRuleEditMode
import com.merxury.blocker.core.model.data.GlobalIfwRuleScreenState
import com.merxury.blocker.core.model.data.GlobalIfwRuleUiState
import com.merxury.blocker.core.model.data.PackageRuleGroup
import com.merxury.blocker.core.model.data.RuleItemUiState
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.previewparameter.GlobalIfwRuleUiStatePreviewParameterProvider
import com.merxury.blocker.core.ui.screen.EmptyScreen
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import com.merxury.blocker.feature.globalifwrule.api.R

@Composable
fun GlobalIfwRuleRoute(
    modifier: Modifier = Modifier,
    viewModel: GlobalIfwRuleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = editorState.screen,
        transitionSpec = {
            when (targetState) {
                GlobalIfwRuleScreenState.LIST -> fadeIn() togetherWith fadeOut()
                else -> slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            }
        },
        label = "globalIfwRuleTransition",
        modifier = modifier,
    ) { screen ->
        when (screen) {
            GlobalIfwRuleScreenState.LIST -> GlobalIfwRuleScreen(
                uiState = uiState,
                onAddSimpleRuleClick = viewModel::startAddingSimpleRule,
                onAddAdvancedRuleClick = viewModel::startAddingAdvancedRule,
                onOpenRuleClick = viewModel::openRule,
                onDeleteRule = viewModel::deleteRule,
            )

            GlobalIfwRuleScreenState.SIMPLE_EDIT -> editorState.simpleDraft?.let { draft ->
                SimpleGlobalIfwRuleScreen(
                    draft = draft,
                    isDirty = editorState.isDirty,
                    selectedPackageLabel = editorState.selectedPackageLabel,
                    componentQuery = editorState.componentQuery,
                    visibleComponents = editorState.visibleComponents,
                    isComponentLoading = editorState.isComponentLoading,
                    componentLoadError = editorState.componentLoadError,
                    onSave = viewModel::saveRule,
                    onBack = viewModel::dismissEditor,
                    onPackageNameChange = viewModel::updateSimplePackageName,
                    onComponentTypeChange = viewModel::updateSimpleComponentType,
                    onTargetModeChange = viewModel::updateSimpleTargetMode,
                    onBlockChange = viewModel::updateSimpleBlock,
                    onLogChange = viewModel::updateSimpleLog,
                    onActionChange = viewModel::updateSimpleAction,
                    onCategoryChange = viewModel::updateSimpleCategory,
                    onCallerPackageChange = viewModel::updateSimpleCallerPackage,
                    onComponentQueryChange = viewModel::updateComponentQuery,
                    onSelectSingleTarget = viewModel::selectSingleTarget,
                    onToggleMultiTarget = viewModel::toggleMultiTarget,
                )
            } ?: LoadingScreen(modifier = Modifier.fillMaxSize())

            GlobalIfwRuleScreenState.ADVANCED_EDIT -> editorState.advancedDraft?.let { draft ->
                AdvancedGlobalIfwRuleScreen(
                    draft = draft,
                    isDirty = editorState.isDirty,
                    onSave = viewModel::saveRule,
                    onBack = viewModel::dismissEditor,
                    onPackageNameChange = viewModel::updateAdvancedPackageName,
                    onComponentTypeChange = viewModel::updateAdvancedComponentType,
                    onBlockChange = viewModel::updateAdvancedBlock,
                    onLogChange = viewModel::updateAdvancedLog,
                    onRootGroupChange = viewModel::updateAdvancedRootGroup,
                )
            } ?: LoadingScreen(modifier = Modifier.fillMaxSize())

            GlobalIfwRuleScreenState.ADVANCED_DETAIL -> editorState.detail?.let { detail ->
                AdvancedGlobalIfwRuleDetailScreen(
                    detail = detail,
                    onBack = viewModel::dismissEditor,
                    onCopyAsNew = viewModel::copyAdvancedRule,
                    onDelete = viewModel::deleteViewedRule,
                )
            } ?: LoadingScreen(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun GlobalIfwRuleScreen(
    uiState: GlobalIfwRuleUiState,
    onAddSimpleRuleClick: () -> Unit,
    onAddAdvancedRuleClick: () -> Unit,
    onOpenRuleClick: (String, Int) -> Unit,
    onDeleteRule: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            BlockerTopAppBar(
                title = stringResource(R.string.feature_globalifwrule_api_title),
                actions = {
                    IconButton(onClick = onAddAdvancedRuleClick) {
                        Icon(
                            imageVector = BlockerIcons.Rule,
                            contentDescription = stringResource(R.string.feature_globalifwrule_api_add_advanced_rule),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddSimpleRuleClick) {
                Icon(
                    imageVector = BlockerIcons.Add,
                    contentDescription = stringResource(R.string.feature_globalifwrule_api_add_rule),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        when (uiState) {
            is GlobalIfwRuleUiState.Loading -> LoadingScreen(modifier = Modifier.padding(padding))
            is GlobalIfwRuleUiState.Error -> ErrorScreen(
                error = UiMessage(title = uiState.message),
                modifier = Modifier.padding(padding),
            )

            is GlobalIfwRuleUiState.Success -> {
                if (uiState.groups.isEmpty()) {
                    EmptyScreen(
                        textRes = R.string.feature_globalifwrule_api_empty,
                        contentDescriptionRes = R.string.feature_globalifwrule_api_empty_desc,
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    RuleListContent(
                        groups = uiState.groups,
                        onOpenRuleClick = onOpenRuleClick,
                        onDeleteRule = onDeleteRule,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleListContent(
    groups: List<PackageRuleGroup>,
    onOpenRuleClick: (String, Int) -> Unit,
    onDeleteRule: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = groups,
            key = { group -> group.packageName },
        ) { group ->
            PackageRuleSection(
                group = group,
                onOpenRuleClick = onOpenRuleClick,
                onDeleteRule = onDeleteRule,
            )
        }
        item {
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
        }
    }
}

@Composable
private fun PackageRuleSection(
    group: PackageRuleGroup,
    onOpenRuleClick: (String, Int) -> Unit,
    onDeleteRule: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            PackageHeader(group = group)
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                group.rules.forEach { rule ->
                    RuleItem(
                        rule = rule,
                        onClick = { onOpenRuleClick(group.packageName, rule.ruleIndex) },
                        onDelete = { onDeleteRule(group.packageName, rule.ruleIndex) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PackageHeader(
    group: PackageRuleGroup,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PackageAvatar(
            packageInfo = group.packageInfo,
            fallbackLabel = group.appLabel ?: group.packageName,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.appLabel ?: group.packageName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = group.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Text(
                text = group.rules.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PackageAvatar(
    packageInfo: android.content.pm.PackageInfo?,
    fallbackLabel: String,
    modifier: Modifier = Modifier,
) {
    val initial = fallbackLabel
        .firstOrNull { character -> character.isLetterOrDigit() }
        ?.uppercase()
        ?: "#"

    Surface(
        modifier = modifier.size(48.dp),
        color = if (packageInfo == null) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        },
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (packageInfo != null) {
                AppIcon(
                    info = packageInfo,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun RuleItem(
    rule: RuleItemUiState,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val cardPresentation = rule.presentation
    val ruleTitle = cardPresentation.title ?: stringResource(rule.componentType.labelRes)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                },
            ),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = ruleTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    cardPresentation.targetPath?.let { targetPath ->
                        RulePrimaryMetadataText(
                            text = targetPath,
                            maxLines = 1,
                        )
                    }
                }
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = BlockerIcons.MoreVert,
                        contentDescription = null,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                RuleMetaBadge(
                    text = stringResource(rule.componentType.labelRes),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (rule.block) {
                    RuleMetaBadge(
                        text = stringResource(R.string.feature_globalifwrule_api_block),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (rule.log) {
                    RuleMetaBadge(
                        text = stringResource(R.string.feature_globalifwrule_api_log),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                if (rule.editMode == GlobalIfwRuleEditMode.ADVANCED) {
                    RuleMetaBadge(
                        text = stringResource(R.string.feature_globalifwrule_api_advanced_rule),
                    )
                }
            }

            cardPresentation.supportingText?.let { supportingText ->
                RuleSecondaryMetadataText(
                    text = supportingText,
                    maxLines = 3,
                    modifier = Modifier.padding(top = 10.dp, end = 6.dp),
                )
            }

            BlockerDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                items = listOf(R.string.feature_globalifwrule_api_delete_rule),
                onItemClick = {
                    showMenu = false
                    showDeleteDialog = true
                },
                itemText = { item -> Text(stringResource(item)) },
            )
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = onDelete,
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun GlobalIfwRuleScreenPreviewContainer(
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
private fun GlobalIfwRuleScreenPreview(
    @PreviewParameter(GlobalIfwRuleUiStatePreviewParameterProvider::class)
    uiState: GlobalIfwRuleUiState,
) {
    GlobalIfwRuleScreenPreviewContainer {
        GlobalIfwRuleScreen(
            uiState = uiState,
            onAddSimpleRuleClick = {},
            onAddAdvancedRuleClick = {},
            onOpenRuleClick = { _, _ -> },
            onDeleteRule = { _, _ -> },
        )
    }
}
