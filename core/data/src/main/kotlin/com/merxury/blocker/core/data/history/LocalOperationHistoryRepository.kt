/*
 * Copyright 2026 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.core.data.history

import com.merxury.blocker.core.data.device.LocalComponentStatusRepository
import com.merxury.blocker.core.database.history.OperationHistoryDao
import com.merxury.blocker.core.database.history.asExternalModel
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.history.OperationEntry
import com.merxury.blocker.core.model.history.OperationSource
import com.merxury.blocker.core.model.history.UndoResult
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

@Singleton
internal class LocalOperationHistoryRepository @Inject constructor(
    private val history: OperationHistoryDao,
    private val components: LocalComponentStatusRepository,
    private val recorder: OperationRecorder,
) : OperationHistoryRepository {
    private val undoLock = Mutex()

    override fun observeOperations() = history.observeOperations().map { operations ->
        operations.filter { it.entryCount > 0 || it.source == OperationSource.UNDO }.map { it.asExternalModel() }
    }

    override suspend fun getEntries(operationId: Long) = history.getEntries(operationId).map { it.asExternalModel() }

    override suspend fun undoOperation(id: Long): UndoResult = undoLock.withLock {
        val original = history.observeOperations().first().find { it.id == id }
        if (original == null || original.source == OperationSource.UNDO || original.undoneByOperationId != null) return@withLock EmptyResult
        undo(getEntries(id), ref = id.toString())
    }

    override suspend fun undoSince(since: Instant): UndoResult = undoLock.withLock {
        undo(history.getEntriesSince(since).map { it.asExternalModel() }, ref = null)
    }

    private suspend fun undo(entries: List<OperationEntry>, ref: String?): UndoResult {
        if (entries.isEmpty()) return EmptyResult
        val targets = entries.groupBy { it.key }.mapValues { (_, changes) -> UndoTarget(changes.first().before, changes.last().after) }
        val plans = targets.entries.groupBy { (key, _) -> AppKey(key.id.packageName, key.userId) }.flatMap { (app, changes) ->
            planApp(app, changes)
        }
        return recorder.withOperation(OperationSource.UNDO, ref) { operation ->
            val recording = operation as OperationScope.Recording
            val outcomes = restore(plans, recording)
            if (outcomes.values.any { it.complete }) {
                val undoId = recording.ensureHeader()
                val completed = entries.groupBy { it.operationId }.filterValues { changes ->
                    changes.all { outcomes.getValue(it.key).complete }
                }
                history.markUndone(completed.keys.toList(), undoId)
            }
            val counts = outcomes.values.groupingBy { it }.eachCount()
            UndoResult(
                restored = counts[UndoOutcome.RESTORED] ?: 0,
                alreadyAtTarget = counts[UndoOutcome.ALREADY_AT_TARGET] ?: 0,
                skipped = counts[UndoOutcome.SKIPPED] ?: 0,
                failed = counts[UndoOutcome.FAILED] ?: 0,
            )
        }
    }

    private suspend fun planApp(app: AppKey, changes: List<Map.Entry<ComponentKey, UndoTarget>>): List<ComponentUndoPlan> {
        val failure = (components.refresh(app) as? ReadResult.Failure)?.reason
        // An explicit refresh keeps cached declarations when it cannot read the manifest.
        if (failure is ReadFailure.PackageNotFound || failure is ReadFailure.ManifestUnreadable || failure is ReadFailure.UserNotReachable) {
            return changes.map { (key, _) -> ComponentUndoPlan.skipped(key) }
        }
        val current = components.components(app).first().associateBy { it.declaration.id }
        val wakeCandidates = changes.mapNotNullTo(mutableSetOf()) { (key, target) ->
            key.id.takeIf { target.before.pm != target.after.pm && current[it]?.state?.pm == PmSetting.DISABLED }
        }
        val wakeOwned = components.claimedWakePmBlocks(app.userId, wakeCandidates).toSet()
        return changes.map { (key, target) ->
            val status = current[key.id]
            if (status == null) ComponentUndoPlan.skipped(key) else target.plan(key, status, key.id in wakeOwned)
        }
    }

    private suspend fun restore(plans: List<ComponentUndoPlan>, operation: OperationScope.Recording): Map<ComponentKey, UndoOutcome> {
        val outcomes = plans.associate { it.key to it.outcome }.toMutableMap()
        val requests = mutableMapOf<UndoRequest, MutableList<ComponentDeclaration>>()
        val changedIfw = mutableMapOf<ComponentId, IfwBlock>()
        for (plan in plans) {
            for (request in plan.requests) {
                requests.getOrPut(request) { mutableListOf() }.add(checkNotNull(plan.declaration))
            }
        }
        for ((request, declarations) in requests) {
            val pending = declarations.mapTo(mutableSetOf()) { ComponentKey(it.id, request.user) }
            components.restore(request.controlRequest(declarations), operation).collect { event ->
                if (event is ControlEvent.PackageDone) {
                    for (outcome in event.outcomes) {
                        if (pending.remove(outcome.key)) {
                            val written = when (request.layer) {
                                ControlLayer.PACKAGE_MANAGER -> outcome.key.id in event.pmWrites
                                ControlLayer.INTENT_FIREWALL -> operation.wroteIfw(outcome.key)
                            }
                            if (request.layer == ControlLayer.INTENT_FIREWALL && written) changedIfw[outcome.key.id] = outcome.state.ifw
                            val result = request.outcome(outcome, written)
                            outcomes[outcome.key] = maxOf(outcomes.getValue(outcome.key), result)
                        }
                    }
                }
            }
            pending.forEach { outcomes[it] = UndoOutcome.FAILED }
        }
        // IFW is device-wide. A write for one user can move another user's component off its target.
        plans.forEach { plan ->
            val target = plan.ifwTarget
            val current = changedIfw[plan.key.id]
            if (target != null && current != null && current != target) {
                outcomes[plan.key] = maxOf(outcomes.getValue(plan.key), UndoOutcome.SKIPPED)
            }
        }
        return outcomes
    }

    private companion object {
        val EmptyResult = UndoResult(restored = 0, alreadyAtTarget = 0, skipped = 0, failed = 0)
    }
}
