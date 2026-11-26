// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.pm.PackageManager
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.FirewallRead
import com.merxury.blocker.core.data.device.PmBlockRecords
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.privileged.wake.ManagedComponentState
import com.merxury.blocker.core.privileged.wake.WakeCallRefusedException
import com.merxury.blocker.core.privileged.wake.WakeJournalUnreadableException
import com.merxury.core.ifw.ManagedWakeRules
import com.merxury.core.ifw.WakeServiceFilter
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * PackageManager changes are scoped to the owner. Device-wide firewall rules are subject to the
 * cross-profile checks in [ReviewedWakePaths.evaluate].
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
    private val userBlocks: PmBlockRecords,
    private val userFirewall: UserWakeFirewall,
    private val lock: WakeRuleLock,
    private val componentStatus: ComponentStatusRepository,
) {
    /**
     * What this run may change, from the channel acquired without a prompt: background work must
     * never show one. Pass it to [revokeStaleFirewallRules] and [applyReviewedPaths].
     */
    suspend fun writeAccess(): WakeWrites {
        val state = channel.connectIfGranted()
        if (!state.mayEnforce) return WakeWrites.ObserveOnly(state.observingStatus())
        return when (journalAccess()) {
            JournalAccess.READABLE -> WakeWrites.Enforce
            JournalAccess.CALLS_REFUSED -> WakeWrites.ObserveOnly("OBSERVING_WITHOUT_FILE_CALLS")
            JournalAccess.UNREADABLE -> WakeWrites.ObserveOnly("OBSERVING_WITH_UNREADABLE_JOURNAL")
        }
    }

    /**
     * Resets the wake rules of [packageName], or all of them when null. Returns false, without a
     * change, when the channel cannot write them. Never shows a prompt; a reset the user asked for
     * runs after the rule store's own calls, which connect the channel when needed.
     *
     * The reset touches only the rule files and the firewall paths; the component journal holds
     * PackageManager changes only, so a journal this identity cannot read does not stop it.
     */
    suspend fun resetFirewall(packageName: String?): Boolean = lock.withLock {
        if (!repository.isOwnerProfile || !channel.connectIfGranted().mayEnforce || journalAccess() == JournalAccess.CALLS_REFUSED) return@withLock false
        withContext(NonCancellable) {
            val alreadyPending = repository.rulesPending
            repository.markRulesPending(true)
            repository.resetFirewall(packageName)
            rules.reset(packageName)
            userFirewall.forgetFirewallBlocks(packageName)
            repository.markRulesPending(packageName != null && alreadyPending)
        }
        true
    }

    /** Rebuilds the user's firewall file from the database while reconciliation holds the lock. */
    suspend fun syncUserFirewallRules(writes: WakeWrites) {
        if (writes == WakeWrites.Enforce) userFirewall.sync()
    }

    /**
     * Records the reviewed paths [capture] qualifies and, when [writes] allows, applies them.
     * A capture the channel could not finish only records them: evidence is missing, and the
     * channel that missed it would make the changes.
     */
    suspend fun applyReviewedPaths(capture: WakeImport, writes: WakeWrites, now: Long): String {
        val canManageComponents = writes == WakeWrites.Enforce && !capture.channelUnavailable
        if (canManageComponents) ownership.recover()
        val previous = repository.protections()
        val candidates = catalog.evaluate(
            repository.observedComponents(now - WAKE_HISTORY_RETENTION_MILLIS),
            capture.identities,
            capture.profiles,
            previous,
            repository.firewallResets(),
        ).filter { it.method != "IFW" || capture.secondaryPackagesAvailable }
        val knownIds = previous.map { it.pathId }.toSet()
        val newPaths = candidates.filter { it.pathId !in knownIds }
        if (newPaths.isNotEmpty()) repository.saveProtections(newPaths)
        if (canManageComponents) {
            reconcileComponents(previous, candidates.filter { it.method == "PM" })
        }
        if (capture.channelUnavailable) return channel.state.value.lostStatus()
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
        for (target in staleTargets) rules.removeTarget(target)
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
            refreshComponentStatus(old.target)
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
            val original = requireNotNull(saved.previousState)
            var superseded = false
            val confirmed = components.compareAndSet(
                path.target,
                path.component,
                WAKE_USER,
                original,
                disabled,
                afterConfirmed = { ownership.save(saved.copy(configured = true, pendingChange = false)) },
            ) {
                val currentUnderLock = catalog.currentState(path)
                superseded = currentUnderLock != original &&
                    (!path.pendingChange || currentUnderLock != disabled || isBlockedByUser(path))
                if (!superseded && currentUnderLock == original) forgetBlock(path)
                !superseded
            }
            if (superseded) {
                ownership.save(saved.copy(configured = false, pendingChange = false, disabledByUser = true))
            } else {
                check(confirmed)
                refreshComponentStatus(path.target)
            }
        }
    }

    private suspend fun refreshComponentStatus(target: String) {
        when (val result = componentStatus.refreshIfReadable(AppKey(target, WAKE_USER), FirewallRead.KEEP_CACHED)) {
            is ReadResult.Success -> Unit
            is ReadResult.Failure -> Timber.w("Cannot refresh wake component status for %s: %s", target, result.reason)
        }
    }

    private enum class JournalAccess { READABLE, UNREADABLE, CALLS_REFUSED }

    private suspend fun journalAccess(): JournalAccess = try {
        ownership.probeJournal()
        JournalAccess.READABLE
    } catch (e: WakeCallRefusedException) {
        Timber.w(e, "The privileged channel cannot run the calls that reach files")
        JournalAccess.CALLS_REFUSED
    } catch (e: WakeJournalUnreadableException) {
        Timber.w(e, "The component journal cannot be read under this identity")
        JournalAccess.UNREADABLE
    }

    private suspend fun forgetBlock(path: WakeProtection) = userBlocks.forget(listOf(path.componentKey()))

    /**
     * Whether the user blocked [path]'s component through Blocker: it is in the record of the
     * components Blocker disabled. Read under the package's lock, which that record is written under.
     * The wake feature drops that record only when it disables the component itself, and the seed
     * from the old component list leaves out what it claims, so a record of a claimed component
     * was made by a block after its change. A component the user disabled first is never claimed.
     */
    private suspend fun isBlockedByUser(path: WakeProtection): Boolean = userBlocks.contains(path.componentKey())

    private fun WakeProtection.componentKey() = ComponentKey(ComponentId(target, component), WAKE_USER)
}

/** Whether the wake feature may change the system through a channel in this state. */
private val ChannelState.mayEnforce: Boolean get() = this is ChannelState.Ready && identity.canWriteSystemFiles

/**
 * The status of a run whose reads the channel could not all make, given the channel's state after
 * them: why it is unavailable, or [UnavailableReason.FAILED] when it still counts as ready because
 * only its privileged process could not be reached.
 */
private fun ChannelState.lostStatus(): String = (this as? ChannelState.Unavailable ?: ChannelState.Unavailable(UnavailableReason.FAILED)).observingStatus()

/** The status of a run that observes only because of this state. */
private fun ChannelState.observingStatus(): String = when (this) {
    is ChannelState.Ready -> "OBSERVING_WITH_${identity.name}"
    is ChannelState.Unavailable -> "OBSERVING_WITHOUT_CHANNEL_${reason.name}"
}
