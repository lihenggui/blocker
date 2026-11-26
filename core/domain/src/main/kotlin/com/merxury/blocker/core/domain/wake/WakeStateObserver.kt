// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.device.BackgroundUsageRepository
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.wake.AutomaticWakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeReceiverResolver
import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.database.wake.WakeRecord
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.model.wake.AppWakeActivity
import com.merxury.blocker.core.model.wake.AppWakeState
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeAppEnvironment
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakePathState
import com.merxury.blocker.core.model.wake.WakeSpecialAccess
import com.merxury.blocker.core.privileged.wake.WAKE_DEVICE_ADMIN
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
    private val backgroundUsage: BackgroundUsageRepository,
    private val receivers: WakeReceiverResolver,
    private val history: WakeRepository,
    @Dispatcher(IO) private val io: CoroutineDispatcher,
) {
    /** Resolves component paths for the supplied activities, then observes all available path blocks. */
    suspend fun observe(activities: List<AppWakeActivity>): Flow<List<AppWakeState>> {
        if (activities.isEmpty()) return flowOf(emptyList())
        val paths = activities.map { activity -> activity.allPaths().filter(WakePath::isBlockable).distinctBy(WakePath::pathKey).toList() }
        val allPaths = paths.flatten()
        val componentIds = withContext(io) { resolveComponentIds(allPaths) }
        val backgroundTargets = allPaths.filter { it.blockMethod == WakeBlockMethod.BACKGROUND_RESTRICTION }.map { it.target }.distinct()
        return combine(
            user.blocks.map { it.associateBy { block -> block.pathKey to block.method } },
            automatic.blocks,
            automatic.ownedComponents,
            observeComponents(componentIds.values),
            observeBackgroundUsage(backgroundTargets),
        ) { blocks, automaticBlocks, owned, components, backgroundStates ->
            val byApp = automaticBlocks.filter { it.active || it.allowed }.groupByApp()
            activities.zip(paths) { activity, blockable ->
                state(activity, blockable, blocks, byApp[activity.packageName].orEmpty(), backgroundStates, owned) { path ->
                    componentIds.getValue(path).map(components::get)
                }
            }
        }.distinctUntilChanged().flowOn(io)
    }

    fun observeEnvironment(packageName: String): Flow<WakeAppEnvironment> {
        val targets = listOf(packageName)
        return combine(history.observeExemptions(targets), history.observeSpecialBindings(targets)) { exemptions, bindings ->
            val exemption = exemptions.singleOrNull()
            val types = exemption?.idleWhitelist?.split(',').orEmpty()
            WakeAppEnvironment(
                userIdleAllowlisted = "user" in types,
                systemIdleAllowlisted = "system" in types || "system-excidle" in types,
                standbyBucket = exemption?.standbyBucket,
                specialAccess = bindings.mapNotNullTo(mutableSetOf()) { it.specialAccess() },
            )
        }.distinctUntilChanged().flowOn(io)
    }

    private fun WakeRecord.specialAccess(): WakeSpecialAccess? = when (kind) {
        "SystemBinding" -> when (action) {
            "ACCESSIBILITY" -> WakeSpecialAccess.ACCESSIBILITY
            "LISTENERS" -> WakeSpecialAccess.NOTIFICATION_LISTENER
            "VPN" -> WakeSpecialAccess.ALWAYS_ON_VPN
            "INPUT" -> WakeSpecialAccess.INPUT_METHOD
            else -> null
        }

        "WallpaperBinding" -> WakeSpecialAccess.LIVE_WALLPAPER

        "CompanionAssociation" -> WakeSpecialAccess.COMPANION_DEVICE

        WAKE_DEVICE_ADMIN -> WakeSpecialAccess.DEVICE_ADMIN

        "WidgetProvider" -> WakeSpecialAccess.WIDGET

        "SyncAdapter" -> WakeSpecialAccess.SYNC_ADAPTER

        else -> null
    }

    private fun resolveComponentIds(paths: List<WakePath>): Map<WakePath, List<ComponentId>> {
        val byAction = mutableMapOf<Pair<String, String>, List<ComponentId>>()
        return paths.asSequence().filter { it.blockMethod == WakeBlockMethod.COMPONENT || it.supportsLegacyComponentBlock }.distinct().associateWith { path ->
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

    private fun observeBackgroundUsage(targets: List<String>): Flow<Map<String, BackgroundUsage?>> {
        if (targets.isEmpty()) return flowOf(emptyMap())
        return combine(targets.map { backgroundUsage.observe(AppKey(it, UserId.SYSTEM)) }) { targets.zip(it.asList()).toMap() }
    }

    private fun state(
        activity: AppWakeActivity,
        blockable: List<WakePath>,
        blocks: Map<Pair<String, WakeBlockMethod>, WakeBlock>,
        automatic: List<AutomaticWakeBlock>,
        backgroundStates: Map<String, BackgroundUsage?>,
        owned: Set<ComponentId>,
        componentStatuses: (WakePath) -> List<ComponentStatus?>,
    ): AppWakeState {
        val protections = AutomaticWakeBlockIndex(automatic)
        val matches = activity.allPaths().filter { it.blockMethod != WakeBlockMethod.BACKGROUND_RESTRICTION }.associateWith(protections::matching)
        val paths = blockable.associate { path ->
            if (path.blockMethod == WakeBlockMethod.BACKGROUND_RESTRICTION) {
                val block = if (backgroundStates[path.target]?.allowed == false) WakePathBlock.USER else WakePathBlock.NONE
                return@associate path.pathKey to WakePathState(path, block, blockedAtMillis = null, seenSinceBlock = false)
            }
            val legacy = if (path.supportsLegacyComponentBlock) blocks[path.pathKey to WakeBlockMethod.COMPONENT] else null
            val saved = blocks[path.pathKey to path.blockMethod] ?: legacy
            val method = saved?.method ?: path.blockMethod
            val statuses = if (method == WakeBlockMethod.COMPONENT) componentStatuses(path) else emptyList()
            val userBlocked = when (method) {
                WakeBlockMethod.FIREWALL_RULE -> saved != null

                WakeBlockMethod.COMPONENT -> statuses.isNotEmpty() && statuses.all { status ->
                    status?.isSwitchOn == false && (status.declaration.id !in owned || status.state.ifw == IfwBlock.BLOCKED)
                }

                else -> false
            }
            val block = when {
                userBlocked -> WakePathBlock.USER
                matches.getValue(path).any { it.active } -> WakePathBlock.AUTOMATIC
                else -> WakePathBlock.NONE
            }
            val blockedAt = saved?.blockedAtMillis?.takeIf { userBlocked }
            val reEnabled = saved != null && !userBlocked && statuses.any { it != null && it.state.pm != PmSetting.UNKNOWN && it.isSwitchOn }
            path.pathKey to WakePathState(
                if (method == path.blockMethod) path else path.copy(blockMethod = method),
                block,
                blockedAt,
                blockedAt != null && path.lastSeenMillis > blockedAt,
                reEnabled,
            )
        }
        val matchedIds = matches.values.asSequence().flatten().mapTo(mutableSetOf()) { it.pathId }
        val unmatched = automatic.filterNot { it.pathId in matchedIds }
        val hasUnblockable = activity.allPaths().any { !it.isBlockable }
        val allBlocked = !hasUnblockable && (paths.isNotEmpty() || unmatched.isNotEmpty()) &&
            paths.values.all { it.block != WakePathBlock.NONE } && unmatched.all { it.active }
        return AppWakeState(activity, paths, unmatched, allBlocked)
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

private val WakePath.isBlockable get() = blockMethod != WakeBlockMethod.NONE

// These paths used PackageManager before the user firewall file supported their component types.
private val WakePath.supportsLegacyComponentBlock get() = blockMethod == WakeBlockMethod.FIREWALL_RULE &&
    trigger == ReviveTrigger.PEER_ACTIVITY
