// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import com.merxury.blocker.core.data.device.BackgroundUsageFailure
import com.merxury.blocker.core.domain.wake.RestoreAppWakeChangesUseCase
import com.merxury.blocker.core.domain.wake.SetWakePathsBlockedUseCase
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.feature.wake.api.R.plurals
import com.merxury.blocker.feature.wake.api.R.string
import com.merxury.blocker.core.ui.R.string as coreString

internal data class WakeNotice(val id: Long, val text: WakeText, val undo: WakeUndo?)

internal sealed interface WakeUndo {
    data class Block(val token: SetWakePathsBlockedUseCase.Undo, val background: Boolean) : WakeUndo
    data class Restore(val token: RestoreAppWakeChangesUseCase.Undo) : WakeUndo
}

internal fun blockedCount(count: Int) = WakeText.Count(plurals.feature_wake_api_blocked_count, count.toLong())

internal fun blockFailureText(failure: WakeBlockFailure?, succeeded: Int, failed: Int, component: String? = null): WakeText {
    val notBlocked = WakeText.Count(plurals.feature_wake_api_not_blocked_count, failed.coerceAtLeast(1).toLong())
    if (succeeded > 0) return WakeText.Resource(string.feature_wake_api_list_pair, blockedCount(succeeded), notBlocked)
    return when (failure) {
        is WakeBlockFailure.SenderFilterUnsupported -> WakeText.Resource(string.feature_wake_api_sender_unsupported)
        WakeBlockFailure.NeedsRoot -> WakeText.Resource(coreString.core_ui_reason_shizuku_shell)
        WakeBlockFailure.NoAccess -> WakeText.Resource(coreString.core_ui_reason_no_access)
        WakeBlockFailure.BackgroundNotChangeable -> WakeText.Resource(string.feature_wake_api_background_not_changeable)
        else -> component?.let { WakeText.Resource(coreString.core_ui_block_component_failed, it.substringAfterLast('.')) } ?: notBlocked
    }
}

internal fun BackgroundUsageFailure.wakeFailure(): WakeBlockFailure = when (this) {
    BackgroundUsageFailure.NO_ACCESS -> WakeBlockFailure.NoAccess
    BackgroundUsageFailure.NOT_CHANGEABLE -> WakeBlockFailure.BackgroundNotChangeable
    else -> WakeBlockFailure.SystemError(name)
}
