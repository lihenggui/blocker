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
import com.merxury.blocker.core.model.component.isFilteredByIntentFirewall
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [ComponentStatusRepository]. Tests set the cached components with [sendComponents].
 * By default [control] changes the requested layers of each component as ComponentControl does for
 * the goal, stores the new state and reports every component as achieved; set [controlEvents] to answer otherwise.
 * Refreshes succeed and change nothing unless [refreshFailure] is set.
 */
class FakeComponentStatusRepository : ComponentStatusRepository {
    private val cached = MutableStateFlow<Map<ComponentKey, ComponentStatus>>(emptyMap())
    private val readable = MutableStateFlow(true)

    /** When set, [refresh], [refreshAll] and [disabledByPackageManager] fail with it. */
    var refreshFailure: ReadFailure? = null

    /**
     * [refresh] of an app of one of these packages fails with its reason, after [refreshFailure],
     * and [disabledByPackageManager] lists the package as unreadable.
     */
    val appRefreshFailures = mutableMapOf<String, ReadFailure>()

    /** When set, [control] emits these events instead of changing the cached state. */
    var controlEvents: ((ControlRequest) -> List<ControlEvent>)? = null

    /** The requests [control] received, in order. */
    val requests = mutableListOf<ControlRequest>()

    /** The apps [refresh] received, in order. */
    val refreshedApps = mutableListOf<AppKey>()

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

    override fun refreshAll(user: UserId): Flow<RefreshProgress> = flow {
        refreshFailure?.let {
            emit(RefreshProgress.Finished(total = 0, failed = 0, appListFailure = it))
            return@flow
        }
        val packages = cached.value.keys.filter { it.userId == user }.map { it.id.packageName }.distinct().sorted()
        emit(RefreshProgress.Refreshing(0, packages.size, null))
        packages.forEachIndexed { index, packageName -> emit(RefreshProgress.Refreshing(index + 1, packages.size, packageName)) }
        emit(RefreshProgress.Finished(total = packages.size, failed = 0, appListFailure = null))
    }

    override fun control(request: ControlRequest): Flow<ControlEvent> = flow {
        requests += request
        controlEvents?.let { events ->
            events(request).forEach { emit(it) }
            return@flow
        }
        val outcomes = request.components.distinctBy { it.id }.map { declaration ->
            val key = ComponentKey(declaration.id, request.user)
            val before = cached.value[key]?.state ?: ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED)
            val layers = if (declaration.type.isFilteredByIntentFirewall) request.layers else setOf(ControlLayer.PACKAGE_MANAGER)
            val block = request.goal == ControlGoal.BLOCK
            val state = ControlState(
                pm = if (ControlLayer.PACKAGE_MANAGER in layers) pmAfter(request.goal, declaration, before.pm) else before.pm,
                ifw = if (ControlLayer.INTENT_FIREWALL in layers) (if (block) IfwBlock.BLOCKED else IfwBlock.NOT_BLOCKED) else before.ifw,
            )
            cached.update { it + (key to ComponentStatus(declaration, state)) }
            ComponentOutcome(key, state, ControlResult.ACHIEVED, emptyMap())
        }
        outcomes.groupBy { it.key.id.packageName }.forEach { (packageName, done) -> emit(ControlEvent.PackageDone(packageName, done)) }
        emit(ControlEvent.Finished(achieved = outcomes.size, partial = 0, failed = 0))
    }

    override suspend fun disabledByPackageManager(user: UserId): ReadResult<DisabledComponents> {
        refreshFailure?.let { return ReadResult.Failure(it) }
        val unreadable = cached.value.keys.filter { it.userId == user && it.id.packageName in appRefreshFailures }.map { AppKey(it.id.packageName, user) }.distinct()
        val disabled = cached.value.filter { (key, status) -> key.userId == user && status.state.pm == PmSetting.DISABLED && key.id.packageName !in appRefreshFailures }.keys
        return ReadResult.Success(DisabledComponents(disabled.sortedWith(KEY_ORDER), unreadable.sortedBy { it.packageName }))
    }

    // The setting ComponentControl leaves for each goal.
    private fun pmAfter(goal: ControlGoal, declaration: ComponentDeclaration, before: PmSetting): PmSetting = when (goal) {
        ControlGoal.BLOCK -> PmSetting.DISABLED

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
