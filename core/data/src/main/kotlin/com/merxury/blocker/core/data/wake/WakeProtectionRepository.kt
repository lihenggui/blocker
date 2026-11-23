// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.pm.PackageManager
import com.merxury.blocker.core.controllers.wake.ManagedComponentState
import com.merxury.blocker.core.database.record.PmBlockRecordDao
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.core.ifw.ManagedWakeRules
import com.merxury.core.ifw.WakeServiceFilter
import timber.log.Timber
import javax.inject.Inject

/**
 * The user whose components the wake feature changes. It enforces only on a device whose one user
 * is the owner, see [ReviewedWakePaths.evaluate], and observes otherwise.
 */
private val WAKE_USER = UserId.SYSTEM

/** What one run of the wake feature may change, decided once from the privileged channel. */
sealed interface WakeWrites {
    /** The channel may write what the feature changes: it enforces. */
    data object Enforce : WakeWrites

    /** The run observes only; [status] is the protection status that says why. */
    data class ObserveOnly(val status: String) : WakeWrites
}

/**
 * Applies the reviewed wake paths through the privileged channel the user chose. Only an identity
 * that may write system files, root or the system uid, changes anything; with any other state the
 * feature observes only.
 */
class WakeProtectionRepository @Inject constructor(
    private val repository: WakeRepository,
    private val ownership: WakeComponentOwnership,
    private val catalog: ReviewedWakePaths,
    private val rules: ManagedWakeRules,
    private val components: ManagedComponentState,
    private val channel: PrivilegedChannel,
    private val userBlocks: PmBlockRecordDao,
) {
    /**
     * What this run may change, from the channel acquired without a prompt: background work must
     * never show one. Pass it to [revokeStaleFirewallRules] and [applyReviewedPaths].
     */
    suspend fun writeAccess(): WakeWrites {
        val state = channel.connectIfGranted()
        if (!state.mayEnforce) return WakeWrites.ObserveOnly(state.observingStatus())
        // Shizuku before 13 runs as root but cannot run the calls that reach files.
        if (ownership.fileCallsRefused()) return WakeWrites.ObserveOnly("OBSERVING_WITHOUT_FILE_CALLS")
        return WakeWrites.Enforce
    }

    /**
     * Resets the wake rules of [packageName], or all of them when null. Returns false, without a
     * change, when the channel cannot write them. Never shows a prompt; a reset the user asked for
     * runs after the rule store's own calls, which connect the channel when needed.
     */
    suspend fun resetFirewall(packageName: String?): Boolean {
        if (writeAccess() != WakeWrites.Enforce) return false
        val alreadyPending = repository.rulesPending
        repository.markRulesPending(true)
        repository.resetFirewall(packageName)
        rules.reset(packageName)
        repository.markRulesPending(packageName != null && alreadyPending)
        return true
    }

    suspend fun applyReviewedPaths(capture: WakeImport, writes: WakeWrites, now: Long): String {
        val canManageComponents = writes == WakeWrites.Enforce && !capture.binderFallback
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
        if (writes is WakeWrites.ObserveOnly) return writes.status
        return reconcileFirewall(previous.filter { it.method == "IFW" }, candidates.filter { it.method == "IFW" })
    }

    suspend fun revokeStaleFirewallRules(writes: WakeWrites) {
        if (writes != WakeWrites.Enforce) return
        val installed = repository.protections().filter { it.method == "IFW" && it.configured }
        val staleTargets = installed.filterNot(catalog::isCurrent).map { it.target }.toSet()
        if (staleTargets.isEmpty()) return

        // Drop old revisions even when the following evidence collection fails.
        repository.markRulesPending(true)
        for (target in staleTargets) rules.reset(target)
        repository.saveProtections(installed.filter { it.target in staleTargets }.map { it.copy(configured = false) })
    }

    private suspend fun reconcileFirewall(previous: List<WakeProtection>, requested: List<WakeProtection>): String {
        if (requested.isEmpty() && previous.none { it.configured } && !repository.rulesPending) return "OK"

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
                var blockedByUser = false
                val restored = components.compareAndSet(old.target, old.component, WAKE_USER, disabled, original) {
                    blockedByUser = isBlockedByUser(old)
                    !blockedByUser
                }
                if (blockedByUser) {
                    // The user blocked it through Blocker too: it stays disabled and only the claim goes.
                    Timber.i("Keep ${old.target}/${old.component} disabled: the user blocked it")
                } else {
                    check(restored)
                }
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
            check(components.compareAndSet(path.target, path.component, WAKE_USER, requireNotNull(saved.previousState), disabled))
            ownership.save(saved.copy(configured = true, pendingChange = false))
        }
    }

    /**
     * Whether the user blocked [path]'s component through Blocker: it is in the record of the
     * components Blocker disabled. Read under the package's lock, which that record is written under.
     */
    private suspend fun isBlockedByUser(path: WakeProtection): Boolean = userBlocks.getByPackage(path.target, WAKE_USER.value).any { it.className == path.component }
}

/** Whether the wake feature may change the system through a channel in this state. */
private val ChannelState.mayEnforce: Boolean get() = this is ChannelState.Ready && identity.canWriteSystemFiles

/** The status of a run that observes only because of this state. */
private fun ChannelState.observingStatus(): String = when (this) {
    is ChannelState.Ready -> "OBSERVING_WITH_${identity.name}"
    is ChannelState.Unavailable -> "OBSERVING_WITHOUT_CHANNEL_${reason.name}"
}
