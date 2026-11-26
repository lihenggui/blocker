// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.FirewallRead
import com.merxury.blocker.core.data.device.PmBlockClaimObserver
import com.merxury.blocker.core.data.device.PmBlockRecords
import com.merxury.blocker.core.database.record.PmBlockRecordEntity
import com.merxury.blocker.core.database.wake.WakeBlockDao
import com.merxury.blocker.core.database.wake.WakeBlockEntity
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ComponentWriteObserver
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.control.isPmClear
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.model.wake.HoldCause
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.privileged.withoutPermissionPrompts
import com.merxury.blocker.core.utils.currentUserId
import com.merxury.core.ifw.ManagedWakeRules
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal class LocalWakeBlockRepository @Inject constructor(
    private val dao: WakeBlockDao,
    private val firewall: UserWakeFirewall,
    private val managedRules: ManagedWakeRules,
    private val components: ComponentStatusRepository,
    private val receivers: WakeReceiverResolver,
    private val channel: PrivilegedChannel,
    private val lock: WakeRuleLock,
    @Dispatcher(IO) private val io: CoroutineDispatcher,
    private val automatic: AutomaticWakeBlockRepository,
    private val pmBlocks: PmBlockRecords,
    @ApplicationContext private val context: Context,
) : WakeBlockRepository {
    override val blocks = dao.observeAll().map { rows -> rows.map(WakeBlockEntity::toModel) }

    override suspend fun block(requests: List<Pair<WakePath, WakeBlockMethod>>): WakeBlockOutcome = blockRequests(requests, snapshot = null)

    override suspend fun blockRecordingChanges(requests: List<Pair<WakePath, WakeBlockMethod>>): Pair<WakeBlockOutcome, WakeBlockRepository.Snapshot> {
        val snapshot = BlockSnapshot()
        val outcome = blockRequests(requests, snapshot)
        return outcome to snapshot
    }

    override suspend fun undoBlockChanges(snapshot: WakeBlockRepository.Snapshot): WakeBlockOutcome {
        require(snapshot is BlockSnapshot)
        return change(hasChanges = { snapshot.hasChanges }) { identity ->
            if (identity == null) return@change WakeBlockOutcome(emptySet(), WakeBlockFailure.NoAccess)
            commitChange { restoreSnapshot(snapshot, identity) }
        }
    }

    private suspend fun restoreSnapshot(snapshot: BlockSnapshot, identity: PrivilegedIdentity): WakeBlockOutcome {
        val current = dao.all().associateBy { it.pathKey }
        val rows = snapshot.rows.filter { current[it.written.pathKey] == it.written }
        val replaced = (snapshot.rows - rows.toSet()).mapTo(mutableSetOf()) { it.written.pathKey }
        val activePaths = snapshot.pathComponents.filterKeys { it !in replaced }
        val activeComponents = activePaths.values.flatten().toSet()
        val batch = BlockBatch(snapshot.rows.map { it.written.pathKey } + activePaths.keys)
        val failures = restorePmSettings(snapshot, activeComponents)
        val released = snapshot.claims.filter { it.componentId in activeComponents && it.componentId !in failures }
        val claimFailure = failureOf("restore wake PM block claims") {
            pmBlocks.forgetIfUnchanged(released)
            null
        }
        val releasedIds = released.mapTo(mutableSetOf()) { it.componentId }
        val failedPaths = activePaths.mapNotNull { (path, components) ->
            val failure = components.firstNotNullOfOrNull(failures::get)
                ?: claimFailure?.takeIf { components.any(releasedIds::contains) }
            failure?.let { path to it }
        }.toMap()
        failedPaths.forEach(batch::record)
        val eligibleRows = rows.filterNot { it.written.pathKey in failedPaths }
        restoreRows(eligibleRows, current.values, identity, batch)
        val rowKeys = snapshot.rows.mapTo(mutableSetOf()) { it.written.pathKey }
        activePaths.forEach { (path, components) ->
            if (path !in rowKeys && components.any { it in snapshot.pm || it in releasedIds }) {
                batch.record(path, failedPaths[path])
            }
        }
        return batch.outcome()
    }

    private suspend fun restorePmSettings(snapshot: BlockSnapshot, active: Set<ComponentId>): Map<ComponentId, WakeBlockFailure> {
        val claims = snapshot.claims.associateBy { it.componentId }
        val guard = object : ComponentWriteObserver {
            override suspend fun selectPmRestoreComponents(app: AppKey, components: Set<ComponentId>): Set<ComponentId> {
                val current = pmBlocks.recordsFor(app).associateBy { it.componentId }
                return components.filterTo(mutableSetOf()) { id ->
                    val added = claims[id]
                    val existing = current[id]
                    // A batch claim must still exist. An ignored insert may only leave the old claim.
                    if (added != null) existing == added else existing == null || existing == snapshot.priorClaims[id]
                }
            }

            override fun canStart() = true
            override fun onWrite(components: Collection<ComponentId>) = Unit
        }
        val failures = mutableMapOf<ComponentId, WakeBlockFailure>()
        val writes = snapshot.pm.filterKeys { it in active }.values
        for ((key, group) in writes.groupBy { it.declaration.id.packageName to it.before }) {
            val goal = when (key.second) {
                PmSetting.DEFAULT -> ControlGoal.UNBLOCK
                PmSetting.ENABLED -> ControlGoal.ENABLE_EXPLICIT
                PmSetting.DISABLED, PmSetting.UNKNOWN -> continue
            }
            var outcomes = emptyMap<ComponentId, ComponentOutcome>()
            val error = failureOf("restore wake PM settings for ${key.first}") {
                outcomes = control(group.map { it.declaration }, goal, guard, expectedPm = PmSetting.DISABLED)
                    ?.outcomes.orEmpty().associateBy { it.key.id }
                null
            }
            group.forEach { write ->
                val id = write.declaration.id
                val outcome = outcomes[id]
                val failure = error ?: outcome?.failures?.get(ControlLayer.PACKAGE_MANAGER)?.toWakeBlockFailure(write.pathKey)
                    ?: WakeBlockFailure.SystemError(null).takeUnless { outcome?.result == ControlResult.ACHIEVED }
                if (failure != null) failures[id] = failure
            }
        }
        return failures
    }

    private suspend fun restoreRows(
        rows: List<RowChange>,
        current: Collection<WakeBlockEntity>,
        identity: PrivilegedIdentity,
        batch: BlockBatch,
    ) {
        for ((isFirewall, group) in rows.groupBy { it.written.isFirewallBlock }) {
            val saved = group.mapNotNull { it.before }
            val removed = group.filter { it.before == null }.map { it.written.pathKey }
            val failure = failureOf("restore wake block rows") {
                when {
                    !isFirewall -> dao.update(saved, removed)
                    !identity.canWriteSystemFiles -> return@failureOf WakeBlockFailure.NeedsRoot
                    else -> firewall.update(current, added = saved, removed = removed)
                }
                null
            }
            group.forEach { batch.record(it.written.pathKey, failure) }
        }
    }

    private suspend fun blockRequests(requests: List<Pair<WakePath, WakeBlockMethod>>, snapshot: BlockSnapshot?): WakeBlockOutcome {
        val byKey = requests.distinctBy { it.first.pathKey }.associateBy { it.first.pathKey }
        return applyBlocks(byKey.keys, snapshot) { key, existing ->
            val (path, method) = byKey.getValue(key)
            val blockedAt = lock.nextBlockTime(existing?.blockedAt)
            when {
                !path.canRequest(method) || (existing != null && existing.method != method.name) -> null
                method == WakeBlockMethod.FIREWALL_RULE -> buildFirewallBlock(path, blockedAt)
                else -> WakeBlockEntity(key, path.target, method.name, null, null, path.component, path.action, blockedAt)
            }
        }
    }

    override suspend fun restoreBlocks(blocks: List<WakeBlock>): WakeBlockOutcome {
        val byKey = blocks.associateBy { it.pathKey }
        return applyBlocks(byKey.keys) { key, existing ->
            val saved = byKey.getValue(key)
            val block = saved.toEntity()
            block.takeIf { saved.method in cutMethods && (existing == null || existing == block) }
        }
    }

    private suspend fun applyBlocks(
        keys: Set<String>,
        snapshot: BlockSnapshot? = null,
        resolve: suspend (String, WakeBlockEntity?) -> WakeBlockEntity?,
    ): WakeBlockOutcome {
        if (keys.isEmpty()) return WakeBlockOutcome(emptySet(), null)
        return change { identity ->
            if (identity == null) return@change WakeBlockOutcome(emptySet(), WakeBlockFailure.NoAccess)
            val current = dao.all().associateBy { it.pathKey }.toMutableMap()
            val batch = BlockBatch(keys.toList())
            val firewallBlocks = mutableListOf<WakeBlockEntity>()
            var peerFilterSupported: Boolean? = null
            for (key in keys) {
                batch.record(
                    key,
                    failureOf("block wake path $key") {
                        val existing = current[key]
                        val block = resolve(key, existing) ?: return@failureOf notBlockable(key)
                        if (!block.isFirewallBlock) return@failureOf blockComponent(block, current, snapshot)
                        if (!isValidFirewallBlock(block)) return@failureOf notBlockable(key)
                        if (!identity.canWriteSystemFiles) return@failureOf WakeBlockFailure.NeedsRoot
                        val needsSenderPackageFilter = block.sender != block.target
                        if (needsSenderPackageFilter && peerFilterSupported == null) {
                            peerFilterSupported = managedRules.supportsPeerFilter()
                        }
                        if (needsSenderPackageFilter && peerFilterSupported == false) {
                            WakeBlockFailure.SenderFilterUnsupported(listOf(key))
                        } else {
                            firewallBlocks += block
                            null
                        }
                    },
                )
            }
            if (firewallBlocks.isNotEmpty()) {
                val failure = failureOf("write user wake firewall blocks") {
                    commitChange { firewall.update(current.values, added = firewallBlocks, removed = emptyList()) }
                    null
                }
                firewallBlocks.forEach {
                    batch.record(it.pathKey, failure)
                    if (failure == null) snapshot?.rows?.add(RowChange(current[it.pathKey], it))
                }
            }
            batch.outcome()
        }
    }

    override suspend fun unblock(pathKeys: Collection<String>): WakeBlockOutcome = removeBlocks(pathKeys.toSet()).first

    override suspend fun unblockPaths(paths: List<WakePath>): WakeBlockOutcome = removeBlocks(paths.mapTo(mutableSetOf()) { it.pathKey }, paths = paths.associateBy { it.pathKey }).first

    override suspend fun removeAppBlocks(packageName: String, outboundPathKeys: Set<String>): Pair<WakeBlockOutcome, List<WakeBlock>> = removeBlocks(outboundPathKeys, target = packageName)

    private suspend fun removeBlocks(
        keys: Set<String>,
        target: String? = null,
        paths: Map<String, WakePath> = emptyMap(),
    ): Pair<WakeBlockOutcome, List<WakeBlock>> {
        if (keys.isEmpty() && target == null) return WakeBlockOutcome(emptySet(), null) to emptyList()
        fun WakeBlockEntity.selected(): Boolean = pathKey in keys || (target != null && this.target == target)
        val changed = mutableSetOf<WakeBlock>()
        val directPaths = paths.values.filter { it.blockMethod == WakeBlockMethod.COMPONENT }
        val outcome = change(hasChanges = { directPaths.isNotEmpty() || dao.all().any { it.selected() } }) { identity ->
            val current = dao.all().associateBy { it.pathKey }.toMutableMap()
            val requested = current.values.filter { it.selected() }
            val direct = directPaths.filter { it.pathKey !in current }
            if (requested.isEmpty() && direct.isEmpty()) return@change WakeBlockOutcome(emptySet(), null)
            if (identity == null) return@change WakeBlockOutcome(emptySet(), WakeBlockFailure.NoAccess)
            val batch = BlockBatch(requested.map { it.pathKey } + direct.map { it.pathKey })
            val firewallBlocks = mutableListOf<WakeBlockEntity>()
            for (block in requested) {
                if (block.isFirewallBlock && identity.canWriteSystemFiles) {
                    firewallBlocks += block
                } else {
                    val failure = if (block.isFirewallBlock) {
                        WakeBlockFailure.NeedsRoot
                    } else {
                        val writes = ComponentWrites()
                        val result = failureOf("unblock wake path ${block.pathKey}") { unblockComponent(block, current, writes) }
                        if (writes.ids.isNotEmpty()) changed += block.toModel()
                        result
                    }
                    batch.record(block.pathKey, failure)
                    if (failure == null) changed += block.toModel()
                }
            }
            if (firewallBlocks.isNotEmpty()) {
                val firewallKeys = firewallBlocks.map { it.pathKey }
                val failure = failureOf("remove user wake firewall blocks") {
                    commitChange { firewall.update(current.values, added = emptyList(), removed = firewallKeys) }
                    null
                }
                firewallKeys.forEach {
                    batch.record(it, failure)
                    if (failure == null) current.remove(it)
                }
                if (failure == null) changed += firewallBlocks.map(WakeBlockEntity::toModel)
            }
            for (path in direct) {
                batch.record(path.pathKey, failureOf("allow wake path ${path.pathKey}") { allowComponentPath(path, current.values) })
            }
            batch.outcome()
        }
        return outcome to changed.toList()
    }

    private fun buildFirewallBlock(path: WakePath, blockedAt: Long): WakeBlockEntity? {
        val component = path.component ?: return null
        val sender = if (path.trigger == ReviveTrigger.SELF_PROCESS) path.target else path.actor ?: return null
        val type = when {
            path.trigger == ReviveTrigger.PEER_ACTIVITY && sender != path.target -> ComponentType.ACTIVITY
            path.trigger == ReviveTrigger.PEER_SERVICE || path.trigger == ReviveTrigger.SELF_PROCESS || path.hold == HoldCause.BOUND_BY_PEER -> ComponentType.SERVICE
            else -> return null
        }
        val action = path.action?.takeIf(String::isNotBlank)
        return WakeBlockEntity(path.pathKey, path.target, WakeBlockMethod.FIREWALL_RULE.name, type.name, sender, component, action, blockedAt)
    }

    private suspend fun isValidFirewallBlock(block: WakeBlockEntity): Boolean {
        val rule = block.toUserWakeRule() ?: return false
        val app = AppKey(rule.target, UserId.SYSTEM)
        if (rule.sender != rule.target && !isExclusiveSender(rule.sender)) return false
        return !components.isProtectedFromBlocking(app) && declaration(app, rule.component)?.type?.name == block.componentType
    }

    private fun isExclusiveSender(sender: String): Boolean {
        val pm = context.packageManager
        val uid = try {
            pm.getApplicationInfo(sender, 0).uid
        } catch (_: NameNotFoundException) {
            return false
        }
        // The firewall matches a sender-package against every package belonging to the UID.
        return pm.getPackagesForUid(uid)?.singleOrNull() == sender
    }

    private suspend fun blockComponent(
        block: WakeBlockEntity,
        current: MutableMap<String, WakeBlockEntity>,
        snapshot: BlockSnapshot?,
    ): WakeBlockFailure? {
        val declarations = blockDeclarations(block.target, block.component, block.action)
        if (declarations.isNullOrEmpty()) return notBlockable(block.pathKey)
        return commitChange {
            val writes = snapshot?.let(::SnapshotComponentWrites) ?: ComponentWrites()
            val result = try {
                control(declarations, ControlGoal.BLOCK, writes).also { writes.ids += it?.pmWrites.orEmpty() }
            } finally {
                snapshot?.recordPmWrites(block.pathKey, declarations, writes)
            }
            val failure = result.failureFor(declarations, block.pathKey)
            if (failure == null || writes.ids.isNotEmpty()) {
                dao.upsert(listOf(block))
                snapshot?.rows?.add(RowChange(current[block.pathKey], block))
                current[block.pathKey] = block
            }
            failure
        }
    }

    private suspend fun unblockComponent(
        block: WakeBlockEntity,
        current: MutableMap<String, WakeBlockEntity>,
        writes: ComponentWrites,
    ): WakeBlockFailure? {
        val otherBlocks = current.values.filter {
            it.method == WakeBlockMethod.COMPONENT.name && it.target == block.target && it.pathKey != block.pathKey
        }
        val remaining = if (block.component != null && otherBlocks.any { it.component == block.component }) {
            emptyList()
        } else {
            val declarations = blockDeclarations(block.target, block.component, block.action)
            if (declarations == null && block.component == null) return notBlockable(block.pathKey)
            uncoveredComponents(block.target, block.component, block.action, declarations.orEmpty(), otherBlocks)
        }
        return commitChange {
            val failure = if (remaining.isEmpty()) {
                null
            } else {
                val result = control(remaining, ControlGoal.UNBLOCK, writes)
                writes.onWrite(result?.pmWrites.orEmpty())
                result.failureFor(remaining, block.pathKey)
            }
            if (failure == null) {
                dao.delete(listOf(block.pathKey))
                current.remove(block.pathKey)
            }
            failure
        }
    }

    private suspend fun allowComponentPath(path: WakePath, current: Collection<WakeBlockEntity>): WakeBlockFailure? {
        val declarations = blockDeclarations(path.target, path.component, path.action)
        if (declarations.isNullOrEmpty()) return notBlockable(path.pathKey)
        val app = AppKey(path.target, UserId.SYSTEM)
        val owned = automatic.ownedComponents.first()
        val statuses = components.components(app).first().associateBy { it.declaration.id }
        val otherBlocks = current.filter { it.method == WakeBlockMethod.COMPONENT.name && it.target == path.target }
        val uncovered = uncoveredComponents(path.target, path.component, path.action, declarations, otherBlocks)
        val byLayer = uncovered.groupBy { declaration ->
            val state = statuses[declaration.id]?.state
            buildSet {
                if (state?.ifw == IfwBlock.BLOCKED) add(ControlLayer.INTENT_FIREWALL)
                val pm = state?.pm?.takeUnless { it == PmSetting.UNKNOWN } ?: PmSetting.DEFAULT
                if (declaration.id !in owned && !declaration.isPmClear(pm)) add(ControlLayer.PACKAGE_MANAGER)
            }
        }.filterKeys { it.isNotEmpty() }
        return byLayer.entries.mapNotNull { (layers, group) ->
            commitChange {
                control(group, ControlGoal.ENABLE, layers = layers).failureFor(group, path.pathKey, layers)
            }
        }.firstOrNull()
    }

    private suspend fun blockDeclarations(target: String, component: String?, action: String?): List<ComponentDeclaration>? = when {
        component != null -> declaration(AppKey(target, UserId.SYSTEM), component)?.let(::listOf)
        !action.isNullOrBlank() -> receivers.resolve(target, action)
        else -> null
    }

    private fun uncoveredComponents(
        target: String,
        component: String?,
        action: String?,
        declarations: List<ComponentDeclaration>,
        otherBlocks: List<WakeBlockEntity>,
    ): List<ComponentDeclaration> {
        val named = otherBlocks.mapNotNull { it.component }.toSet()
        val candidates = declarations.filterNot { it.id.className in named }
        if (candidates.none { it.type == ComponentType.RECEIVER }) return candidates
        val actions = otherBlocks.filter { it.component == null }.mapNotNull { it.action }.toSet()
        if (component == null && action in actions) return emptyList()
        val coveredReceivers = actions.flatMapTo(mutableSetOf()) { action -> receivers.resolve(target, action).map { it.id } }
        return candidates.filterNot { it.id in coveredReceivers }
    }

    private suspend fun declaration(app: AppKey, name: String): ComponentDeclaration? {
        val refresh = components.refreshIfReadable(app, FirewallRead.KEEP_CACHED)
        if (refresh is ReadResult.Failure) {
            when (val reason = refresh.reason) {
                is ReadFailure.PackageNotFound -> return null

                is ReadFailure.ManifestUnreadable, is ReadFailure.UserNotReachable -> error("Cannot read wake component: $reason")

                // A settings read can fail after the declarations have already been stored.
                else -> Timber.w("Cannot fully refresh wake component %s: %s", app, reason)
            }
        }
        val id = ComponentId(app.packageName, name)
        val declaration = components.components(app).first().firstOrNull { it.declaration.id == id }?.declaration
        check(declaration != null || refresh !is ReadResult.Failure) { "Cannot confirm whether $id is declared: $refresh" }
        return declaration
    }

    private suspend fun control(
        declarations: List<ComponentDeclaration>,
        goal: ControlGoal,
        writeObserver: ComponentWriteObserver? = null,
        layers: Set<ControlLayer> = setOf(ControlLayer.PACKAGE_MANAGER),
        expectedPm: PmSetting? = null,
    ): ControlEvent.PackageDone? {
        val request = ControlRequest(UserId.SYSTEM, declarations, goal, layers, expectedPm)
        return components.control(request, keepKnownState = false, writeObserver).filterIsInstance<ControlEvent.PackageDone>().singleOrNull()
    }

    /** Acquires permission before serializing writes and checks the channel again inside the lock. */
    private suspend fun change(
        hasChanges: suspend () -> Boolean = { true },
        action: suspend (PrivilegedIdentity?) -> WakeBlockOutcome,
    ): WakeBlockOutcome = withContext(io) {
        try {
            if (!hasChanges()) return@withContext WakeBlockOutcome(emptySet(), null)
            val connected = currentUserId() == UserId.SYSTEM && channel.connect() is ChannelState.Ready
            lock.withLock {
                val identity = if (connected) (channel.state.value as? ChannelState.Ready)?.identity else null
                withoutPermissionPrompts { action(identity) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cannot change wake blocks")
            WakeBlockOutcome(emptySet(), WakeBlockFailure.SystemError(e.message))
        }
    }
}

private fun ControlEvent.PackageDone?.failureFor(
    declarations: List<ComponentDeclaration>,
    pathKey: String,
    layers: Set<ControlLayer> = setOf(ControlLayer.PACKAGE_MANAGER),
): WakeBlockFailure? {
    val outcomes = this?.outcomes?.associateBy { it.key } ?: return WakeBlockFailure.SystemError(null)
    return declarations.firstNotNullOfOrNull { declaration ->
        val outcome = outcomes[ComponentKey(declaration.id, UserId.SYSTEM)] ?: return WakeBlockFailure.SystemError(null)
        val failure = layers.firstNotNullOfOrNull(outcome.failures::get)
        failure?.toWakeBlockFailure(pathKey) ?: if (outcome.result == ControlResult.ACHIEVED) null else WakeBlockFailure.SystemError(null)
    }
}

private fun ControlFailure.toWakeBlockFailure(pathKey: String): WakeBlockFailure = when (val failure = this) {
    ControlFailure.ChannelUnavailable -> WakeBlockFailure.NoAccess
    is ControlFailure.NotPermitted -> if (failure.identity == PrivilegedIdentity.SHELL) WakeBlockFailure.NeedsRoot else WakeBlockFailure.SystemError(failure.message)
    ControlFailure.ProtectedComponent, ControlFailure.FirewallProtectedComponent, is ControlFailure.TargetNotFound -> notBlockable(pathKey)
    is ControlFailure.WriteFailed -> WakeBlockFailure.SystemError(failure.message)
    is ControlFailure.SystemError -> WakeBlockFailure.SystemError(failure.message)
    ControlFailure.RulesUnreadable, ControlFailure.StateNotConfirmed -> WakeBlockFailure.SystemError(null)
}

private fun WakePath.canRequest(method: WakeBlockMethod): Boolean = blockMethod in cutMethods && method in cutMethods

private val cutMethods = setOf(WakeBlockMethod.FIREWALL_RULE, WakeBlockMethod.COMPONENT)

private fun notBlockable(pathKey: String) = WakeBlockFailure.NotBlockable(listOf(pathKey))

private fun WakeBlockEntity.toModel() = WakeBlock(
    pathKey,
    target,
    WakeBlockMethod.valueOf(method),
    componentType?.let(ComponentType::valueOf),
    sender,
    component,
    action,
    blockedAt,
)

private fun WakeBlock.toEntity() = WakeBlockEntity(
    pathKey,
    target,
    method.name,
    componentType?.name,
    sender,
    component,
    action,
    blockedAtMillis,
)

/** Once a system write starts, finish storing its result even if the request is cancelled. */
private suspend fun <T> commitChange(action: suspend () -> T): T {
    currentCoroutineContext().ensureActive()
    return withContext(NonCancellable) { action() }
}

private suspend fun failureOf(operation: String, action: suspend () -> WakeBlockFailure?): WakeBlockFailure? = try {
    action()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Timber.w(e, "Cannot %s", operation)
    WakeBlockFailure.SystemError(e.message)
}

private data class RowChange(val before: WakeBlockEntity?, val written: WakeBlockEntity)

private data class PmChange(val pathKey: String, val declaration: ComponentDeclaration, val before: PmSetting)

private class BlockSnapshot : WakeBlockRepository.Snapshot {
    val rows = mutableListOf<RowChange>()
    val pm = mutableMapOf<ComponentId, PmChange>()
    val claims = mutableSetOf<PmBlockRecordEntity>()
    val priorClaims = mutableMapOf<ComponentId, PmBlockRecordEntity>()
    val pathComponents = mutableMapOf<String, Set<ComponentId>>()

    override val hasChanges get() = rows.isNotEmpty() || pm.isNotEmpty() || claims.isNotEmpty()

    fun recordPmWrites(pathKey: String, declarations: List<ComponentDeclaration>, writes: ComponentWrites) {
        pathComponents[pathKey] = declarations.mapTo(mutableSetOf()) { it.id }
        for (declaration in declarations) {
            val before = writes.before[declaration.id]
            if (declaration.id in writes.ids && before != null && before != PmSetting.UNKNOWN) {
                pm.putIfAbsent(declaration.id, PmChange(pathKey, declaration, before))
            }
        }
    }
}

private val PmBlockRecordEntity.componentId get() = ComponentId(packageName, className)

private open class ComponentWrites : ComponentWriteObserver {
    val ids = mutableSetOf<ComponentId>()
    var before = emptyMap<ComponentId, PmSetting>()
        private set

    override fun onPmRead(settings: Map<ComponentId, PmSetting>) {
        before = settings
    }

    override fun canStart() = true

    override fun onWrite(components: Collection<ComponentId>) {
        ids += components
    }
}

private class SnapshotComponentWrites(private val snapshot: BlockSnapshot) :
    ComponentWrites(),
    PmBlockClaimObserver {
    override fun onPmClaimsBefore(records: List<PmBlockRecordEntity>) {
        records.forEach { snapshot.priorClaims.putIfAbsent(it.componentId, it) }
    }

    override fun onPmClaimsAdded(records: List<PmBlockRecordEntity>) {
        snapshot.claims.addAll(records)
    }
}

/** Reports the first failure by request order, even when the firewall writes are batched. */
private class BlockBatch(private val order: List<String>) {
    private val results = mutableMapOf<String, WakeBlockFailure?>()

    fun record(key: String, failure: WakeBlockFailure?) {
        results[key] = results[key] ?: failure
    }

    fun outcome(): WakeBlockOutcome {
        val keys = order.distinct()
        val failure = keys.firstNotNullOfOrNull(results::get)
        return WakeBlockOutcome(
            keys.filterTo(mutableSetOf()) { it in results && results[it] == null },
            when (failure) {
                is WakeBlockFailure.NotBlockable -> WakeBlockFailure.NotBlockable(keys.filter { results[it] is WakeBlockFailure.NotBlockable })
                is WakeBlockFailure.SenderFilterUnsupported -> WakeBlockFailure.SenderFilterUnsupported(keys.filter { results[it] is WakeBlockFailure.SenderFilterUnsupported })
                else -> failure
            },
        )
    }
}
