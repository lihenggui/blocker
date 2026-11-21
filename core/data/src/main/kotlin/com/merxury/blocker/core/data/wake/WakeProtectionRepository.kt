// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.pm.PackageManager
import com.merxury.blocker.core.controllers.root.api.ManagedComponentState
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.model.wake.WakeAccess
import com.merxury.core.ifw.ManagedWakeRules
import com.merxury.core.ifw.WakeResetTransport
import com.merxury.core.ifw.WakeServiceFilter
import javax.inject.Inject

class WakeProtectionRepository @Inject constructor(
    private val repository: WakeRepository,
    private val ownership: WakeComponentOwnership,
    private val catalog: ReviewedWakePaths,
    private val rules: ManagedWakeRules,
    private val components: ManagedComponentState,
) {
    suspend fun resetFirewall(packageName: String?, transport: WakeResetTransport): Boolean {
        val alreadyPending = repository.rulesPending
        repository.markRulesPending(true)
        repository.resetFirewall(packageName)
        val manualRulesRemain = rules.reset(packageName, transport)
        repository.markRulesPending(packageName != null && alreadyPending)
        return manualRulesRemain
    }

    suspend fun applyReviewedPaths(capture: WakeImport, access: WakeAccess, now: Long): String {
        val canManageComponents = access != WakeAccess.SHIZUKU_SHELL && !capture.binderFallback
        if (canManageComponents) ownership.recover()
        val previous = repository.protections()
        val candidates = catalog.evaluate(repository.observedComponents(now - WAKE_HISTORY_RETENTION_MILLIS), capture.identities, capture.profiles, previous, repository.firewallResets())
        val knownIds = previous.map { it.pathId }.toSet()
        val newPaths = candidates.filter { it.pathId !in knownIds }
        if (newPaths.isNotEmpty()) repository.saveProtections(newPaths)
        if (canManageComponents) {
            reconcileComponents(previous, candidates.filter { it.method == "PM" })
        }
        if (capture.binderFallback) return "SHIZUKU_USER_SERVICE_UNAVAILABLE"
        if (access == WakeAccess.SHIZUKU_SHELL) return "OBSERVING_WITH_SHELL"
        return reconcileFirewall(access, previous.filter { it.method == "IFW" }, candidates.filter { it.method == "IFW" })
    }

    suspend fun revokeStaleFirewallRules(access: WakeAccess) {
        val transport = when (access) {
            WakeAccess.DIRECT_ROOT -> WakeResetTransport.DIRECT_ROOT
            WakeAccess.SHIZUKU_ROOT -> WakeResetTransport.SHIZUKU_ROOT
            else -> return
        }
        val installed = repository.protections().filter { it.method == "IFW" && it.configured }
        val staleTargets = installed.filterNot(catalog::isCurrent).map { it.target }.toSet()
        if (staleTargets.isEmpty()) return

        // Drop old revisions even when the following evidence collection fails.
        repository.markRulesPending(true)
        for (target in staleTargets) rules.reset(target, transport)
        repository.saveProtections(installed.filter { it.target in staleTargets }.map { it.copy(configured = false) })
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
            ownership.save(old.copy(configured = false, pendingChange = false))
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
                ownership.save(replacement)
                replacement
            } else {
                candidate
            }
            val current = catalog.currentState(path) ?: continue
            if (path.configured && current == disabled) continue
            if (path.configured || (path.pendingChange && current != disabled && current != path.previousState)) {
                // A manual change supersedes the automatic decision.
                ownership.save(path.copy(configured = false, pendingChange = false, disabledByUser = true))
                continue
            }
            if (!path.pendingChange && current != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) continue
            val saved = path.copy(previousState = path.previousState ?: current, pendingChange = true)
            ownership.save(saved)
            check(components.compareAndSet(path.target, path.component, requireNotNull(saved.previousState), disabled))
            ownership.save(saved.copy(configured = true, pendingChange = false))
        }
    }
}
