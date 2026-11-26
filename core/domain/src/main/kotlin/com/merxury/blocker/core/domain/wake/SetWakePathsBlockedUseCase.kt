// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.wake.AutomaticWakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeBlockRepository
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakePathState
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SetWakePathsBlockedUseCase @Inject constructor(
    private val user: WakeBlockRepository,
    private val automatic: AutomaticWakeBlockRepository,
) {
    /** Changes the user and automatic blocks that govern the selected paths. */
    suspend operator fun invoke(paths: List<WakePathState>, blocked: Boolean): WakeBlockOutcome {
        if (paths.isEmpty()) return WakeBlockOutcome(emptySet(), null)
        val reviewed = AutomaticWakeBlockIndex(automatic.blocks.first())
        val userPaths = mutableMapOf<String, WakePath>()
        val automaticIds = mutableMapOf<String, List<String>>()
        for (state in paths.distinctBy { it.path.pathKey }) {
            val path = state.path
            val ids = reviewed.matching(path).filter { if (blocked) it.allowed else it.active }.map { it.pathId }
            val automaticOnly = if (blocked) ids.isNotEmpty() else state.block == WakePathBlock.AUTOMATIC
            if (!automaticOnly) userPaths[path.pathKey] = path
            if (automaticOnly || ids.isNotEmpty()) automaticIds[path.pathKey] = ids
        }

        val manual = if (blocked) {
            user.block(userPaths.values.map { it to it.blockMethod })
        } else {
            user.unblockPaths(userPaths.values.toList())
        }
        val reviewedOutcome = automatic.setBlocked(automaticIds.values.flatten().toSet(), blocked)
        val reviewedDone = automaticIds.filterValues { ids -> ids.isNotEmpty() && ids.all { it in reviewedOutcome.done } }.keys
        val missing = automaticIds.keys - reviewedDone
        val failure = when (val error = reviewedOutcome.failure) {
            null -> if (missing.isEmpty()) null else WakeBlockFailure.NotBlockable(missing.toList())
            is WakeBlockFailure.NotBlockable -> WakeBlockFailure.NotBlockable(missing.toList())
            else -> error
        }
        val completed = (manual.done - automaticIds.keys) + reviewedDone.filter { it !in userPaths || it in manual.done }
        return WakeBlockOutcome(completed, manual.failure ?: failure)
    }

    suspend operator fun invoke(blocks: Collection<AutomaticWakeBlock>, blocked: Boolean): WakeBlockOutcome = automatic.setBlocked(blocks.mapTo(mutableSetOf()) { it.pathId }, blocked)
}

internal operator fun WakeBlockOutcome.plus(other: WakeBlockOutcome) = WakeBlockOutcome(done + other.done, failure ?: other.failure)
