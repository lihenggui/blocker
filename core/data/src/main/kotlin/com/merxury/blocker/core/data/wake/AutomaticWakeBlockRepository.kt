// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.merxury.blocker.core.data.device.PmBlockRecords
import com.merxury.blocker.core.data.wake.AutomaticWakeBlockRepository.ChangeResult
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
    data class ChangeResult(val outcome: WakeBlockOutcome, val changedIds: Set<String>)

    val blocks: Flow<List<AutomaticWakeBlock>>

    /** Owner-profile components disabled by protection and not also claimed by the user. */
    val ownedComponents: Flow<Set<ComponentId>>

    /** Changes the reviewed paths by ID and requests reconciliation. */
    suspend fun setBlocked(pathIds: Set<String>, blocked: Boolean): ChangeResult

    /** Allows active reviewed paths involving [packageName]. The completed IDs had their flags changed. */
    suspend fun allowForApp(packageName: String): WakeBlockOutcome
}

internal class LocalAutomaticWakeBlockRepository @Inject constructor(
    private val history: WakeHistoryDao,
    private val userBlocks: PmBlockRecords,
    private val lock: WakeRuleLock,
    private val runs: WakeRunRequester,
    private val protection: WakeProtectionRepository,
) : AutomaticWakeBlockRepository {
    override val blocks = history.protections().map { rows ->
        rows.map { row ->
            AutomaticWakeBlock(
                row.pathId,
                row.caller,
                row.target,
                row.component,
                row.action,
                active = (row.configured || row.pendingChange) && !row.disabledByUser,
                allowed = row.disabledByUser,
                method = row.method,
            )
        }
    }.distinctUntilChanged()

    override val ownedComponents = combine(history.protections(), userBlocks.observe(UserId.SYSTEM)) { rows, user ->
        rows.asSequence()
            .filter { it.method == "PM" && (it.configured || it.componentWritePending) }
            .map { ComponentId(it.target, it.component) }
            .filterNot { ComponentKey(it, UserId.SYSTEM) in user }
            .toSet()
    }.distinctUntilChanged()

    override suspend fun setBlocked(pathIds: Set<String>, blocked: Boolean): ChangeResult = change(blocked, pathIds) { it.pathId in pathIds }

    override suspend fun allowForApp(packageName: String): WakeBlockOutcome = change(blocked = false) {
        !it.disabledByUser && (it.configured || it.reblockPending) && (it.caller == packageName || it.target == packageName)
    }.outcome

    private suspend fun change(
        blocked: Boolean,
        requested: Set<String>? = null,
        select: (WakeProtection) -> Boolean,
    ): ChangeResult {
        if (requested?.isEmpty() == true) return ChangeResult(WakeBlockOutcome(emptySet(), null), emptySet())
        var done = emptySet<String>()
        var changedIds = emptySet<String>()
        val failure = try {
            lock.withLock {
                val selected = history.readProtections().filter(select)
                if (selected.isNotEmpty() && protection.writeAccess() != WakeWrites.Enforce) return@withLock WakeBlockFailure.NoAccess
                val peers = selected.filter { blocked && it.requiresPeerFilter }
                val unsupported = if (peers.isNotEmpty() && !protection.supportsPeerFilter()) peers else emptyList()
                val rows = selected - unsupported.toSet()
                val changed = rows.filter { it.disabledByUser == blocked }.map { row ->
                    if (blocked && !row.configured && !row.pendingChange) {
                        row.copy(disabledByUser = false, pendingChange = true, previousState = null)
                    } else {
                        row.copy(disabledByUser = !blocked, pendingChange = row.pendingChange && (blocked || !row.reblockPending))
                    }
                }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    if (changed.isNotEmpty()) history.putProtections(changed)
                    changedIds = changed.mapTo(mutableSetOf()) { it.pathId }
                    done = rows.mapTo(mutableSetOf()) { it.pathId }
                    if (done.isNotEmpty()) runs.requestRun()
                }
                val missing = requested.orEmpty() - done
                when {
                    unsupported.isNotEmpty() -> WakeBlockFailure.SenderFilterUnsupported(unsupported.map { it.pathId })
                    missing.isNotEmpty() -> WakeBlockFailure.NotBlockable(missing.toList())
                    else -> null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cannot change automatic wake blocks")
            WakeBlockFailure.SystemError(e.message)
        }
        return ChangeResult(WakeBlockOutcome(done, failure), changedIds)
    }
}

internal val WakeProtection.requiresPeerFilter get() = method == "IFW" && caller != target

// A PM request has no rollback state until reconciliation prepares the actual system write.
internal val WakeProtection.reblockPending get() = pendingChange && !componentWritePending
internal val WakeProtection.componentWritePending get() = method == "PM" && pendingChange && previousState != null
