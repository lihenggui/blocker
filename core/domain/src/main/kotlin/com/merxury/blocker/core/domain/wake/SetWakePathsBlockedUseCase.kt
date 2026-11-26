// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.device.BackgroundUsageFailure
import com.merxury.blocker.core.data.device.BackgroundUsageRepository
import com.merxury.blocker.core.data.device.BackgroundUsageResult
import com.merxury.blocker.core.data.wake.AutomaticWakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeBlockRepository
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockMethod
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
    private val backgroundUsage: BackgroundUsageRepository,
) {
    data class BatchResult(val outcome: WakeBlockOutcome, val undo: Undo)

    class Undo internal constructor(
        internal val snapshot: WakeBlockRepository.Snapshot?,
        internal val automaticIds: Set<String>,
        internal val restrictedPackages: Set<String>,
    ) {
        val hasChanges get() = snapshot?.hasChanges == true || automaticIds.isNotEmpty() || restrictedPackages.isNotEmpty()
    }

    /** Changes the manual, automatic and background usage blocks that govern the selected paths. */
    suspend operator fun invoke(paths: List<WakePathState>, blocked: Boolean): WakeBlockOutcome = change(paths, emptySet(), blocked, recordUndo = false).outcome

    /** Blocks only allowed paths and records the changes made, including partially successful batches. */
    suspend fun blockWithUndo(paths: List<WakePathState>, blocks: Collection<AutomaticWakeBlock>): BatchResult {
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) {
            val allowedPaths = paths.filter { it.block == WakePathBlock.NONE }
            val allowedBlocks = blocks.filter { it.allowed }
            change(
                allowedPaths,
                allowedBlocks.mapTo(mutableSetOf()) { it.pathId },
                blocked = true,
                recordUndo = true,
            )
        }
    }

    suspend fun undo(token: Undo): WakeBlockOutcome {
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) {
            val manual = token.snapshot?.let { user.undoBlockChanges(it) } ?: EmptyOutcome
            val reviewed = automatic.setBlocked(token.automaticIds, blocked = false).outcome
            token.restrictedPackages.fold(manual + reviewed) { outcome, target ->
                outcome + setBackgroundAllowed(AppKey(target, UserId.SYSTEM), allowed = true, listOf("background:$target"))
            }
        }
    }

    private suspend fun change(
        paths: List<WakePathState>,
        unmatchedIds: Set<String>,
        blocked: Boolean,
        recordUndo: Boolean,
    ): BatchResult {
        if (paths.isEmpty() && unmatchedIds.isEmpty()) return BatchResult(EmptyOutcome, Undo(null, emptySet(), emptySet()))
        val (backgroundPaths, rulePaths) = paths.distinctBy { it.path.pathKey }.partition { it.path.blockMethod == WakeBlockMethod.BACKGROUND_RESTRICTION }
        val reviewed = AutomaticWakeBlockIndex(if (rulePaths.isEmpty()) emptyList() else automatic.blocks.first())
        val userPaths = mutableMapOf<String, WakePath>()
        val automaticIds = mutableMapOf<String, List<String>>()
        for (state in rulePaths) {
            val path = state.path
            val ids = reviewed.matching(path).filter {
                if (blocked) it.allowed else it.active
            }.map { it.pathId }
            val automaticOnly = if (blocked) ids.isNotEmpty() else state.block == WakePathBlock.AUTOMATIC
            if (!automaticOnly) userPaths[path.pathKey] = path
            if (automaticOnly || ids.isNotEmpty()) automaticIds[path.pathKey] = ids
        }

        val requests = userPaths.values.map { it to it.blockMethod }
        val (manual, snapshot) = when {
            requests.isEmpty() -> EmptyOutcome to null
            recordUndo -> user.blockRecordingChanges(requests)
            blocked -> user.block(requests) to null
            else -> user.unblockPaths(userPaths.values.toList()) to null
        }
        val (reviewedOutcome, changedIds) = automatic.setBlocked(automaticIds.values.flatten().toSet() + unmatchedIds, blocked)
        val reviewedDone = automaticIds.filterValues { ids -> ids.isNotEmpty() && ids.all { it in reviewedOutcome.done } }.keys
        val missing = (automaticIds.keys - reviewedDone) + (unmatchedIds - reviewedOutcome.done)
        val failure = when (val error = reviewedOutcome.failure) {
            null -> if (missing.isEmpty()) null else WakeBlockFailure.NotBlockable(missing.toList())
            is WakeBlockFailure.NotBlockable -> WakeBlockFailure.NotBlockable(missing.toList())
            else -> error
        }
        val completed = (manual.done - automaticIds.keys) + reviewedDone.filter { it !in userPaths || it in manual.done } +
            unmatchedIds.intersect(reviewedOutcome.done)
        var outcome = WakeBlockOutcome(completed, manual.failure ?: failure)
        val restrictedPackages = mutableSetOf<String>()
        for ((target, group) in backgroundPaths.groupBy { it.path.target }) {
            val app = AppKey(target, UserId.SYSTEM)
            val before = if (recordUndo) backgroundUsage.read(app) else null
            val wasAllowed = recordUndo && (before !is BackgroundUsageResult.Success || before.value.allowed)
            val background = setBackgroundAllowed(app, allowed = !blocked, group.map { it.path.pathKey })
            if (wasAllowed && background.done.isNotEmpty()) restrictedPackages += target
            outcome += background
        }
        return BatchResult(outcome, Undo(snapshot, changedIds, restrictedPackages))
    }

    private suspend fun setBackgroundAllowed(app: AppKey, allowed: Boolean, pathKeys: List<String>): WakeBlockOutcome {
        val failure = when (val result = backgroundUsage.setAllowed(app, allowed)) {
            is BackgroundUsageResult.Success -> WakeBlockFailure.BackgroundNotChangeable.takeIf { result.value.allowed != allowed }

            is BackgroundUsageResult.Failure -> when (result.reason) {
                BackgroundUsageFailure.NO_ACCESS -> WakeBlockFailure.NoAccess
                BackgroundUsageFailure.NOT_CHANGEABLE -> WakeBlockFailure.BackgroundNotChangeable
                BackgroundUsageFailure.UNSUPPORTED, BackgroundUsageFailure.NOT_INSTALLED -> WakeBlockFailure.NotBlockable(pathKeys)
                BackgroundUsageFailure.SYSTEM_ERROR -> WakeBlockFailure.SystemError(null)
            }
        }
        return WakeBlockOutcome(if (failure == null) pathKeys.toSet() else emptySet(), failure)
    }

    suspend operator fun invoke(blocks: Collection<AutomaticWakeBlock>, blocked: Boolean): WakeBlockOutcome = automatic.setBlocked(blocks.mapTo(mutableSetOf()) { it.pathId }, blocked).outcome
}

private val EmptyOutcome = WakeBlockOutcome(emptySet(), null)

internal operator fun WakeBlockOutcome.plus(other: WakeBlockOutcome) = WakeBlockOutcome(done + other.done, failure ?: other.failure)
