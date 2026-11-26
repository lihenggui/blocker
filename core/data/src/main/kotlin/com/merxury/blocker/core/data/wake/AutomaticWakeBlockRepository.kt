// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.merxury.blocker.core.data.device.PmBlockRecords
import com.merxury.blocker.core.database.wake.WakeHistoryDao
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

interface AutomaticWakeBlockRepository {
    val blocks: Flow<List<AutomaticWakeBlock>>

    /** Owner-profile components disabled by protection and not also claimed by the user. */
    val ownedComponents: Flow<Set<ComponentId>>

    /** Changes the reviewed paths by ID and requests reconciliation. */
    suspend fun setBlocked(pathIds: Set<String>, blocked: Boolean): WakeBlockOutcome

    /** Allows all reviewed paths that involve [packageName]. The completed IDs had their flags changed. */
    suspend fun allowForApp(packageName: String): WakeBlockOutcome
}

internal class LocalAutomaticWakeBlockRepository @Inject constructor(
    private val history: WakeHistoryDao,
    private val userBlocks: PmBlockRecords,
    private val lock: WakeRuleLock,
    private val runs: WakeRunRequester,
) : AutomaticWakeBlockRepository {
    override val blocks = history.protections().map { rows ->
        rows.map { row ->
            AutomaticWakeBlock(
                row.pathId,
                row.caller,
                row.target,
                row.component,
                row.action,
                active = row.configured && !row.disabledByUser,
                allowed = row.disabledByUser,
            )
        }
    }.distinctUntilChanged()

    override val ownedComponents = combine(history.protections(), userBlocks.observe(UserId.SYSTEM)) { rows, user ->
        rows.asSequence()
            .filter { it.method == "PM" && (it.configured || it.pendingChange) }
            .map { ComponentId(it.target, it.component) }
            .filterNot { ComponentKey(it, UserId.SYSTEM) in user }
            .toSet()
    }.distinctUntilChanged()

    override suspend fun setBlocked(pathIds: Set<String>, blocked: Boolean): WakeBlockOutcome = change(blocked, pathIds) { it.pathId in pathIds }

    override suspend fun allowForApp(packageName: String): WakeBlockOutcome = change(blocked = false) { !it.disabledByUser && (it.caller == packageName || it.target == packageName) }

    private suspend fun change(
        blocked: Boolean,
        requested: Set<String>? = null,
        select: (WakeProtection) -> Boolean,
    ): WakeBlockOutcome {
        if (requested?.isEmpty() == true) return WakeBlockOutcome(emptySet(), null)
        var done = emptySet<String>()
        val failure = try {
            lock.withLock {
                val rows = history.readProtections().filter(select)
                val changed = rows.filter { it.disabledByUser == blocked }.map { it.copy(disabledByUser = !blocked) }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    if (changed.isNotEmpty()) history.putProtections(changed)
                    done = rows.mapTo(mutableSetOf()) { it.pathId }
                    if (done.isNotEmpty()) runs.requestRun()
                }
            }
            val missing = requested.orEmpty() - done
            if (missing.isEmpty()) null else WakeBlockFailure.NotBlockable(missing.toList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cannot change automatic wake blocks")
            WakeBlockFailure.SystemError(e.message)
        }
        return WakeBlockOutcome(done, failure)
    }
}
