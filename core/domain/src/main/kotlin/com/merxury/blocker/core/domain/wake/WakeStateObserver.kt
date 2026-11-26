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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class WakeStateObserver @Inject constructor(
    private val user: WakeBlockRepository,
    private val automatic: AutomaticWakeBlockRepository,
    private val components: ComponentStatusRepository,
    private val receivers: WakeReceiverResolver,
    @Dispatcher(IO) private val io: CoroutineDispatcher,
) {
    /** Resolves component paths for the supplied activities, then observes their block states. */
    suspend fun observe(activities: List<AppWakeActivity>): Flow<List<AppWakeState>> {
        if (activities.isEmpty()) return flowOf(emptyList())
        val paths = activities.map { activity -> activity.allPaths().filter(WakePath::isBlockable).distinctBy(WakePath::pathKey).toList() }
        val componentIds = withContext(io) { resolveComponentIds(paths.flatten()) }
        return combine(
            user.blocks.map { it.associateBy { block -> block.pathKey to block.method } },
            automatic.blocks,
            automatic.ownedComponents,
            observeComponents(componentIds.values),
        ) { blocks, automaticBlocks, owned, components ->
            val byApp = automaticBlocks.filter { it.active || it.allowed }.groupByApp()
            activities.zip(paths) { activity, blockable ->
                state(activity, blockable, blocks, byApp[activity.packageName].orEmpty()) { path ->
                    isUserComponentBlock(componentIds.getValue(path), components, owned)
                }
            }
        }.distinctUntilChanged().flowOn(io)
    }

    private fun resolveComponentIds(paths: List<WakePath>): Map<WakePath, List<ComponentId>> {
        val byAction = mutableMapOf<Pair<String, String>, List<ComponentId>>()
        return paths.asSequence().filter { it.blockMethod == WakeBlockMethod.COMPONENT }.distinct().associateWith { path ->
            val component = path.component
            val action = path.action
            when {
                component != null -> listOf(ComponentId(path.target, component))

                !action.isNullOrBlank() -> byAction.getOrPut(path.target to action) {
                    receivers.resolve(path.target, action).map { it.id }
                }

                else -> emptyList()
            }
        }
    }

    private fun observeComponents(ids: Collection<List<ComponentId>>): Flow<Map<ComponentId, ComponentStatus>> {
        val statuses = ids.flatten().groupBy { it.packageName }.map { (target, targetIds) ->
            val needed = targetIds.toSet()
            components.components(AppKey(target, UserId.SYSTEM)).map { components ->
                components.filter { it.declaration.id in needed }.associateBy { it.declaration.id }
            }.distinctUntilChanged()
        }
        if (statuses.isEmpty()) return flowOf(emptyMap())
        return combine(statuses) { byTarget -> buildMap { byTarget.forEach { putAll(it) } } }
    }

    private fun state(
        activity: AppWakeActivity,
        blockable: List<WakePath>,
        blocks: Map<Pair<String, WakeBlockMethod>, WakeBlock>,
        automatic: List<AutomaticWakeBlock>,
        isUserComponentBlock: (WakePath) -> Boolean,
    ): AppWakeState {
        val protections = AutomaticWakeBlockIndex(automatic)
        val matches = activity.allPaths().associateWith(protections::matching)
        val paths = blockable.associate { path ->
            val saved = blocks[path.pathKey to path.blockMethod]
            val userBlocked = when (path.blockMethod) {
                WakeBlockMethod.FIREWALL_RULE -> saved != null
                WakeBlockMethod.COMPONENT -> isUserComponentBlock(path)
                else -> false
            }
            val block = when {
                userBlocked -> WakePathBlock.USER
                matches.getValue(path).any { it.active } -> WakePathBlock.AUTOMATIC
                else -> WakePathBlock.NONE
            }
            val blockedAt = saved?.blockedAtMillis?.takeIf { userBlocked }
            path.pathKey to WakePathState(path, block, blockedAt, blockedAt != null && path.lastSeenMillis > blockedAt)
        }
        val matchedIds = matches.values.asSequence().flatten().mapTo(mutableSetOf()) { it.pathId }
        val unmatched = automatic.filterNot { it.pathId in matchedIds }
        val allBlocked = (paths.isNotEmpty() || unmatched.isNotEmpty()) &&
            paths.values.all { it.block != WakePathBlock.NONE } && unmatched.all { it.active }
        return AppWakeState(activity, paths, unmatched, allBlocked)
    }

    private fun isUserComponentBlock(
        ids: List<ComponentId>,
        components: Map<ComponentId, ComponentStatus>,
        owned: Set<ComponentId>,
    ): Boolean = ids.isNotEmpty() && ids.all { id ->
        val status = components[id]
        status?.isSwitchOn == false && (id !in owned || status.state.ifw == IfwBlock.BLOCKED)
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
