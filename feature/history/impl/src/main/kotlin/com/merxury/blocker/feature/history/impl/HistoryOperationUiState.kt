// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.history.UndoResult
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.core.ui.profile.ProfileLabel
import java.time.ZoneId
import kotlin.time.Instant

internal data class HistoryOperationUiState(
    val row: HistoryOperation? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val groups: List<HistoryAppGroup>? = null,
    val loadFailed: Boolean = false,
    val entriesFailed: Boolean = false,
    val undoneAt: Instant? = null,
    val laterCount: Int = 0,
    val confirmLater: Boolean = false,
    val isUndoing: Boolean = false,
    val message: HistoryMessage? = null,
) {
    val canUndo: Boolean get() = row?.operation?.canUndo == true
    val canUndoLater: Boolean get() = canUndo && laterCount > 0
}

internal data class HistoryAppGroup(
    val appIcon: ApkIcon,
    val appLabel: String,
    val entries: List<HistoryEntry>,
)

internal data class HistoryEntry(val key: ComponentKey, val profile: ProfileLabel?, val change: ComponentState)

internal enum class ComponentState { ON, OFF, UNKNOWN }

internal sealed interface HistoryMessage {
    class Undone(val result: UndoResult) : HistoryMessage
    class UndoFailed : HistoryMessage
}
