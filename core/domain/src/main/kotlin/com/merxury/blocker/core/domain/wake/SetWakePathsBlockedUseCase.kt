// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.wake.AutomaticWakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeBlockRepository
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakePathState
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SetWakePathsBlockedUseCase @Inject constructor(
    private val user: WakeBlockRepository,
    private val automatic: AutomaticWakeBlockRepository,
) {
    suspend operator fun invoke(paths: List<WakePathState>, blocked: Boolean): WakeBlockOutcome {
        if (paths.isEmpty()) return WakeBlockOutcome(emptySet(), null)
        val reviewed = automatic.blocks.first().groupBy { it.identity }
        val userPaths = mutableListOf<WakePathState>()
        val automaticIds = mutableMapOf<String, List<String>>()
        for (state in paths.distinctBy { it.path.pathKey }) {
            val matches = reviewed[state.path.identity].orEmpty()
            if (state.block == WakePathBlock.AUTOMATIC || (blocked && state.block == WakePathBlock.NONE && matches.isNotEmpty())) {
                automaticIds[state.path.pathKey] = matches.map { it.pathId }
            } else {
                userPaths += state
            }
        }

        val manual = if (blocked) {
            user.block(userPaths.map { it.path to it.path.blockMethod })
        } else {
            user.unblockPaths(userPaths.map { it.path })
        }
        val reviewedOutcome = automatic.setBlocked(automaticIds.values.flatten().toSet(), blocked)
        val completed = automaticIds.filterValues { ids -> ids.isNotEmpty() && ids.all { it in reviewedOutcome.done } }.keys
        val missing = automaticIds.filterValues { ids -> ids.isEmpty() || ids.any { it !in reviewedOutcome.done } }.keys
        val failure = when (val error = reviewedOutcome.failure) {
            null -> if (missing.isEmpty()) null else WakeBlockFailure.NotBlockable(missing.toList())
            is WakeBlockFailure.NotBlockable -> WakeBlockFailure.NotBlockable(missing.toList())
            else -> error
        }
        return manual + WakeBlockOutcome(completed, failure)
    }

    suspend operator fun invoke(blocks: Collection<AutomaticWakeBlock>, blocked: Boolean): WakeBlockOutcome = automatic.setBlocked(blocks.mapTo(mutableSetOf()) { it.pathId }, blocked)
}

internal operator fun WakeBlockOutcome.plus(other: WakeBlockOutcome) = WakeBlockOutcome(done + other.done, failure ?: other.failure)
