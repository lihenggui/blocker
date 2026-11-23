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

package com.merxury.blocker.core.data.device

import com.merxury.blocker.core.control.ComponentControl
import com.merxury.blocker.core.database.cache.ComponentCacheDao
import com.merxury.blocker.core.database.cache.ComponentRow
import com.merxury.blocker.core.database.cache.asExternalModel
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.core.ifw.IfwStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apps refreshed at once by [LocalComponentStatusRepository.refreshAll]. The privileged channel
 * runs one root call at a time, so more would only queue; a few overlap the calls with the local
 * reads and the database writes.
 */
internal const val REFRESH_PARALLELISM = 4

/**
 * [ComponentStatusRepository] over the component cache.
 *
 * ## Locks
 * Each package has one [Mutex] here, held for a refresh from its first read to its last write,
 * for a firewall re-read likewise, and for storing a control's outcomes. ComponentControl holds
 * its own package lock from its first read to its read-back and emits the package's event only
 * after releasing it, so this repository takes its lock only once theirs is free and never calls
 * ComponentControl while holding its own: the two locks are never held together, so they cannot
 * deadlock. The rule store's lock, taken inside a firewall read, is innermost for both. The
 * mutexes are fair, so a control result that arrives while a refresh holds the lock is stored
 * right after that refresh, and a refresh that starts later reads the system after the change.
 */
@Singleton
internal class LocalComponentStatusRepository @Inject constructor(
    private val dao: ComponentCacheDao,
    private val installedApps: InstalledAppRepository,
    private val packages: PackageStateReader,
    private val componentControl: ComponentControl,
    channel: PrivilegedChannel,
    ifwStore: IfwStore,
    @ApplicationScope appScope: CoroutineScope,
) : ComponentStatusRepository {

    private val locks = ConcurrentHashMap<String, Mutex>()

    init {
        // Rule editor saves and imports change the rules without going through control().
        appScope.launch {
            ifwStore.changes.collect { packageName ->
                try {
                    lockOf(packageName).withLock { packages.refreshIfw(packageName) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Cannot store the changed IFW rules of $packageName")
                }
            }
        }
    }

    override val firewallReadable: Flow<Boolean> = channel.state.map { it.allowsFirewallReads }.distinctUntilChanged()

    // The cache query reads the app row with the components, so an app removed for this user
    // emits an empty list at once, never its shared declarations without settings.
    override fun components(app: AppKey): Flow<List<ComponentStatus>> = dao.observeComponents(app.packageName, app.userId.value).map(List<ComponentRow>::asStatuses)

    override fun component(key: ComponentKey): Flow<ComponentStatus?> = dao.observeComponent(key.id.packageName, key.id.className, key.userId.value).map { it?.asStatus() }

    override fun allComponents(user: UserId): Flow<List<ComponentStatus>> = dao.observeAllComponents(user.value).map(List<ComponentRow>::asStatuses)

    override fun search(user: UserId, keyword: String): Flow<List<ComponentStatus>> = dao.searchComponents(user.value, keyword).map(List<ComponentRow>::asStatuses)

    override suspend fun refresh(app: AppKey): ReadResult<Unit> {
        val (installed, appFailure) = when (val read = installedApps.refreshApp(app)) {
            is ReadResult.Success -> (read.value ?: return ReadResult.Failure(ReadFailure.PackageNotFound(app))) to null
            is ReadResult.Failure -> null to read.reason
        }
        return refreshPackage(app, installed, appFailure).asResult()
    }

    override fun refreshAll(user: UserId): Flow<RefreshProgress> = flow {
        val apps = when (val listed = listApps(user)) {
            is ReadResult.Success -> listed.value

            is ReadResult.Failure -> {
                emit(RefreshProgress.Finished(total = 0, failed = 0, appListFailure = listed.reason))
                return@flow
            }
        }
        emit(RefreshProgress.Refreshing(done = 0, total = apps.size, current = null))
        var done = 0
        var failed = 0
        refreshEach(apps).collect { (app, refresh) ->
            done++
            if (refresh.failures.isNotEmpty() && !refresh.removed) failed++
            emit(RefreshProgress.Refreshing(done, apps.size, app.key.packageName))
        }
        Timber.i("Refreshed ${apps.size} apps of user ${user.value}, $failed with failures")
        emit(RefreshProgress.Finished(total = apps.size, failed = failed, appListFailure = null))
    }

    override fun control(request: ControlRequest): Flow<ControlEvent> = flow {
        componentControl.apply(request).collect { event ->
            if (event is ControlEvent.PackageDone) store(event)
            emit(event)
        }
    }

    override suspend fun disabledByPackageManager(user: UserId): ReadResult<List<ComponentKey>> {
        val apps = when (val listed = listApps(user)) {
            is ReadResult.Success -> listed.value
            is ReadResult.Failure -> return listed
        }
        val disabled = mutableListOf<ComponentKey>()
        var failure: ReadFailure? = null
        refreshEach(apps).collect { (app, refresh) ->
            if (refresh.removed) return@collect
            refresh.pmSettings?.forEach { (id, setting) -> if (setting == PmSetting.DISABLED) disabled += ComponentKey(id, user) }
            if (failure == null) failure = refresh.pmFailure?.also { Timber.w("Cannot read the PM settings of ${app.key}: $it") }
        }
        return failure?.let { ReadResult.Failure(it) }
            ?: ReadResult.Success(disabled.sortedWith(compareBy({ it.id.packageName }, { it.id.className })))
    }

    /** Refreshes the user's app list and returns the apps it stored. */
    private suspend fun listApps(user: UserId): ReadResult<List<InstalledApp>> = when (val read = installedApps.refresh(user)) {
        is ReadResult.Success -> ReadResult.Success(dao.getApps(user.value).map { it.asExternalModel() })
        is ReadResult.Failure -> read
    }

    /** Refreshes the components of [apps], [REFRESH_PARALLELISM] at a time, in the order they finish. */
    private fun refreshEach(apps: List<InstalledApp>): Flow<Pair<InstalledApp, PackageRefresh>> = channelFlow {
        val queue = Channel<InstalledApp>(Channel.UNLIMITED)
        apps.forEach { queue.send(it) }
        queue.close()
        repeat(minOf(REFRESH_PARALLELISM, apps.size)) {
            launch {
                for (app in queue) send(app to refreshPackage(app.key, app, appFailure = null))
            }
        }
    }

    private suspend fun refreshPackage(key: AppKey, app: InstalledApp?, appFailure: ReadFailure?): PackageRefresh = lockOf(key.packageName).withLock { packages.refresh(key, app, appFailure) }

    /**
     * Stores the read-back state of each outcome, both layers in one transaction; runs to its end
     * once the event has arrived.
     */
    private suspend fun store(event: ControlEvent.PackageDone) = withContext(NonCancellable) {
        lockOf(event.packageName).withLock {
            event.outcomes.groupBy { it.key.userId }.forEach { (user, outcomes) ->
                dao.storePackageState(
                    packageName = event.packageName,
                    userId = user.value,
                    declarations = null,
                    appLastUpdateTime = null,
                    pmSettings = outcomes.associate { it.key.id.className to it.state.pm },
                    ifwBlocks = outcomes.associate { it.key.id.className to it.state.ifw },
                )
            }
        }
    }

    private fun lockOf(packageName: String): Mutex = locks.computeIfAbsent(packageName) { Mutex() }
}

private fun ComponentRow.asStatus(): ComponentStatus = asExternalModel().let { (declaration, state) -> ComponentStatus(declaration, state) }

private fun List<ComponentRow>.asStatuses(): List<ComponentStatus> = map { it.asStatus() }
