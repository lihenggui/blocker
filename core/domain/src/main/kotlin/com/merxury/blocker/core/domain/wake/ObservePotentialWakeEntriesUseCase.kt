// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.device.AppManifestReader
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.wake.AutomaticWakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeBlockRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.wake.PotentialWakeCategory
import com.merxury.blocker.core.model.wake.PotentialWakeEntry
import com.merxury.blocker.core.model.wake.ReviveTrigger
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakePath
import com.merxury.blocker.core.model.wake.WakePathBlock
import com.merxury.blocker.core.model.wake.WakePathState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObservePotentialWakeEntriesUseCase @Inject constructor(
    private val manifests: AppManifestReader,
    private val installedApps: InstalledAppRepository,
    private val components: ComponentStatusRepository,
    private val user: WakeBlockRepository,
    private val automatic: AutomaticWakeBlockRepository,
    @Dispatcher(IO) private val io: CoroutineDispatcher,
) {
    /** Rescans the owner app when its installed APK changes and observes the current block state. */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(packageName: String, excludeComponents: Set<String> = emptySet()): Flow<List<PotentialWakeEntry>> {
        val app = AppKey(packageName, UserId.SYSTEM)
        return installedApps.app(app)
            .map { it?.let { Triple(it.apkPath, it.versionCode, it.lastUpdateTime) } }
            .distinctUntilChanged()
            .flatMapLatest {
                val manifest = if (components.isProtectedFromBlocking(app)) null else manifests.read(app)
                val paths = manifest?.let(StaticWakeScanner::scan).orEmpty()
                    .filterNot { it.component in excludeComponents }.map { it to it.toWakePath() }
                if (paths.isEmpty()) return@flatMapLatest flowOf(emptyList())
                combine(components.components(app), user.blocks, automatic.ownedComponents) { statuses, blocks, owned ->
                    val byComponent = statuses.associateBy { it.declaration.id }
                    val saved = blocks.filter { it.method == WakeBlockMethod.COMPONENT }.associateBy { it.pathKey }
                    paths.mapNotNull { (declaration, path) ->
                        val id = ComponentId(packageName, declaration.component)
                        declaration.toEntry(path, byComponent[id], saved[path.pathKey], id in owned)
                    }
                }
            }.distinctUntilChanged().flowOn(io)
    }
}

private fun StaticWakeDeclaration.toEntry(
    path: WakePath,
    status: ComponentStatus?,
    saved: WakeBlock?,
    owned: Boolean,
): PotentialWakeEntry? {
    if (!enabledInManifest && status?.state?.pm == PmSetting.DEFAULT && saved == null && !owned && status.state.ifw != IfwBlock.BLOCKED) {
        return null
    }
    val userBlocked = status?.isSwitchOn == false && (!owned || status.state.ifw == IfwBlock.BLOCKED)
    val automaticBlocked = !userBlocked && owned && status?.isSwitchOn == false
    val block = when {
        userBlocked -> WakePathBlock.USER
        automaticBlocked -> WakePathBlock.AUTOMATIC
        else -> WakePathBlock.NONE
    }
    val reEnabled = saved != null && !userBlocked && status != null && status.state.pm != PmSetting.UNKNOWN && status.isSwitchOn
    return PotentialWakeEntry(
        category,
        WakePathState(path, block, saved?.blockedAtMillis?.takeIf { userBlocked }, seenSinceBlock = false, reEnabledByApp = reEnabled),
    )
}

private fun StaticWakeDeclaration.toWakePath() = WakePath(
    pathKey = "STATIC|${category.name}|$packageName|$component",
    target = packageName,
    actor = null,
    trigger = when (category) {
        PotentialWakeCategory.BOOT_AUTOSTART, PotentialWakeCategory.SYSTEM_BROADCAST -> ReviveTrigger.SYSTEM_BROADCAST
        PotentialWakeCategory.PUSH_RECEIVER -> ReviveTrigger.PUSH_TRANSPORT
        PotentialWakeCategory.SCHEDULED_JOB -> ReviveTrigger.JOB
        PotentialWakeCategory.ACCOUNT_SYNC -> ReviveTrigger.SYNC
    },
    hold = null,
    component = component,
    componentType = componentType,
    action = action,
    count = 0L,
    durationNanos = 0L,
    screenOffNanos = 0L,
    lastSeenMillis = 0L,
    afterKill = false,
    blockMethod = WakeBlockMethod.COMPONENT,
)
