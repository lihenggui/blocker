/*
 * Copyright 2026 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.core.testing.repository.device

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.DisabledComponents
import com.merxury.blocker.core.data.device.FirewallRead
import com.merxury.blocker.core.data.device.RefreshProgress
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
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
import com.merxury.blocker.core.model.control.layersFor
import com.merxury.blocker.core.model.control.supportedLayers
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * In-memory [ComponentStatusRepository]. Tests set the cached components with [sendComponents].
 * By default [control] changes the requested layers of each component as ComponentControl does for
 * the goal, stores the new state and reports every component as achieved; set [controlEvents] to answer otherwise.
 * Refreshes succeed and change nothing unless [refreshFailure] is set.
 *
 * Blocker's PackageManager blocks are kept in [recorded] as the real repository keeps them: a
 * block through PackageManager records what reads back DISABLED, any control drops what reads
 * back DEFAULT or ENABLED. [disabledByBlocker] lists the recorded components still DISABLED in
 * the cache; recorded components no longer cached count as not installed and lose their record.
 */
class FakeComponentStatusRepository @Inject constructor() : ComponentStatusRepository {
    private val cached = MutableStateFlow<Map<ComponentKey, ComponentStatus>>(emptyMap())
    private val readable = MutableStateFlow(true)

    /** When set, [refresh], [refreshAll] and [disabledByBlocker] fail with it. */
    var refreshFailure: ReadFailure? = null

    /**
     * [refresh] of an app of one of these packages fails with its reason, after [refreshFailure],
     * and [disabledByBlocker] lists the package as unreadable.
     */
    val appRefreshFailures = mutableMapOf<String, ReadFailure>()

    /** When set, [control] emits these events instead of changing the cached state. */
    var controlEvents: ((ControlRequest) -> List<ControlEvent>)? = null

    var protectedApp: AppKey? = null

    /** The requests [control] received, in order. */
    val requests = mutableListOf<ControlRequest>()

    /** The apps [refresh] received, in order. */
    val refreshedApps = mutableListOf<AppKey>()

    /** The users [refreshAll] was collected for, in order. */
    val refreshedUsers = mutableListOf<UserId>()

    /**
     * When set, [refreshAll] emits what this gives for the user instead of failing or counting, so a test
     * can hold an indexing open and change the cache while it runs.
     */
    var refreshAllProgress: ((UserId) -> Flow<RefreshProgress>)? = null

    /** The components recorded as disabled by Blocker through PackageManager. */
    val recorded = mutableSetOf<ComponentKey>()

    override val firewallReadable: Flow<Boolean> = readable

    /** Sets what [firewallReadable] emits, as a change of the privileged channel would. */
    fun setFirewallReadable(value: Boolean) {
        readable.value = value
    }

    override fun components(app: AppKey): Flow<List<ComponentStatus>> = statuses { it.userId == app.userId && it.id.packageName == app.packageName }

    override fun component(key: ComponentKey): Flow<ComponentStatus?> = cached.map { it[key] }

    override fun allComponents(user: UserId): Flow<List<ComponentStatus>> = statuses { it.userId == user }

    override fun search(user: UserId, keyword: String): Flow<List<ComponentStatus>> = statuses { it.userId == user && it.id.className.contains(keyword, ignoreCase = true) }

    override suspend fun refresh(app: AppKey): ReadResult<Unit> {
        refreshedApps += app
        val failure = refreshFailure ?: appRefreshFailures[app.packageName]
        return failure?.let { ReadResult.Failure(it) } ?: ReadResult.Success(Unit)
    }

    override suspend fun refreshIfReadable(app: AppKey, firewall: FirewallRead): ReadResult<Unit> = refresh(app)

    override fun refreshAll(user: UserId): Flow<RefreshProgress> = flow {
        refreshedUsers += user
        refreshAllProgress?.let { progress ->
            emitAll(progress(user))
            return@flow
        }
        refreshFailure?.let {
            emit(RefreshProgress.Finished(total = 0, failed = 0, appListFailure = it))
            return@flow
        }
        val packages = cached.value.keys.filter { it.userId == user }.map { it.id.packageName }.distinct().sorted()
        emit(RefreshProgress.Refreshing(0, packages.size, null))
        packages.forEachIndexed { index, packageName -> emit(RefreshProgress.Refreshing(index + 1, packages.size, packageName)) }
        emit(RefreshProgress.Finished(total = packages.size, failed = 0, appListFailure = null))
    }

    override fun isProtectedFromBlocking(app: AppKey): Boolean = app == protectedApp

    override fun control(request: ControlRequest, keepKnownState: Boolean, writeObserver: ComponentWriteObserver?): Flow<ControlEvent> = control(request, writeObserver) {
        it.layersFor(request.layers, request.goal)
    }

    override fun restore(request: ControlRequest): Flow<ControlEvent> = control(request, writeObserver = null) {
        it.supportedLayers(request.layers)
    }

    private fun control(
        request: ControlRequest,
        writeObserver: ComponentWriteObserver?,
        layersForComponent: (ComponentDeclaration) -> Set<ControlLayer>,
    ): Flow<ControlEvent> = flow {
        requests += request
        val before = cached.value
        writeObserver?.onPmRead(
            request.components.associate { it.id to (before[ComponentKey(it.id, request.user)]?.state?.pm ?: PmSetting.DEFAULT) },
        )
        controlEvents?.let { events ->
            events(request).forEach {
                if (it is ControlEvent.PackageDone) record(request, it.outcomes, before, layersForComponent)
                emit(it)
            }
            return@flow
        }
        val outcomes = request.components.distinctBy { it.id }.map { declaration ->
            val key = ComponentKey(declaration.id, request.user)
            val before = cached.value[key]?.state ?: ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)
            val expectedPm = request.expectedPm
            if (expectedPm != null && before.pm != expectedPm) {
                val failures = if (before.pm == PmSetting.UNKNOWN) {
                    mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.StateNotConfirmed)
                } else {
                    emptyMap()
                }
                val result = if (failures.isEmpty()) ControlResult.ACHIEVED else ControlResult.FAILED
                return@map ComponentOutcome(key, before, result, failures)
            }
            val layers = layersForComponent(declaration)
            val block = request.goal == ControlGoal.BLOCK
            val state = ControlState(
                pm = if (ControlLayer.PACKAGE_MANAGER in layers) pmAfter(request.goal, declaration, before.pm) else before.pm,
                ifw = if (ControlLayer.INTENT_FIREWALL in layers) (if (block) IfwBlock.BLOCKED else IfwBlock.NOT_BLOCKED) else before.ifw,
            )
            cached.update { it + (key to ComponentStatus(declaration, state)) }
            if (state != before) writeObserver?.onWrite(listOf(declaration.id))
            ComponentOutcome(key, state, ControlResult.ACHIEVED, emptyMap())
        }
        record(request, outcomes, before, layersForComponent)
        outcomes.groupBy { it.key.id.packageName }.forEach { (packageName, done) -> emit(ControlEvent.PackageDone(packageName, done)) }
        emit(ControlEvent.Finished(achieved = outcomes.size, partial = 0, failed = 0))
    }

    override fun resetPmBlocks(user: UserId, components: List<ComponentDeclaration>): Flow<ControlEvent> = control(ControlRequest(user, components, ControlGoal.UNBLOCK, setOf(ControlLayer.PACKAGE_MANAGER))).onEach { event ->
        if (event is ControlEvent.PackageDone) {
            event.outcomes.forEach { outcome ->
                val failure = outcome.failures[ControlLayer.PACKAGE_MANAGER]
                if (failure is ControlFailure.NotPermitted && failure.isSystemRefusal) recorded -= outcome.key
            }
        }
    }

    override suspend fun disabledByBlocker(user: UserId): ReadResult<DisabledComponents> {
        refreshFailure?.let { return ReadResult.Failure(it) }
        val mine = recorded.filter { it.userId == user }.sortedWith(KEY_ORDER)
        val gone = mine.filter { it !in cached.value }
        recorded -= gone.toSet()
        val readable = mine.filter { it in cached.value }
        val unreadable = readable.filter { it.id.packageName in appRefreshFailures }.map { AppKey(it.id.packageName, user) }.distinct()
        val cleared = readable.filter { it.id.packageName !in appRefreshFailures && cached.value.getValue(it).state.pm.let { pm -> pm == PmSetting.DEFAULT || pm == PmSetting.ENABLED } }
        recorded -= cleared.toSet()
        val disabled = readable.filter { it.id.packageName !in appRefreshFailures && cached.value.getValue(it).state.pm == PmSetting.DISABLED }
        return ReadResult.Success(DisabledComponents(disabled, unreadable, notInstalled = gone.size))
    }

    private fun record(
        request: ControlRequest,
        outcomes: List<ComponentOutcome>,
        before: Map<ComponentKey, ComponentStatus>,
        layersForComponent: (ComponentDeclaration) -> Set<ControlLayer>,
    ) {
        val declarations = request.components.associateBy { it.id }
        outcomes.forEach { outcome ->
            if (outcome.result == ControlResult.SKIPPED) return@forEach
            val throughPm = declarations[outcome.key.id]?.let(layersForComponent)?.contains(ControlLayer.PACKAGE_MANAGER) == true
            when (outcome.state.pm) {
                PmSetting.DISABLED -> if (request.goal == ControlGoal.BLOCK && throughPm && before[outcome.key]?.state?.pm != PmSetting.DISABLED) {
                    recorded += outcome.key
                }

                PmSetting.DEFAULT, PmSetting.ENABLED -> recorded -= outcome.key

                PmSetting.UNKNOWN -> Unit
            }
        }
    }

    // The setting ComponentControl leaves for each goal.
    private fun pmAfter(goal: ControlGoal, declaration: ComponentDeclaration, before: PmSetting): PmSetting = when (goal) {
        ControlGoal.BLOCK -> PmSetting.DISABLED

        ControlGoal.ENABLE_EXPLICIT -> PmSetting.ENABLED

        ControlGoal.UNBLOCK -> if (before == PmSetting.DISABLED || before == PmSetting.UNKNOWN) PmSetting.DEFAULT else before

        ControlGoal.ENABLE -> when {
            before == PmSetting.ENABLED || (before == PmSetting.DEFAULT && declaration.enabledInManifest) -> before
            declaration.enabledInManifest -> PmSetting.DEFAULT
            else -> PmSetting.ENABLED
        }
    }

    /** Replaces the cached components of [user]. */
    fun sendComponents(user: UserId, components: List<ComponentStatus>) {
        cached.update { current ->
            current.filterKeys { it.userId != user } + components.associateBy { ComponentKey(it.declaration.id, user) }
        }
    }

    private fun statuses(filter: (ComponentKey) -> Boolean): Flow<List<ComponentStatus>> = cached.map { all ->
        all.filterKeys(filter).entries.sortedWith(compareBy(KEY_ORDER) { it.key }).map { it.value }
    }

    private companion object {
        val KEY_ORDER: Comparator<ComponentKey> = compareBy({ it.id.packageName }, { it.id.className })
    }
}
