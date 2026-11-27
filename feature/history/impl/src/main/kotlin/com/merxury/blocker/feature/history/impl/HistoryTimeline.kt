// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import androidx.annotation.StringRes
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
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

private val TimelineSpacing = 8.dp

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
    ) {
        days.forEachIndexed { index, day ->
            item(key = "day-${day.date}", contentType = "day") {
                DayHeader(
                    date = dateFormatter.format(day.date),
                    showUndo = day.canUndoSince,
                    isUndoing = isUndoing,
                    onUndo = { onUndoSince(day.date) },
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else TimelineSpacing),
                )
            }
            day.operations.forEach { row ->
                val operation = row.operation
                val expansion = expanded?.takeIf { it.id == operation.id && operation.entryCount > 0 }
                val segmentCount = if (expansion == null) 1 else 1 + expansion.entries.orEmpty().size.coerceAtLeast(1)
                item(key = operation.id, contentType = "operation") {
                    OperationRow(
                        row = row,
                        time = timeFormatter.format(operation.timestamp.toJavaInstant()),
                        segmentCount = segmentCount,
                        isUndoing = isUndoing,
                        onExpand = { onExpand(row) },
                        onUndo = { onUndo(row) },
                    )
                }
                if (expansion != null) expandedEntries(expansion, segmentCount)
            }
        }
        item { Spacer(Modifier.padding(top = TimelineSpacing).windowInsetsBottomHeight(WindowInsets.safeDrawing)) }
    }
}

@Composable
private fun DayHeader(date: String, showUndo: Boolean, isUndoing: Boolean, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(date, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        if (showUndo) {
            BlockerTextButton(onClick = onUndo, enabled = !isUndoing) {
                Text(stringResource(R.string.feature_history_impl_undo_since))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OperationRow(
    row: HistoryOperation,
    time: String,
    segmentCount: Int,
    isUndoing: Boolean,
    onExpand: () -> Unit,
    onUndo: () -> Unit,
) {
    val operation = row.operation
    val modifier = Modifier.padding(top = TimelineSpacing)
    val shapes = ListItemDefaults.segmentedShapes(index = 0, count = segmentCount)
    val colors = historySegmentedColors()
    val headline: @Composable () -> Unit = { Text(stringResource(operation.source.label())) }
    val supporting: @Composable () -> Unit = {
        Column {
            row.reference?.let {
                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(time)
            Text(
                pluralStringResource(
                    R.plurals.feature_history_impl_component_count,
                    operation.entryCount,
                    operation.entryCount,
                ),
            )
            if (operation.undoneByOperationId != null) {
                Text(stringResource(R.string.feature_history_impl_undone))
            }
        }
    }
    if (operation.entryCount == 0) {
        SegmentedListItem(
            shapes = shapes,
            colors = colors,
            modifier = modifier,
            supportingContent = supporting,
            content = headline,
        )
    } else {
        val expanded = segmentCount > 1
        SegmentedListItem(
            onClick = onExpand,
            shapes = shapes,
            colors = colors,
            modifier = modifier.semantics { role = Role.Button },
            supportingContent = supporting,
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.canUndo) {
                        BlockerTextButton(onClick = onUndo, enabled = !isUndoing) {
                            Text(stringResource(R.string.feature_history_impl_undo))
                        }
                    }
                    Icon(
                        imageVector = if (expanded) BlockerIcons.ExpandLess else BlockerIcons.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.feature_history_impl_collapse else R.string.feature_history_impl_expand,
                        ),
                    )
                }
            },
            content = headline,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.expandedEntries(expanded: ExpandedOperation, segmentCount: Int) {
    val entries = expanded.entries
    if (entries.isNullOrEmpty()) {
        item(key = "entries-${expanded.id}", contentType = "entry status") {
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = segmentCount),
                colors = historySegmentedColors(),
                modifier = Modifier.padding(top = ListItemDefaults.SegmentedGap),
            ) {
                when {
                    expanded.loadFailed -> Text(stringResource(R.string.feature_history_impl_entries_failed))

                    entries == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BlockerLoadingWheel(contentDesc = stringResource(UiStrings.core_ui_loading))
                    }

                    else -> Text(stringResource(R.string.feature_history_impl_no_snapshots))
                }
            }
        }
    } else {
        items(entries.size, key = { "entry-${expanded.id}-$it" }, contentType = { "component" }) { index ->
            ComponentChange(entries[index], segmentIndex = index + 1, segmentCount = segmentCount)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ComponentChange(entry: HistoryEntry, segmentIndex: Int, segmentCount: Int) {
    val before = stringResource(entry.before.label())
    val after = stringResource(entry.after.label())
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = segmentIndex, count = segmentCount),
        colors = historySegmentedColors(),
        modifier = Modifier.padding(top = ListItemDefaults.SegmentedGap),
        supportingContent = {
            Column {
                Text(entry.app)
                entry.profile?.let { Text(it.text()) }
                Text(stringResource(R.string.feature_history_impl_state_change, before, after))
            }
        },
    ) { Text(entry.component) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun historySegmentedColors() = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

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
