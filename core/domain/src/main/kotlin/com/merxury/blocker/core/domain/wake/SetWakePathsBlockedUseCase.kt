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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SetWakePathsBlockedUseCase @Inject constructor(
    private val user: WakeBlockRepository,
    private val automatic: AutomaticWakeBlockRepository,
) {
    data class BatchResult(val outcome: WakeBlockOutcome, val undo: Undo)

    class Undo internal constructor(
        internal val added: WakeBlockRepository.AddedBlocks?,
        internal val automaticIds: Set<String>,
    ) {
        val hasChanges get() = added?.records?.isNotEmpty() == true || automaticIds.isNotEmpty()
    }

    /** Changes the user and automatic blocks that govern the selected paths. */
    suspend operator fun invoke(paths: List<WakePathState>, blocked: Boolean): WakeBlockOutcome = change(paths, emptySet(), blocked, recordUndo = false).outcome

    /** Blocks only allowed paths and records the exact entries written, including partial writes. */
    suspend fun blockWithUndo(paths: List<WakePathState>, blocks: Collection<AutomaticWakeBlock>): BatchResult {
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) {
            change(
                paths.filter { it.block == WakePathBlock.NONE },
                blocks.filter { it.allowed }.mapTo(mutableSetOf()) { it.pathId },
                blocked = true,
                recordUndo = true,
            )
        }
    }

    suspend fun undo(token: Undo): WakeBlockOutcome {
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) {
            val manual = token.added?.let { user.undoAddedBlocks(it) } ?: EmptyOutcome
            manual + automatic.setBlocked(token.automaticIds, blocked = false)
        }
    }

    private suspend fun change(
        paths: List<WakePathState>,
        unmatchedIds: Set<String>,
        blocked: Boolean,
        recordUndo: Boolean,
    ): BatchResult {
        if (paths.isEmpty() && unmatchedIds.isEmpty()) return BatchResult(EmptyOutcome, Undo(null, emptySet()))
        val reviewed = AutomaticWakeBlockIndex(if (paths.isEmpty()) emptyList() else automatic.blocks.first())
        val userPaths = mutableMapOf<String, WakePath>()
        val automaticIds = mutableMapOf<String, List<String>>()
        for (state in paths.distinctBy { it.path.pathKey }) {
            val path = state.path
            val ids = reviewed.matching(path).filter {
                if (blocked) it.allowed || (recordUndo && it.active) else it.active
            }.map { it.pathId }
            val automaticOnly = if (blocked) ids.isNotEmpty() else state.block == WakePathBlock.AUTOMATIC
            if (!automaticOnly) userPaths[path.pathKey] = path
            if (automaticOnly || ids.isNotEmpty()) automaticIds[path.pathKey] = ids
        }

        val requests = userPaths.values.map { it to it.blockMethod }
        val (manual, added) = when {
            recordUndo -> user.blockRecordingChanges(requests)
            blocked -> user.block(requests) to null
            else -> user.unblockPaths(userPaths.values.toList()) to null
        }
        val reviewedOutcome = automatic.setBlocked(automaticIds.values.flatten().toSet() + unmatchedIds, blocked)
        val reviewedDone = automaticIds.filterValues { ids -> ids.isNotEmpty() && ids.all { it in reviewedOutcome.done } }.keys
        val missing = (automaticIds.keys - reviewedDone) + (unmatchedIds - reviewedOutcome.done)
        val failure = when (val error = reviewedOutcome.failure) {
            null -> if (missing.isEmpty()) null else WakeBlockFailure.NotBlockable(missing.toList())
            is WakeBlockFailure.NotBlockable -> WakeBlockFailure.NotBlockable(missing.toList())
            else -> error
        }
        val completed = (manual.done - automaticIds.keys) + reviewedDone.filter { it !in userPaths || it in manual.done } +
            unmatchedIds.intersect(reviewedOutcome.done)
        return BatchResult(WakeBlockOutcome(completed, manual.failure ?: failure), Undo(added, reviewedOutcome.changed))
    }

    suspend operator fun invoke(blocks: Collection<AutomaticWakeBlock>, blocked: Boolean): WakeBlockOutcome = automatic.setBlocked(blocks.mapTo(mutableSetOf()) { it.pathId }, blocked)
}

private val EmptyOutcome = WakeBlockOutcome(emptySet(), null)

internal operator fun WakeBlockOutcome.plus(other: WakeBlockOutcome) = WakeBlockOutcome(done + other.done, failure ?: other.failure, changed + other.changed)
