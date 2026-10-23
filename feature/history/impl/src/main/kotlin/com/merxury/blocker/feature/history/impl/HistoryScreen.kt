/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.feature.history.impl

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.domain.history.UndoFailureReason
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.Operation
import com.merxury.blocker.core.model.data.OperationEntry
import com.merxury.blocker.core.model.data.OperationSource
import com.merxury.blocker.core.ui.LocalTimeZone
import com.merxury.blocker.core.ui.screen.LoadingScreen
import timber.log.Timber
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@Composable
fun HistoryScreen(
    navigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    val expandedId by viewModel.expandedOperationId.collectAsStateWithLifecycle()
    val entries by viewModel.expandedEntries.collectAsStateWithLifecycle()
    val request by viewModel.undoRequest.collectAsStateWithLifecycle()
    val isUndoing by viewModel.isUndoing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var failureReasons by remember { mutableStateOf<Map<ComponentInfo, List<UndoFailureReason>>?>(null) }
    LaunchedEffect(viewModel, snackbarHostState, context) {
        viewModel.undoResults.collect { result ->
            result.fold(
                onSuccess = { undo ->
                    val response = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.feature_history_impl_undo_result, undo.restored, undo.skipped, undo.failed),
                        actionLabel = if (undo.failureReasons.isNotEmpty()) context.getString(R.string.feature_history_impl_details) else null,
                        withDismissAction = true,
                    )
                    if (response == SnackbarResult.ActionPerformed) failureReasons = undo.failureReasons
                },
                onFailure = { error ->
                    Timber.e(error, "Operation history failed")
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.feature_history_impl_error),
                        withDismissAction = true,
                    )
                },
            )
        }
    }
    HistoryScreen(
        operations = operations,
        expandedOperationId = expandedId,
        entries = entries,
        undoRequest = request,
        isUndoing = isUndoing,
        navigateBack = navigateBack,
        onExpand = viewModel::toggleOperation,
        onUndoOperation = viewModel::requestUndo,
        onUndoSince = viewModel::requestUndo,
        onConfirmUndo = viewModel::confirmUndo,
        onDismissUndo = viewModel::dismissUndo,
    )
    failureReasons?.let { reasons ->
        AlertDialog(
            onDismissRequest = { failureReasons = null },
            title = { Text(stringResource(R.string.feature_history_impl_failures)) },
            text = {
                val message = reasons.entries.joinToString("\n\n") { (component, failures) ->
                    context.getString(
                        R.string.feature_history_impl_failure,
                        component.packageName,
                        component.name,
                        failures.joinToString { context.getString(it.stringRes()) },
                    )
                }
                Text(message, Modifier.verticalScroll(rememberScrollState()))
            },
            confirmButton = {
                TextButton(onClick = { failureReasons = null }) { Text(stringResource(R.string.feature_history_impl_close)) }
            },
        )
    }
}

@StringRes
internal fun UndoFailureReason.stringRes(): Int = when (this) {
    UndoFailureReason.COMPONENT_MISSING -> R.string.feature_history_impl_failure_component_missing
    UndoFailureReason.PM_FAILED -> R.string.feature_history_impl_failure_pm
    UndoFailureReason.IFW_PERMISSION_UNAVAILABLE -> R.string.feature_history_impl_failure_ifw_permission
    UndoFailureReason.IFW_FAILED -> R.string.feature_history_impl_failure_ifw
    UndoFailureReason.ERROR -> R.string.feature_history_impl_failure_error
}

@Composable
internal fun HistoryScreen(
    operations: List<Operation>?,
    modifier: Modifier = Modifier,
    expandedOperationId: Long? = null,
    entries: List<OperationEntry>? = null,
    undoRequest: UndoRequest? = null,
    isUndoing: Boolean = false,
    navigateBack: () -> Unit = {},
    onExpand: (Long) -> Unit = {},
    onUndoOperation: (Operation) -> Unit = {},
    onUndoSince: (Instant) -> Unit = {},
    onConfirmUndo: () -> Unit = {},
    onDismissUndo: () -> Unit = {},
) {
    val zone = ZoneId.of(LocalTimeZone.current.id)
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone)
    Column(modifier.fillMaxSize()) {
        BlockerTopAppBar(
            title = stringResource(R.string.feature_history_impl_title),
            hasNavigationIcon = true,
            onNavigationClick = navigateBack,
        )
        if (isUndoing) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.feature_history_impl_undoing))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when {
                operations == null -> LoadingScreen()

                operations.isEmpty() -> Text(
                    stringResource(R.string.feature_history_impl_empty),
                    modifier = Modifier.padding(24.dp).align(Alignment.Center),
                )

                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize().testTag("history:list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val grouped = operations.sortedWith(compareByDescending<Operation> { it.timestamp }.thenByDescending { it.id })
                        .groupBy { it.timestamp.toJavaInstant().atZone(zone).toLocalDate() }
                    grouped.forEach { (day, dayOperations) ->
                        val since = day.atStartOfDay(zone).toInstant().toKotlinInstant()
                        item(key = "day-$day") {
                            Column(Modifier.fillMaxWidth()) {
                                Text(dateFormatter.format(day), style = MaterialTheme.typography.titleMedium)
                                if (operations.any { it.timestamp >= since && it.isUndoable() }) {
                                    TextButton(
                                        onClick = { onUndoSince(since) },
                                        enabled = !isUndoing,
                                        modifier = Modifier.align(Alignment.End),
                                    ) { Text(stringResource(R.string.feature_history_impl_undo_since)) }
                                }
                            }
                        }
                        items(dayOperations, key = { it.id }) { operation ->
                            OperationRow(
                                operation = operation,
                                time = timeFormatter.format(operation.timestamp.toJavaInstant()),
                                expanded = expandedOperationId == operation.id,
                                entries = entries?.filter { it.operationId == operation.id },
                                isUndoing = isUndoing,
                                onExpand = { onExpand(operation.id) },
                                onUndo = { onUndoOperation(operation) },
                            )
                        }
                    }
                    item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing)) }
                }
            }
        }
    }
    undoRequest?.let { request ->
        val message = when (request) {
            is UndoRequest.Single -> pluralStringResource(
                R.plurals.feature_history_impl_confirm_operation,
                request.operation.entryCount,
                request.operation.entryCount,
            )

            is UndoRequest.Since -> pluralStringResource(
                R.plurals.feature_history_impl_confirm_since,
                request.entryCount,
                request.entryCount,
                dateFormatter.format(request.timestamp.toJavaInstant().atZone(zone)),
            )
        }
        AlertDialog(
            onDismissRequest = onDismissUndo,
            title = { Text(stringResource(R.string.feature_history_impl_confirm_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onConfirmUndo, enabled = !isUndoing) { Text(stringResource(R.string.feature_history_impl_undo)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissUndo) { Text(stringResource(R.string.feature_history_impl_cancel)) }
            },
        )
    }
}

@Composable
private fun OperationRow(
    operation: Operation,
    time: String,
    expanded: Boolean,
    entries: List<OperationEntry>?,
    isUndoing: Boolean,
    onExpand: () -> Unit,
    onUndo: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("history:operation-${operation.id}")) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = operation.entryCount > 0, onClick = onExpand).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$time · ${stringResource(operation.source.label())}", style = MaterialTheme.typography.titleSmall)
                operation.ref?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    pluralStringResource(R.plurals.feature_history_impl_component_count, operation.entryCount, operation.entryCount),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (operation.undoneByOperationId != null) {
                    Text(stringResource(R.string.feature_history_impl_undone), color = MaterialTheme.colorScheme.primary)
                }
            }
            if (operation.entryCount > 0) {
                Icon(
                    imageVector = if (expanded) BlockerIcons.ExpandLess else BlockerIcons.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.feature_history_impl_collapse else R.string.feature_history_impl_expand),
                )
            }
        }
        if (expanded && operation.entryCount > 0) {
            HorizontalDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (entries == null) {
                    CircularProgressIndicator()
                } else if (entries.isEmpty()) {
                    Text(stringResource(R.string.feature_history_impl_no_snapshots))
                } else {
                    entries.forEach { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${entry.packageName}/${entry.componentName}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.feature_history_impl_pm_state, stateLabel(entry.beforePmBlocked), stateLabel(entry.afterPmBlocked)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                stringResource(R.string.feature_history_impl_ifw_state, stateLabel(entry.beforeIfwBlocked), stateLabel(entry.afterIfwBlocked)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        if (operation.isUndoable()) {
            TextButton(
                onClick = onUndo,
                enabled = !isUndoing,
                modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp),
            ) { Text(stringResource(R.string.feature_history_impl_undo_operation)) }
        }
    }
}

@Composable
private fun stateLabel(blocked: Boolean) = stringResource(if (blocked) R.string.feature_history_impl_blocked else R.string.feature_history_impl_allowed)

@StringRes
private fun OperationSource.label(): Int = when (this) {
    OperationSource.APP_DETAIL -> R.string.feature_history_impl_source_app_detail
    OperationSource.SEARCH -> R.string.feature_history_impl_source_search
    OperationSource.GENERAL_RULE -> R.string.feature_history_impl_source_general_rule
    OperationSource.RULE_DETAIL -> R.string.feature_history_impl_source_rule_detail
    OperationSource.DEBLOATER -> R.string.feature_history_impl_source_debloater
    OperationSource.EXTERNAL_PROVIDER -> R.string.feature_history_impl_source_provider
    OperationSource.IMPORT_BLOCKER_RULE -> R.string.feature_history_impl_source_import_blocker
    OperationSource.IMPORT_IFW_RULE -> R.string.feature_history_impl_source_import_ifw
    OperationSource.IMPORT_MAT_RULE -> R.string.feature_history_impl_source_import_mat
    OperationSource.RESET_IFW -> R.string.feature_history_impl_source_reset_ifw
    OperationSource.UNDO -> R.string.feature_history_impl_source_undo
}
