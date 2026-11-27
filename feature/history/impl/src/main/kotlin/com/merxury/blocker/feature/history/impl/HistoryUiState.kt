// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import com.merxury.blocker.core.model.history.Operation
import com.merxury.blocker.core.model.history.OperationSource
import com.merxury.blocker.core.ui.coil.ApkIcon
import java.time.LocalDate
import java.time.ZoneId

internal data class HistoryUiState(
    val days: List<HistoryDay>? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val loadFailed: Boolean = false,
)

internal data class HistoryDay(val date: LocalDate, val operations: List<HistoryOperation>)

internal data class HistoryOperation(
    val operation: Operation,
    val headline: OperationHeadline = OperationHeadline(operation.source),
    val appIcon: ApkIcon? = null,
    val undoTarget: UndoTarget? = null,
) {
    val isUndone: Boolean get() = operation.undoneByOperationId != null

    val summary: OperationSummary
        get() = with(operation) {
            when {
                source == OperationSource.UNDO -> OperationSummary.Undo(undoTarget, entryCount)
                blockedCount > 0 && enabledCount > 0 -> OperationSummary.Mixed(blockedCount, enabledCount)
                blockedCount > 0 -> OperationSummary.Blocked(blockedCount)
                enabledCount > 0 -> OperationSummary.Enabled(enabledCount)
                else -> OperationSummary.Changed(entryCount)
            }
        }
}

internal data class OperationHeadline(val source: OperationSource, val reference: String? = null)

internal sealed interface UndoTarget {
    data class Single(val headline: OperationHeadline) : UndoTarget
    data class Several(val count: Int) : UndoTarget
}

internal sealed interface OperationSummary {
    data class Blocked(val count: Int) : OperationSummary
    data class Enabled(val count: Int) : OperationSummary
    data class Mixed(val blocked: Int, val enabled: Int) : OperationSummary
    data class Changed(val count: Int) : OperationSummary
    data class Undo(val target: UndoTarget?, val restored: Int) : OperationSummary
}

internal val Operation.canUndo: Boolean
    get() = entryCount > 0 && undoneByOperationId == null && source != OperationSource.UNDO
