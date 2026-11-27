// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import com.merxury.blocker.core.model.history.Operation
import com.merxury.blocker.core.model.history.OperationSource
import com.merxury.blocker.core.model.history.UndoResult
import com.merxury.blocker.core.ui.profile.ProfileLabel
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Instant

internal data class HistoryUiState(
    val days: List<HistoryDay>? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val loadFailed: Boolean = false,
    val expanded: ExpandedOperation? = null,
    val confirmation: UndoRequest? = null,
    val isUndoing: Boolean = false,
    val message: HistoryMessage? = null,
)

internal data class HistoryDay(val date: LocalDate, val operations: List<HistoryOperation>, val canUndoSince: Boolean)

internal data class HistoryOperation(val operation: Operation, val reference: String? = null) {
    val canUndo: Boolean
        get() = operation.entryCount > 0 && operation.undoneByOperationId == null && operation.source != OperationSource.UNDO
}

internal data class ExpandedOperation(
    val id: Long,
    val entries: List<HistoryEntry>? = null,
    val loadFailed: Boolean = false,
)

internal data class HistoryEntry(
    val component: String,
    val app: String,
    val profile: ProfileLabel?,
    val before: ComponentState,
    val after: ComponentState,
)

internal enum class ComponentState { ON, OFF, UNKNOWN }

internal sealed interface UndoRequest {
    data class Single(val id: Long, val entryCount: Int) : UndoRequest
    data class Since(val day: LocalDate, val start: Instant) : UndoRequest
}

internal sealed interface HistoryMessage {
    class Undone(val result: UndoResult) : HistoryMessage
    class UndoFailed : HistoryMessage
}
