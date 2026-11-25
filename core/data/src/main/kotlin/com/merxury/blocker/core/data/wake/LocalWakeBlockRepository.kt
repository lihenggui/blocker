// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.FirewallRead
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
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ComponentWriteObserver
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
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
import com.merxury.core.ifw.isWakePackageName
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
) : WakeBlockRepository {
    override val blocks = dao.observeAll().map { rows -> rows.map(WakeBlockEntity::toModel) }

    override suspend fun block(requests: List<Pair<WakePath, WakeBlockMethod>>): WakeBlockOutcome {
        if (requests.isEmpty()) return WakeBlockOutcome(emptySet(), null)
        return change { identity ->
            if (identity == null) return@change WakeBlockOutcome(emptySet(), WakeBlockFailure.NoAccess)
            val current = dao.all().associateBy { it.pathKey }.toMutableMap()
            val batch = BlockBatch(requests.map { it.first.pathKey })
            val firewallBlocks = mutableListOf<WakeBlockEntity>()
            var peerFilterSupported: Boolean? = null
            for ((path, method) in requests.distinctBy { it.first.pathKey }) {
                val key = path.pathKey
                batch.record(
                    key,
                    failureOf("block wake path $key") {
                        val existing = current[key]
                        when {
                            !path.canRequest(method) || (existing != null && existing.method != method.name) -> notBlockable(key)

                            method == WakeBlockMethod.COMPONENT -> blockComponent(path, current)

                            else -> {
                                val block = buildFirewallBlock(path) ?: return@failureOf notBlockable(key)
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
                            }
                        }
                    },
                )
            }
            if (firewallBlocks.isNotEmpty()) {
                val failure = failureOf("write user wake firewall blocks") {
                    commitChange { firewall.update(current.values, added = firewallBlocks, removed = emptyList()) }
                    null
                }
                firewallBlocks.forEach { batch.record(it.pathKey, failure) }
            }
            batch.outcome()
        }
    }

    override suspend fun unblock(pathKeys: Collection<String>): WakeBlockOutcome {
        if (pathKeys.isEmpty()) return WakeBlockOutcome(emptySet(), null)
        val keys = pathKeys.toSet()
        return change(hasChanges = { dao.all().any { it.pathKey in keys } }) { identity ->
            val current = dao.all().associateBy { it.pathKey }.toMutableMap()
            val requested = keys.mapNotNull(current::get)
            if (requested.isEmpty()) return@change WakeBlockOutcome(emptySet(), null)
            if (identity == null) return@change WakeBlockOutcome(emptySet(), WakeBlockFailure.NoAccess)
            val batch = BlockBatch(requested.map { it.pathKey })
            val firewallKeys = mutableListOf<String>()
            for (block in requested) {
                if (block.isFirewallBlock && identity.canWriteSystemFiles) {
                    firewallKeys += block.pathKey
                } else {
                    val failure = if (block.isFirewallBlock) {
                        WakeBlockFailure.NeedsRoot
                    } else {
                        failureOf("unblock wake path ${block.pathKey}") { unblockComponent(block, current) }
                    }
                    batch.record(block.pathKey, failure)
                }
            }
            if (firewallKeys.isNotEmpty()) {
                val failure = failureOf("remove user wake firewall blocks") {
                    commitChange { firewall.update(current.values, added = emptyList(), removed = firewallKeys) }
                    null
                }
                firewallKeys.forEach { batch.record(it, failure) }
            }
            batch.outcome()
        }
    }

    private suspend fun buildFirewallBlock(path: WakePath): WakeBlockEntity? {
        val component = path.component ?: return null
        val app = AppKey(path.target, UserId.SYSTEM)
        if (!isWakePackageName(path.target) || components.isProtectedFromBlocking(app)) return null
        val sender = when {
            path.trigger == ReviveTrigger.SELF_PROCESS -> path.target

            path.trigger == ReviveTrigger.PEER_SERVICE || path.hold == HoldCause.BOUND_BY_PEER ->
                path.actor?.takeIf(::isWakePackageName) ?: return null

            else -> return null
        }
        if (declaration(app, component)?.type != ComponentType.SERVICE) return null
        val action = path.action?.takeIf(String::isNotBlank)
        return WakeBlockEntity(path.pathKey, path.target, WakeBlockMethod.FIREWALL_RULE.name, ComponentType.SERVICE.name, sender, component, action, System.currentTimeMillis())
    }

    private suspend fun blockComponent(path: WakePath, current: MutableMap<String, WakeBlockEntity>): WakeBlockFailure? {
        val declarations = blockDeclarations(path.target, path.component, path.action)
        if (declarations.isNullOrEmpty()) return notBlockable(path.pathKey)
        return commitChange {
            val acknowledged = mutableSetOf<ComponentId>()
            val observer = object : ComponentWriteObserver {
                override fun canStart() = true

                override fun onWrite(components: Collection<ComponentId>) {
                    acknowledged += components
                }
            }
            val result = control(declarations, ControlGoal.BLOCK, observer)
            val failure = result.failureFor(declarations, path.pathKey)
            val written = acknowledged + result?.pmWrites.orEmpty()
            if (failure == null || declarations.any { it.id in written }) {
                val block = WakeBlockEntity(path.pathKey, path.target, WakeBlockMethod.COMPONENT.name, null, null, path.component, path.action, System.currentTimeMillis())
                dao.upsert(listOf(block))
                current[path.pathKey] = block
            }
            failure
        }
    }

    private suspend fun unblockComponent(block: WakeBlockEntity, current: MutableMap<String, WakeBlockEntity>): WakeBlockFailure? {
        val otherBlocks = current.values.filter {
            it.method == WakeBlockMethod.COMPONENT.name && it.target == block.target && it.pathKey != block.pathKey
        }
        val remaining = if (block.component != null && otherBlocks.any { it.component == block.component }) {
            emptyList()
        } else {
            val declarations = blockDeclarations(block.target, block.component, block.action)
            if (declarations == null && block.component == null) return notBlockable(block.pathKey)
            uncoveredComponents(block, declarations.orEmpty(), otherBlocks)
        }
        return commitChange {
            val failure = if (remaining.isEmpty()) null else control(remaining, ControlGoal.UNBLOCK).failureFor(remaining, block.pathKey)
            if (failure == null) {
                dao.delete(listOf(block.pathKey))
                current.remove(block.pathKey)
            }
            failure
        }
    }

    private suspend fun blockDeclarations(target: String, component: String?, action: String?): List<ComponentDeclaration>? = when {
        component != null -> declaration(AppKey(target, UserId.SYSTEM), component)?.let(::listOf)
        !action.isNullOrBlank() -> receivers.resolve(target, action)
        else -> null
    }

    private fun uncoveredComponents(
        block: WakeBlockEntity,
        declarations: List<ComponentDeclaration>,
        otherBlocks: List<WakeBlockEntity>,
    ): List<ComponentDeclaration> {
        val named = otherBlocks.mapNotNull { it.component }.toSet()
        val candidates = declarations.filterNot { it.id.className in named }
        if (candidates.none { it.type == ComponentType.RECEIVER }) return candidates
        val actions = otherBlocks.filter { it.component == null }.mapNotNull { it.action }.toSet()
        if (block.component == null && block.action in actions) return emptyList()
        val coveredReceivers = actions.flatMapTo(mutableSetOf()) { action -> receivers.resolve(block.target, action).map { it.id } }
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
    ): ControlEvent.PackageDone? {
        val request = ControlRequest(UserId.SYSTEM, declarations, goal, setOf(ControlLayer.PACKAGE_MANAGER))
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

private fun ControlEvent.PackageDone?.failureFor(declarations: List<ComponentDeclaration>, pathKey: String): WakeBlockFailure? {
    val outcomes = this?.outcomes?.associateBy { it.key } ?: return WakeBlockFailure.SystemError(null)
    return declarations.firstNotNullOfOrNull { declaration ->
        val outcome = outcomes[ComponentKey(declaration.id, UserId.SYSTEM)] ?: return WakeBlockFailure.SystemError(null)
        outcome.failureFor(pathKey)
    }
}

private fun ComponentOutcome.failureFor(pathKey: String): WakeBlockFailure? = when (val failure = failures[ControlLayer.PACKAGE_MANAGER]) {
    null -> if (result == ControlResult.ACHIEVED) null else WakeBlockFailure.SystemError(null)
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

/** Reports the first failure by request order, even when the firewall writes are batched. */
private class BlockBatch(private val order: List<String>) {
    private val results = mutableMapOf<String, WakeBlockFailure?>()

    fun record(key: String, failure: WakeBlockFailure?) {
        results[key] = failure
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
