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
import com.merxury.blocker.core.database.cache.AppEntity
import com.merxury.blocker.core.database.cache.ComponentCacheDao
import com.merxury.blocker.core.database.cache.ComponentRow
import com.merxury.blocker.core.database.cache.asExternalModel
import com.merxury.blocker.core.di.AppPackageName
import com.merxury.blocker.core.di.ApplicationScope
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
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.privileged.withLocalPackageReads
import com.merxury.blocker.core.system.SystemPackageReader
import com.merxury.core.ifw.IfwStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
internal const val AUTOMATIC_REFRESH_PARALLELISM = 2

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
 * The per-user scan lock covers reconciliation and full indexing. A scan can take a package
 * lock, and a package refresh can take the app-list lock; neither path takes them in reverse.
 */
@Singleton
internal class LocalComponentStatusRepository(
    private val dao: ComponentCacheDao,
    private val installedApps: LocalInstalledAppRepository,
    private val packages: PackageStateReader,
    private val componentControl: ComponentControl,
    private val pmBlocks: PmBlockRecords,
    private val channel: PrivilegedChannel,
    ifwStore: IfwStore,
    appScope: CoroutineScope,
    private val self: AppKey,
    private val reader: SystemPackageReader,
    private val controlActivity: PackageControlActivity,
) : ComponentStatusRepository {

    @Inject constructor(
        dao: ComponentCacheDao,
        installedApps: LocalInstalledAppRepository,
        packages: PackageStateReader,
        componentControl: ComponentControl,
        pmBlocks: PmBlockRecords,
        channel: PrivilegedChannel,
        ifwStore: IfwStore,
        @ApplicationScope appScope: CoroutineScope,
        @AppPackageName appPackageName: String,
        reader: SystemPackageReader,
        controlActivity: PackageControlActivity,
    ) : this(dao, installedApps, packages, componentControl, pmBlocks, channel, ifwStore, appScope, AppKey(appPackageName, ownUser()), reader, controlActivity)

    private val locks = ConcurrentHashMap<String, Mutex>()
    private val scanLocks = ConcurrentHashMap<UserId, Mutex>()
    private val automaticRefreshes = Semaphore(AUTOMATIC_REFRESH_PARALLELISM)
    private val retries = ConcurrentHashMap.newKeySet<AppKey>()

    init {
        // Rule editor saves and imports change the rules without going through control().
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
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

    override suspend fun refresh(app: AppKey): ReadResult<Unit> = lockOf(app.packageName).withLock {
        val previous = dao.getApp(app.packageName, app.userId.value)
        val (installed, appFailure) = when (val read = installedApps.refreshApp(app)) {
            is ReadResult.Success -> {
                if (read.value == null) {
                    packages.forgetDeclarationRetryIfUncached(app.packageName)
                    return@withLock ReadResult.Failure(ReadFailure.PackageNotFound(app))
                }
                read.value to null
            }

            is ReadResult.Failure -> null to read.reason
        }
        refreshPackageLocked(app, installed, appFailure, previous = previous, forceDeclarations = true, firewall = FirewallRead.FORCE).asResult()
    }

    override suspend fun refreshIfReadable(app: AppKey, firewall: FirewallRead): ReadResult<Unit> = lockOf(app.packageName).withLock {
        if (firewall == FirewallRead.KEEP_CACHED) {
            if (app.userId != self.userId) return@withLock ReadResult.Failure(ReadFailure.ChannelUnavailable)
            return@withLock withLocalPackageReads { readAndStoreAutomatically(app, forceDeclarations = true, firewall = firewall) }
        }
        readAndStoreAutomatically(app, forceDeclarations = true, firewall = firewall)
    }

    /**
     * A broadcast is an invalidation. Confirm live presence in case a reinstall followed removal,
     * and return false to retry if a write started or a newer broadcast arrived while queued.
     */
    suspend fun refreshAfterPackageChange(app: AppKey, isLatest: () -> Boolean): Boolean = automaticRefreshes.withPermit {
        lockOf(app.packageName).withLock {
            if (controlActivity.isActive(app.packageName) || !isLatest()) return@withLock false
            refreshAutomatically(app)
            true
        }
    }

    suspend fun usersWithCachedApps(): List<UserId> = dao.appUsers().map(::UserId)

    suspend fun liveUsers(): Map<UserId, DeviceUser> = when (val read = reader.users()) {
        is ReadResult.Success -> read.value.associateBy { it.id }

        is ReadResult.Failure -> {
            Timber.w("Cannot read user states for package reconciliation: ${read.reason}")
            installedApps.cachedUsers().associateBy { it.id }
        }
    }

    /**
     * Runs under the same per-user lock as a full component index. The version list only
     * selects candidates: each candidate is read live under the package lock before any write.
     */
    suspend fun reconcile(user: UserId, state: DeviceUser?) = scanLockOf(user).withLock {
        if (!canRead(user) || state?.running == false || state?.unlocked == false) return@withLock
        val cached = dao.getApps(user.value).associateBy { it.packageName }
        if (cached.isEmpty()) return@withLock
        val installed = when (val read = reader.packageRevisions(user)) {
            is ReadResult.Success -> read.value

            is ReadResult.Failure -> {
                Timber.w("Cannot reconcile the packages of user ${user.value}: ${read.reason}")
                return@withLock
            }
        }
        val indexed = dao.hasIndexedApps(user.value)
        val recorded = pmBlocks.of(user).map { it.id.packageName }
        val candidates = installed.filter { (name, revision) ->
            val app = cached[name]
            packages.needsDeclarationRetry(name) ||
                if (app == null) indexed else app.versionCode != revision.versionCode || app.lastUpdateTime != revision.lastUpdateTime
        }.keys + ((cached.keys + recorded) - installed.keys) + retries.filter { it.userId == user }.map { it.packageName }
        for (packageName in candidates) {
            if (!canRead(user)) break
            automaticRefreshes.withPermit {
                lockOf(packageName).withLock { refreshAutomatically(AppKey(packageName, user)) }
            }
        }
    }

    private fun canRead(user: UserId): Boolean = user == self.userId || channel.state.value is ChannelState.Ready

    private suspend fun refreshAutomatically(app: AppKey) {
        // Keep same-revision failures eligible for the next reconciliation.
        retries += app
        try {
            if (!canRead(app.userId)) return
            val result = readAndStoreAutomatically(app)
            if (result is ReadResult.Failure && result.reason !is ReadFailure.PackageNotFound) {
                Timber.w("Cannot refresh changed package $app: ${result.reason}")
            } else {
                retries -= app
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot refresh changed package $app")
        }
    }

    private suspend fun readAndStoreAutomatically(
        app: AppKey,
        forceDeclarations: Boolean = false,
        firewall: FirewallRead = FirewallRead.IF_AVAILABLE,
    ): ReadResult<Unit> {
        val previous = dao.getApp(app.packageName, app.userId.value)
        val installed = when (val read = installedApps.readForRefresh(app)) {
            is ReadResult.Success -> read.value

            is ReadResult.Failure -> {
                if (read.reason is ReadFailure.PackageNotFound) packages.forgetDeclarationRetryIfUncached(app.packageName)
                return read
            }
        }
        if (installed.enabledSetting == AppEnabledSetting.UNKNOWN) {
            Timber.w("PackageManager did not return the app enabled setting for $app")
            return ReadResult.Failure(ReadFailure.ChannelUnavailable)
        }
        return refreshPackageLocked(app, installed, appFailure = null, automatic = true, previous = previous, forceDeclarations, firewall).asResult()
    }

    override fun refreshAll(user: UserId): Flow<RefreshProgress> = flow {
        scanLockOf(user).withLock { refreshAllApps(user) }
    }

    private suspend fun FlowCollector<RefreshProgress>.refreshAllApps(user: UserId) {
        val apps = when (val listed = listApps(user)) {
            is ReadResult.Success -> listed.value

            is ReadResult.Failure -> {
                emit(RefreshProgress.Finished(total = 0, failed = 0, appListFailure = listed.reason))
                return
            }
        }
        emit(RefreshProgress.Refreshing(done = 0, total = apps.size, current = null))
        var done = 0
        var failed = 0
        refreshEach(apps.map { it.key }).collect { (app, refresh) ->
            done++
            if (refresh.failures.isNotEmpty() && !refresh.removed) failed++
            emit(RefreshProgress.Refreshing(done, apps.size, app.packageName))
        }
        Timber.i("Refreshed ${apps.size} apps of user ${user.value}, $failed with failures")
        emit(RefreshProgress.Finished(total = apps.size, failed = failed, appListFailure = null))
    }

    override fun control(request: ControlRequest, keepKnownState: Boolean, writeObserver: ComponentWriteObserver?): Flow<ControlEvent> = control(request, keepKnownState, writeObserver, forgetRefusedPmBlocks = false)

    override fun resetPmBlocks(user: UserId, components: List<ComponentDeclaration>): Flow<ControlEvent> = control(
        ControlRequest(user, components, ControlGoal.UNBLOCK, setOf(ControlLayer.PACKAGE_MANAGER)),
        keepKnownState = false,
        writeObserver = null,
        forgetRefusedPmBlocks = true,
    )

    private fun control(
        request: ControlRequest,
        keepKnownState: Boolean,
        writeObserver: ComponentWriteObserver?,
        forgetRefusedPmBlocks: Boolean,
    ): Flow<ControlEvent> = flow {
        val (own, others) = request.components.partition { request.goal == ControlGoal.BLOCK && AppKey(it.id.packageName, request.user) == self }
        val refused = refuseOwn(request, own)
        refused?.let { emit(it) }
        val refusedCount = refused?.outcomes?.size ?: 0
        if (others.isEmpty()) {
            emit(ControlEvent.Finished(achieved = 0, partial = 0, failed = refusedCount))
            return@flow
        }
        val packages = others.mapTo(mutableSetOf()) { it.id.packageName }
        controlActivity.duringControl(packages) {
            componentControl.apply(request.copy(components = others), writeObserver).collect { event ->
                when (event) {
                    is ControlEvent.PackageDone -> {
                        store(request, event, keepKnownState, forgetRefusedPmBlocks)
                        controlActivity.recordWrites(AppKey(event.packageName, request.user), event.pmWrites)
                        emit(event)
                    }

                    is ControlEvent.Finished -> emit(event.copy(failed = event.failed + refusedCount))
                }
            }
        }
    }

    /**
     * Blocking Blocker's own components in the user it runs in could only break the app in use,
     * whichever screen, import or restore asks, so they are never sent. They end as not
     * permitted, with the state the cache holds, which stays as it is.
     */
    private suspend fun refuseOwn(request: ControlRequest, own: List<ComponentDeclaration>): ControlEvent.PackageDone? {
        if (own.isEmpty()) return null
        Timber.w("Refuse to block ${own.size} components of Blocker itself for user ${request.user.value}")
        val outcomes = own.distinctBy { it.id }.map { declaration ->
            val key = ComponentKey(declaration.id, request.user)
            ComponentOutcome(
                key = key,
                state = component(key).first()?.state ?: ControlState(PmSetting.UNKNOWN, IfwBlock.UNKNOWN),
                result = ControlResult.FAILED,
                failures = declaration.layersFor(request.layers).associateWith { ControlFailure.NotPermitted(message = null) },
            )
        }
        return ControlEvent.PackageDone(self.packageName, outcomes)
    }

    override suspend fun disabledByBlocker(user: UserId): ReadResult<DisabledComponents> {
        val recordedApps = pmBlocks.of(user).groupBy { it.id.packageName }
        // Listing the apps also drops the records of apps gone for the user.
        val apps = when (val listed = listApps(user)) {
            is ReadResult.Success -> listed.value.associateBy { it.key.packageName }
            is ReadResult.Failure -> return listed
        }
        val disabled = mutableListOf<ComponentKey>()
        val unreadable = mutableListOf<AppKey>()
        var notInstalled = 0
        var notDeclared = 0
        recordedApps.forEach { (packageName, recorded) ->
            val key = AppKey(packageName, user)
            val app = apps[packageName]
            val refresh = if (app == null) PackageRefresh.removed(key) else refreshPackage(key, app, appFailure = null)
            val settings = refresh.pmSettings
            when {
                refresh.removed -> {
                    notInstalled += recorded.size
                }

                settings == null || refresh.pmFailure != null || recorded.any { settings[it.id] == PmSetting.UNKNOWN } -> {
                    Timber.w("Cannot read the PM settings of $key: ${refresh.pmFailure}")
                    unreadable += key
                }

                else -> recorded.forEach {
                    when (settings[it.id]) {
                        null -> notDeclared++
                        PmSetting.DISABLED -> disabled += it
                        else -> Unit
                    }
                }
            }
        }
        return ReadResult.Success(DisabledComponents(disabled, unreadable, notInstalled, notDeclared))
    }

    /** Refreshes the user's app list and returns the apps it stored. */
    private suspend fun listApps(user: UserId): ReadResult<List<InstalledApp>> = when (val read = installedApps.refresh(user)) {
        is ReadResult.Success -> ReadResult.Success(dao.getApps(user.value).map { it.asExternalModel() })
        is ReadResult.Failure -> read
    }

    /** Refreshes the components of [apps], [REFRESH_PARALLELISM] at a time, in the order they finish. */
    private fun refreshEach(apps: List<AppKey>): Flow<Pair<AppKey, PackageRefresh>> = channelFlow {
        val queue = Channel<AppKey>(Channel.UNLIMITED)
        apps.forEach { queue.send(it) }
        queue.close()
        repeat(minOf(REFRESH_PARALLELISM, apps.size)) {
            launch {
                for (app in queue) {
                    val refresh = lockOf(app.packageName).withLock {
                        // A broadcast or detail refresh may have changed the row since listing.
                        val cached = dao.getApp(app.packageName, app.userId.value)?.asExternalModel()
                        if (cached == null) PackageRefresh.removed(app) else refreshPackageLocked(app, cached, appFailure = null)
                    }
                    send(app to refresh)
                }
            }
        }
    }

    private suspend fun refreshPackage(key: AppKey, app: InstalledApp?, appFailure: ReadFailure?): PackageRefresh = lockOf(key.packageName).withLock {
        refreshPackageLocked(key, app, appFailure)
    }

    private suspend fun refreshPackageLocked(
        key: AppKey,
        app: InstalledApp?,
        appFailure: ReadFailure?,
        automatic: Boolean = false,
        previous: AppEntity? = null,
        forceDeclarations: Boolean = false,
        firewall: FirewallRead = FirewallRead.IF_AVAILABLE,
    ): PackageRefresh {
        val refresh = packages.refresh(key, app, appFailure, automatic, previous, forceDeclarations, firewall)
        if (!refresh.removed && (!automatic || refresh.failures.isEmpty())) {
            withContext(NonCancellable) { pmBlocks.afterRefresh(key, refresh) }
        }
        if (refresh.removed || refresh.failures.isEmpty()) retries -= key
        return refresh
    }

    /**
     * Stores the read-back state of each outcome, both layers in one transaction, then updates the
     * record of Blocker's PackageManager blocks from it; runs to its end once the event has arrived.
     */
    private suspend fun store(
        request: ControlRequest,
        event: ControlEvent.PackageDone,
        keepKnownState: Boolean,
        forgetRefusedPmBlocks: Boolean,
    ) = withContext(NonCancellable) {
        lockOf(event.packageName).withLock {
            event.outcomes.groupBy { it.key.userId }.forEach { (user, outcomes) ->
                dao.storePackageState(
                    packageName = event.packageName,
                    userId = user.value,
                    declarations = null,
                    appLastUpdateTime = null,
                    pmSettings = outcomes.associate { it.key.id.className to it.state.pm }.filterValues { !keepKnownState || it != PmSetting.UNKNOWN },
                    ifwBlocks = outcomes.associate { it.key.id.className to it.state.ifw }.filterValues { !keepKnownState || it != IfwBlock.UNKNOWN },
                )
            }
            pmBlocks.afterControl(request, event, forgetRefusedPmBlocks)
        }
    }

    private fun scanLockOf(user: UserId): Mutex = scanLocks.computeIfAbsent(user) { Mutex() }

    private fun lockOf(packageName: String): Mutex = locks.computeIfAbsent(packageName) { Mutex() }
}

private fun ComponentRow.asStatus(): ComponentStatus = asExternalModel().let { (declaration, state) -> ComponentStatus(declaration, state) }

private fun List<ComponentRow>.asStatuses(): List<ComponentStatus> = map { it.asStatus() }
