// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerLoadingWheel
import com.merxury.blocker.core.designsystem.component.BlockerTextButton
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.model.history.OperationSource
import com.merxury.blocker.core.ui.profile.text
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.toJavaInstant
import com.merxury.blocker.core.ui.R.string as UiStrings

@Composable
internal fun HistoryTimeline(
    days: List<HistoryDay>,
    expanded: ExpandedOperation?,
    isUndoing: Boolean,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    onExpand: (HistoryOperation) -> Unit,
    onUndo: (HistoryOperation) -> Unit,
    onUndoSince: (LocalDate) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { day ->
            item(key = "day-${day.date}", contentType = "day") {
                DayHeader(dateFormatter.format(day.date), day.canUndoSince, isUndoing) { onUndoSince(day.date) }
            }
            day.operations.forEach { row ->
                val operation = row.operation
                val isExpanded = expanded?.id == operation.id
                item(key = operation.id, contentType = "operation") {
                    OperationRow(
                        row = row,
                        time = timeFormatter.format(operation.timestamp.toJavaInstant()),
                        expanded = isExpanded,
                        canUndo = !isUndoing,
                        onExpand = { onExpand(row) },
                        onUndo = { onUndo(row) },
                    )
                }
                if (expanded != null && isExpanded) expandedEntries(expanded)
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing)) }
    }
}

@Composable
private fun DayHeader(date: String, showUndo: Boolean, isUndoing: Boolean, onUndo: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(date, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        if (showUndo) {
            BlockerTextButton(onClick = onUndo, enabled = !isUndoing) {
                Text(stringResource(R.string.feature_history_impl_undo_since))
            }
        }
    }
}

@Composable
private fun OperationRow(
    row: HistoryOperation,
    time: String,
    expanded: Boolean,
    canUndo: Boolean,
    onExpand: () -> Unit,
    onUndo: () -> Unit,
) {
    val operation = row.operation
    val expandable = operation.entryCount > 0
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .then(if (expandable) Modifier.clickable(role = Role.Button, onClick = onExpand) else Modifier)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(operation.source.label()), style = MaterialTheme.typography.titleSmall)
                row.reference?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(time, style = MaterialTheme.typography.bodySmall)
                Text(
                    pluralStringResource(
                        R.plurals.feature_history_impl_component_count,
                        operation.entryCount,
                        operation.entryCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (operation.undoneByOperationId != null) {
                    Text(stringResource(R.string.feature_history_impl_undone), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (expandable) {
                Icon(
                    imageVector = if (expanded) BlockerIcons.ExpandLess else BlockerIcons.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.feature_history_impl_collapse else R.string.feature_history_impl_expand,
                    ),
                )
            }
        }
        if (row.canUndo) {
            BlockerTextButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.feature_history_impl_undo))
            }
        }
    }
}

private fun LazyListScope.expandedEntries(expanded: ExpandedOperation) {
    val entries = expanded.entries
    if (entries.isNullOrEmpty()) {
        item(key = "entries-${expanded.id}") {
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                when {
                    expanded.loadFailed -> Text(stringResource(R.string.feature_history_impl_entries_failed))

                    entries == null -> BlockerLoadingWheel(
                        contentDesc = stringResource(UiStrings.core_ui_loading),
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> Text(stringResource(R.string.feature_history_impl_no_snapshots))
                }
            }
        }
    } else {
        items(entries.size, key = { "entry-${expanded.id}-$it" }, contentType = { "component" }) { index ->
            ComponentChange(entries[index])
        }
    }
}

@Composable
private fun ComponentChange(entry: HistoryEntry) {
    val before = stringResource(entry.before.label())
    val after = stringResource(entry.after.label())
    ListItem(
        supportingContent = {
            Column {
                Text(entry.app)
                entry.profile?.let { Text(it.text()) }
                Text(stringResource(R.string.feature_history_impl_state_change, before, after))
            }
        },
        modifier = Modifier.padding(start = 16.dp),
    ) { Text(entry.component) }
}

@StringRes
private fun ComponentState.label(): Int = when (this) {
    ComponentState.ON -> R.string.feature_history_impl_on
    ComponentState.OFF -> R.string.feature_history_impl_off
    ComponentState.UNKNOWN -> UiStrings.core_ui_unknown
}

@StringRes
private fun OperationSource.label(): Int = when (this) {
    OperationSource.APP_DETAIL -> R.string.feature_history_impl_source_app_detail
    OperationSource.SEARCH -> R.string.feature_history_impl_source_search
    OperationSource.GENERAL_RULE -> R.string.feature_history_impl_source_general_rule
    OperationSource.RULE_DETAIL -> R.string.feature_history_impl_source_rule_detail
    OperationSource.DEBLOATER -> R.string.feature_history_impl_source_debloater
    OperationSource.EXTERNAL_PROVIDER -> R.string.feature_history_impl_source_provider
    OperationSource.IMPORT_BLOCKER_RULE -> R.string.feature_history_impl_source_import_blocker
    OperationSource.IMPORT_MAT_RULE -> R.string.feature_history_impl_source_import_mat
    OperationSource.RESET_PM -> R.string.feature_history_impl_source_reset_pm
    OperationSource.UNDO -> R.string.feature_history_impl_undo
}
