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
    private val channel: PrivilegedChannel,
    private val lock: WakeRuleLock,
    @Dispatcher(IO) private val io: CoroutineDispatcher,
) : WakeBlockRepository {
    override val blocks = dao.observeAll().map { rows -> rows.map(WakeBlockEntity::toModel) }

    override suspend fun block(requests: List<Pair<WakePath, WakeBlockMethod>>): WakeBlockOutcome {
        if (requests.isEmpty()) return WakeBlockOutcome(emptySet(), null)
        return change {
            val identity = connect() ?: return@change WakeBlockOutcome(emptySet(), WakeBlockFailure.NoAccess)
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
        return change {
            val current = dao.all().associateBy { it.pathKey }.toMutableMap()
            val requested = pathKeys.distinct().mapNotNull(current::get)
            if (requested.isEmpty()) return@change WakeBlockOutcome(emptySet(), null)
            val identity = connect() ?: return@change WakeBlockOutcome(emptySet(), WakeBlockFailure.NoAccess)
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
        val name = path.component ?: return notBlockable(path.pathKey)
        val declaration = declaration(AppKey(path.target, UserId.SYSTEM), name) ?: return notBlockable(path.pathKey)
        return commitChange {
            val failure = control(declaration, ControlGoal.BLOCK, path.pathKey)
            if (failure == null) {
                val block = WakeBlockEntity(path.pathKey, path.target, WakeBlockMethod.COMPONENT.name, null, null, name, path.action, System.currentTimeMillis())
                dao.upsert(listOf(block))
                current[path.pathKey] = block
            }
            failure
        }
    }

    private suspend fun unblockComponent(block: WakeBlockEntity, current: MutableMap<String, WakeBlockEntity>): WakeBlockFailure? {
        val stillBlocked = current.values.any {
            !it.isFirewallBlock && it.target == block.target && it.component == block.component && it.pathKey != block.pathKey
        }
        val declaration = if (stillBlocked) {
            null
        } else {
            declaration(AppKey(block.target, UserId.SYSTEM), requireNotNull(block.component)) ?: return notBlockable(block.pathKey)
        }
        return commitChange {
            val failure = declaration?.let { control(it, ControlGoal.UNBLOCK, block.pathKey) }
            if (failure == null) {
                dao.delete(listOf(block.pathKey))
                current.remove(block.pathKey)
            }
            failure
        }
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
        return components.components(app).first().firstOrNull { it.declaration.id == id }?.declaration
    }

    private suspend fun control(declaration: ComponentDeclaration, goal: ControlGoal, pathKey: String): WakeBlockFailure? {
        val key = ComponentKey(declaration.id, UserId.SYSTEM)
        val request = ControlRequest(key.userId, listOf(declaration), goal, setOf(ControlLayer.PACKAGE_MANAGER))
        val result = components.control(request).filterIsInstance<ControlEvent.PackageDone>().singleOrNull()
            ?.outcomes?.singleOrNull { it.key == key }
            ?: return WakeBlockFailure.SystemError(null)
        return when (val failure = result.failures[ControlLayer.PACKAGE_MANAGER]) {
            null -> if (result.result == ControlResult.ACHIEVED) null else WakeBlockFailure.SystemError(null)
            ControlFailure.ChannelUnavailable -> WakeBlockFailure.NoAccess
            is ControlFailure.NotPermitted -> if (failure.identity == PrivilegedIdentity.SHELL) WakeBlockFailure.NeedsRoot else WakeBlockFailure.SystemError(failure.message)
            ControlFailure.ProtectedComponent, ControlFailure.FirewallProtectedComponent, is ControlFailure.TargetNotFound -> notBlockable(pathKey)
            is ControlFailure.WriteFailed -> WakeBlockFailure.SystemError(failure.message)
            is ControlFailure.SystemError -> WakeBlockFailure.SystemError(failure.message)
            ControlFailure.RulesUnreadable, ControlFailure.StateNotConfirmed -> WakeBlockFailure.SystemError(null)
        }
    }

    private suspend fun connect(): PrivilegedIdentity? = if (currentUserId() == UserId.SYSTEM) {
        (channel.connect() as? ChannelState.Ready)?.identity
    } else {
        null
    }

    private suspend fun change(action: suspend () -> WakeBlockOutcome): WakeBlockOutcome = withContext(io) {
        try {
            lock.withLock(action)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cannot change wake blocks")
            WakeBlockOutcome(emptySet(), WakeBlockFailure.SystemError(e.message))
        }
    }
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
