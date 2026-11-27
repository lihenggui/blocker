// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerOutlinedButton
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.profile.text
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import java.time.LocalDate
import com.merxury.blocker.core.ui.R.string as UiStrings

@Composable
internal fun HistoryOperationRoute(
    viewModel: HistoryOperationViewModel,
    navigateBack: () -> Unit,
    showBackButton: Boolean,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    state.message?.let { message ->
        val text = message.toUiMessage()
        LaunchedEffect(message, text.title) {
            snackbarHostState.showSnackbar(text.title, duration = SnackbarDuration.Long, withDismissAction = true)
            viewModel.messageShown(message)
        }
    }
    HistoryOperationScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        navigateBack = navigateBack,
        showBackButton = showBackButton,
        onUndo = viewModel::undo,
        onRequestUndoLater = viewModel::requestUndoLater,
        onConfirm = viewModel::confirmUndoLater,
        onCancel = viewModel::cancelUndoLater,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HistoryOperationScreen(
    state: HistoryOperationUiState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    navigateBack: () -> Unit = {},
    showBackButton: Boolean = true,
    onUndo: () -> Unit = {},
    onRequestUndoLater: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    today: LocalDate = LocalDate.now(state.zone),
) {
    Scaffold(
        modifier = Modifier.testTag("history:operation"),
        topBar = { BlockerTopAppBar(title = {}, hasNavigationIcon = showBackButton, onNavigationClick = navigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        val row = state.row
        when {
            state.loadFailed -> ErrorScreen(UiMessage(stringResource(R.string.feature_history_impl_load_failed)), modifier)

            row == null -> LoadingScreen(modifier)

            else -> {
                val dates = rememberHistoryDates(state.zone, today)
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(HistoryMargin),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    item(key = "header") {
                        Column(Modifier.animateItem()) {
                            OperationHeader(row, dates)
                            OperationActions(state, dates, onUndo, onRequestUndoLater)
                        }
                    }
                    val groups = state.groups
                    when {
                        state.entriesFailed -> item { Text(stringResource(R.string.feature_history_impl_entries_failed)) }
                        groups == null -> item { LoadingScreen(Modifier.padding(HistoryMargin)) }
                        else -> componentGroups(groups)
                    }
                }
                if (state.confirmLater) UndoLaterDialog(state.laterCount, onConfirm, onCancel)
            }
        }
    }
}

@Composable
private fun OperationHeader(row: HistoryOperation, dates: HistoryDates) {
    Column(verticalArrangement = Arrangement.spacedBy(HistorySpacing)) {
        OperationIcon(row, large = true)
        Text(
            text = row.headline.text(),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        val headline = row.headline.text()
        val source = stringResource(row.operation.source.label())
        val time = dates.dateTime(row.operation.timestamp)
        Text(
            // A multi-app source is already the headline; repeating it adds nothing.
            text = if (source == headline) time else stringResource(R.string.feature_history_impl_source_time, source, time),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = row.summary.text(), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OperationActions(
    state: HistoryOperationUiState,
    dates: HistoryDates,
    onUndo: () -> Unit,
    onRequestUndoLater: () -> Unit,
) {
    val modifier = Modifier.padding(top = HistorySpacing)
    if (state.row?.isUndone == true) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.IconSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val primary = MaterialTheme.colorScheme.primary
            Icon(BlockerIcons.Check, contentDescription = null, tint = primary, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(
                text = state.undoneAt?.let { stringResource(R.string.feature_history_impl_undone_status, dates.dateTime(it)) }
                    ?: stringResource(R.string.feature_history_impl_undone),
                style = MaterialTheme.typography.labelLarge,
                color = primary,
            )
        }
    } else if (state.canUndo) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(HistorySpacing),
        ) {
            FilledTonalButton(
                onClick = onUndo,
                enabled = !state.isUndoing,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                val iconSize = Modifier.size(ButtonDefaults.IconSize)
                if (state.isUndoing) {
                    LoadingIndicator(modifier = iconSize)
                } else {
                    Icon(BlockerIcons.SettingsBackupRestore, contentDescription = null, modifier = iconSize)
                }
                Text(
                    text = stringResource(if (state.isUndoing) R.string.feature_history_impl_undoing else R.string.feature_history_impl_undo),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
            if (state.canUndoLater) {
                BlockerOutlinedButton(onRequestUndoLater, enabled = !state.isUndoing) {
                    Text(stringResource(R.string.feature_history_impl_undo_later))
                }
            }
        }
    }
}

@Composable
private fun UndoLaterDialog(laterCount: Int, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val total = laterCount + 1
    AlertDialog(
        title = { Text(pluralStringResource(R.plurals.feature_history_impl_confirm_title, total, total)) },
        text = { Text(pluralStringResource(R.plurals.feature_history_impl_confirm_body, laterCount, laterCount)) },
        onDismissRequest = onCancel,
        confirmButton = { BlockerTextButton(onConfirm) { Text(stringResource(R.string.feature_history_impl_undo)) } },
        dismissButton = { BlockerTextButton(onCancel) { Text(stringResource(R.string.feature_history_impl_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.componentGroups(groups: List<HistoryAppGroup>) {
    groups.forEach { group ->
        if (groups.size > 1) {
            item(key = "app:${group.appIcon.packageName}", contentType = "app") {
                Row(
                    modifier = Modifier.animateItem().padding(top = HistoryMargin, bottom = HistorySpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HistorySpacing),
                ) {
                    AppIcon(group.appIcon, Modifier.size(IconButtonDefaults.smallIconSize))
                    HistorySectionTitle(group.appLabel)
                }
            }
        }
        itemsIndexed(
            items = group.entries,
            key = { _, entry ->
                val (component, userId) = entry.key
                "component:${userId.value}:${component.packageName}:${component.className}"
            },
            contentType = { _, _ -> "component" },
        ) { index, entry ->
            val profile = entry.profile
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index, group.entries.size),
                modifier = Modifier.animateItem().then(
                    if (groups.size == 1 && index == 0) Modifier.padding(top = HistoryMargin) else Modifier,
                ),
                colors = historySegmentedColors(),
                supportingContent = if (profile != null) {
                    { Text(profile.text()) }
                } else {
                    null
                },
                trailingContent = { Text(stringResource(entry.change.label())) },
            ) { Text(entry.key.id.simpleName) }
        }
    }
}

private fun ComponentState.label(): Int = when (this) {
    ComponentState.ON -> R.string.feature_history_impl_on
    ComponentState.OFF -> R.string.feature_history_impl_off
    ComponentState.UNKNOWN -> UiStrings.core_ui_unknown
}
