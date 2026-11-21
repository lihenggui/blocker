// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import android.content.pm.PackageManager
import com.merxury.blocker.core.controllers.root.api.ManagedComponentState
import com.merxury.blocker.core.controllers.wake.WakeAccess
import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.database.wake.WakeSourceStatus
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.core.ifw.ManagedWakeRules
import com.merxury.core.ifw.WakeServiceFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeProtectionManager @Inject constructor(
    private val repository: WakeRepository,
    private val catalog: ReviewedWakePaths,
    private val rules: ManagedWakeRules,
    private val components: ManagedComponentState,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val lock = Mutex()

    suspend fun reconcile(): Boolean = withContext(ioDispatcher) {
        lock.withLock {
            val now = System.currentTimeMillis()
            try {
                if (!repository.isOwnerProfile) {
                    repository.reportStatus(WakeSourceStatus("protection", now, "OWNER_PROFILE_REQUIRED"))
                    return@withLock true
                }
                val access = repository.access()
                if (access == WakeAccess.NONE) {
                    repository.reportStatus(WakeSourceStatus("authorization", now, "WAITING_FOR_PERMISSION"))
                    return@withLock true
                }
                val capture = repository.collect(now - repository.lastDiagnostic >= 86_400_000, access)
                val records = repository.attributionRecords(capture.boot)
                val relations = WakeAttribution.relationships(records)
                repository.saveAttribution(records, relations, WakeAttribution.continuations(records, relations))
                if (capture.profiles.isEmpty()) {
                    repository.reportStatus(WakeSourceStatus("protection", now, "PROFILE_SCOPE_UNAVAILABLE"))
                    return@withLock false
                }
                val previous = repository.protections()
                val candidates = catalog.evaluate(repository.observedComponents(now - 14L * 86400000), capture.identities, capture.profiles, previous)
                val knownIds = previous.map { it.pathId }.toSet()
                val newPaths = candidates.filter { it.pathId !in knownIds }
                if (newPaths.isNotEmpty()) repository.saveProtections(newPaths)
                if (access == WakeAccess.SHIZUKU_SHELL) {
                    repository.reportStatus(WakeSourceStatus("protection", now, "OBSERVING_WITH_SHELL"))
                    return@withLock true
                }
                reconcileComponents(previous, candidates.filter { it.method == "PM" })
                val status = reconcileFirewall(access, previous.filter { it.method == "IFW" }, candidates.filter { it.method == "IFW" })
                repository.reportStatus(WakeSourceStatus("protection", now, status))
                true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                repository.reportStatus(WakeSourceStatus("protection", now, e.message ?: e.javaClass.simpleName))
                false
            }
        }
    }

    private suspend fun reconcileFirewall(access: WakeAccess, previous: List<WakeProtection>, requested: List<WakeProtection>): String {
        if (requested.isEmpty() && previous.none { it.configured } && !repository.rulesPending) return "OK"
        // Only Shizuku 13+ supplies the package context used to authenticate file writes.
        if (access == WakeAccess.SHIZUKU_LEGACY_ROOT) return "IFW_REQUIRES_ROOT_OR_SHIZUKU_13"

        repository.markRulesPending(true)
        val peerUnavailable = requested.any { it.caller != it.target } && !rules.supportsPeerFilter()
        val desired = requested.filter { (!peerUnavailable || it.caller == it.target) && catalog.isCurrent(it) }
        rules.replace(desired.map { WakeServiceFilter(it.caller, it.target, it.component, it.action) })
        repository.saveProtections(previous.map { it.copy(configured = false) } + desired.map { it.copy(configured = true) })
        repository.markRulesPending(false)
        return if (peerUnavailable) "PEER_FILTER_UNAVAILABLE" else "OK"
    }

    private suspend fun reconcileComponents(previous: List<WakeProtection>, desired: List<WakeProtection>) {
        val disabled = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val active = previous.filter { it.method == "PM" && (it.configured || it.pendingChange) }.associateBy { it.pathId }
        val ids = desired.map { it.pathId }.toSet()
        for (old in active.values.filter { it.pathId !in ids }) {
            val original = old.previousState ?: continue
            if (catalog.sameInstallation(old) && catalog.currentState(old) == disabled) {
                check(components.compareAndSet(old.target, old.component, disabled, original))
            }
            repository.saveProtections(listOf(old.copy(configured = false, pendingChange = false)))
        }
        for (candidate in desired) {
            if (!catalog.isCurrent(candidate)) continue
            val old = active[candidate.pathId]
            val path = if (old != null && old.targetRevision != candidate.targetRevision) {
                val replacement = if (catalog.sameInstallation(old)) {
                    candidate.copy(configured = old.configured, pendingChange = old.pendingChange, previousState = old.previousState)
                } else {
                    candidate
                }
                repository.saveProtections(listOf(replacement))
                replacement
            } else {
                candidate
            }
            val current = catalog.currentState(path) ?: continue
            if (path.configured && current == disabled) continue
            if (path.configured || (path.pendingChange && current != disabled && current != path.previousState)) {
                // A manual change supersedes the automatic decision.
                repository.saveProtections(listOf(path.copy(configured = false, pendingChange = false, disabledByUser = true)))
                continue
            }
            if (!path.pendingChange && current != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) continue
            val saved = path.copy(previousState = path.previousState ?: current, pendingChange = true)
            repository.saveProtections(listOf(saved))
            check(components.compareAndSet(path.target, path.component, requireNotNull(saved.previousState), disabled))
            repository.saveProtections(listOf(saved.copy(configured = true, pendingChange = false)))
        }
    }
}
