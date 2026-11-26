// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.wake.AutomaticWakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeReceiverResolver
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.wake.AppWakeActivity
import com.merxury.blocker.core.model.wake.AppWakeState
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakePathState
import com.merxury.blocker.core.model.wake.qualifiedWakeComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class WakeStateObserver @Inject constructor(
    private val user: WakeBlockRepository,
    private val automatic: AutomaticWakeBlockRepository,
    private val components: ComponentStatusRepository,
    private val receivers: WakeReceiverResolver,
    @Dispatcher(IO) private val io: CoroutineDispatcher,
) {
    fun observe(activities: List<AppWakeActivity>): Flow<List<AppWakeState>> {
        if (activities.isEmpty()) return flowOf(emptyList())
        val paths = activities.map { activity -> activity.allPaths().filter(WakePath::isBlockable).distinctBy(WakePath::pathKey).toList() }
        val targets = paths.flatten().filter { it.blockMethod == WakeBlockMethod.COMPONENT }.map { it.target }.distinct()
        val statuses = targets.map { target -> components.components(AppKey(target, UserId.SYSTEM)) }
        val componentsById = if (statuses.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(statuses) { lists -> lists.flatMap { it }.associateBy { it.declaration.id } }
        }
        return combine(
            user.blocks.map { it.associateBy { block -> block.pathKey to block.method } },
            automatic.blocks,
            automatic.ownedComponents,
            componentsById,
        ) { blocks, automaticBlocks, owned, components ->
            val byApp = automaticBlocks.groupByApp()
            activities.zip(paths) { activity, blockable ->
                state(activity, blockable, blocks, byApp[activity.packageName].orEmpty(), owned, components)
            }
        }.distinctUntilChanged().flowOn(io)
    }

    private fun state(
        activity: AppWakeActivity,
        blockable: List<WakePath>,
        blocks: Map<Pair<String, WakeBlockMethod>, WakeBlock>,
        automatic: List<AutomaticWakeBlock>,
        owned: Set<ComponentId>,
        components: Map<ComponentId, ComponentStatus>,
    ): AppWakeState {
        val protections = automatic.groupBy { it.identity }
        val paths = blockable.associate { path ->
            val saved = blocks[path.pathKey to path.blockMethod]
            val userBlocked = when (path.blockMethod) {
                WakeBlockMethod.FIREWALL_RULE -> saved != null
                WakeBlockMethod.COMPONENT -> isUserComponentBlock(path, components, owned)
                else -> false
            }
            val block = when {
                userBlocked -> WakePathBlock.USER
                protections[path.identity].orEmpty().any { it.active } -> WakePathBlock.AUTOMATIC
                else -> WakePathBlock.NONE
            }
            val blockedAt = saved?.blockedAtMillis?.takeIf { userBlocked }
            path.pathKey to WakePathState(path, block, blockedAt, blockedAt != null && path.lastSeenMillis > blockedAt)
        }
        val identities = activity.allPaths().mapTo(mutableSetOf()) { it.identity }
        val unmatched = automatic.filterNot { it.identity in identities }
        val allBlocked = (paths.isNotEmpty() || unmatched.isNotEmpty()) &&
            paths.values.all { it.block != WakePathBlock.NONE } && unmatched.all { it.active }
        return AppWakeState(activity, paths, unmatched, allBlocked)
    }

    private fun isUserComponentBlock(
        path: WakePath,
        components: Map<ComponentId, ComponentStatus>,
        owned: Set<ComponentId>,
    ): Boolean {
        val component = path.component
        val action = path.action
        val ids = when {
            component != null -> listOf(ComponentId(path.target, component))
            !action.isNullOrBlank() -> receivers.resolve(path.target, action).map { it.id }
            else -> emptyList()
        }
        return ids.isNotEmpty() && ids.all { id ->
            val status = components[id]
            status?.isSwitchOn == false && (id !in owned || status.state.ifw == IfwBlock.BLOCKED)
        }
    }
}

private fun List<AutomaticWakeBlock>.groupByApp(): Map<String, List<AutomaticWakeBlock>> = buildMap<String, MutableList<AutomaticWakeBlock>> {
    for (block in this@groupByApp) {
        getOrPut(block.target, ::mutableListOf) += block
        if (block.caller != block.target) getOrPut(block.caller, ::mutableListOf) += block
    }
}

internal fun AppWakeActivity.outboundPaths(): Sequence<WakePath> = peers.asSequence().flatMap { it.outbound }
private fun AppWakeActivity.allPaths(): Sequence<WakePath> = outboundPaths() + peers.asSequence().flatMap { it.inbound } + own.asSequence().flatMap { it.paths }

private val WakePath.isBlockable get() = blockMethod == WakeBlockMethod.FIREWALL_RULE || blockMethod == WakeBlockMethod.COMPONENT

internal data class WakePathIdentity(val caller: String?, val target: String, val component: String?, val action: String)

internal val WakePath.identity get() = WakePathIdentity(actor, target, qualifiedWakeComponent(target, component), action.orEmpty())
internal val AutomaticWakeBlock.identity get() = WakePathIdentity(caller, target, qualifiedWakeComponent(target, component), action)
