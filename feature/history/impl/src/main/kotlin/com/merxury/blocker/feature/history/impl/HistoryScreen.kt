// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun HistoryRoute(
    navigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state.message?.let { message ->
        val text = message.toUiMessage()
        LaunchedEffect(snackbarHostState, message, text) {
            snackbarHostState.showSnackbar(text.title, duration = SnackbarDuration.Long, withDismissAction = true)
            viewModel.messageShown(message)
        }
    }
    HistoryScreen(
        state = state,
        navigateBack = navigateBack,
        onExpand = viewModel::toggleOperation,
        onUndo = viewModel::requestUndo,
        onUndoSince = viewModel::requestUndoSince,
        onConfirm = viewModel::confirmUndo,
        onCancel = viewModel::cancelUndo,
    )
}

@Composable
internal fun HistoryScreen(
    state: HistoryUiState,
    modifier: Modifier = Modifier,
    navigateBack: () -> Unit = {},
    onExpand: (HistoryOperation) -> Unit = {},
    onUndo: (HistoryOperation) -> Unit = {},
    onUndoSince: (LocalDate) -> Unit = {},
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val timeFormatter = remember(locale, state.zone) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(state.zone)
    }
    Column(modifier.fillMaxSize()) {
        BlockerTopAppBar(
            title = stringResource(R.string.feature_history_impl_title),
            hasNavigationIcon = true,
            onNavigationClick = navigateBack,
        )
        if (state.isUndoing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.feature_history_impl_undoing),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val days = state.days
            when {
                state.loadFailed -> ErrorScreen(UiMessage(stringResource(R.string.feature_history_impl_load_failed)))

                days == null -> LoadingScreen()

                days.isEmpty() -> Text(
                    text = stringResource(R.string.feature_history_impl_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )

                else -> HistoryTimeline(
                    days = days,
                    expanded = state.expanded,
                    isUndoing = state.isUndoing,
                    dateFormatter = dateFormatter,
                    timeFormatter = timeFormatter,
                    onExpand = onExpand,
                    onUndo = onUndo,
                    onUndoSince = onUndoSince,
                )
            }
        }
    }
    state.confirmation?.let { request ->
        UndoConfirmation(request, dateFormatter, onConfirm, onCancel)
    }
}

@Composable
private fun UndoConfirmation(
    request: UndoRequest,
    dateFormatter: DateTimeFormatter,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val message = when (request) {
        is UndoRequest.Single -> pluralStringResource(
            R.plurals.feature_history_impl_confirm_operation,
            request.entryCount,
            request.entryCount,
        )

        is UndoRequest.Since -> stringResource(R.string.feature_history_impl_confirm_since, dateFormatter.format(request.day))
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.feature_history_impl_confirm_title)) },
        text = { Text(message) },
        confirmButton = {
            BlockerTextButton(onClick = onConfirm) { Text(stringResource(R.string.feature_history_impl_undo)) }
        },
        dismissButton = {
            BlockerTextButton(onClick = onCancel) { Text(stringResource(R.string.feature_history_impl_cancel)) }
        },
    )
}

@Composable
private fun HistoryMessage.toUiMessage(): UiMessage = when (this) {
    is HistoryMessage.Undone -> UiMessage(
        title = stringResource(
            R.string.feature_history_impl_undo_result,
            result.restored,
            result.alreadyAtTarget,
            result.skipped,
            result.failed,
        ),
    )

    is HistoryMessage.UndoFailed -> UiMessage(title = stringResource(R.string.feature_history_impl_undo_failed))
}
