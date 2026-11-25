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

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.merxury.blocker.core.appcontrol.AppControl
import com.merxury.blocker.core.control.ComponentControl
import com.merxury.blocker.core.control.ComponentControlPersistence
import com.merxury.blocker.core.control.PackageLocks
import com.merxury.blocker.core.database.cache.ComponentCacheDatabase
import com.merxury.blocker.core.database.record.BlockerRecordsDatabase
import com.merxury.blocker.core.database.wake.WakeHistoryDatabase
import com.merxury.blocker.core.datastore.AppProperties
import com.merxury.blocker.core.datastore.BlockerAppPropertiesDataStore
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.datastore.UserPreferences
import com.merxury.blocker.core.datastore.test.InMemoryDataStore
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ComponentWriteObserver
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.layersFor
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.system.PackageRevision
import com.merxury.blocker.core.system.SystemPackageReader
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.core.ifw.di.IfwModule
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.junit.After
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlin.time.Instant

internal const val PKG = "com.example"
internal const val OTHER_PKG = "com.example.other"

/** The package the repositories are told Blocker has. */
internal const val SELF_PKG = "com.merxury.blocker.test"
internal val OWNER = UserId(0)
internal val WORK = UserId(10)

internal fun deviceUser(id: UserId) = DeviceUser(
    id = id,
    name = "User ${id.value}",
    kind = if (id == OWNER) UserKind.OWNER else UserKind.WORK_PROFILE,
    parent = if (id == OWNER) null else OWNER,
    running = true,
)

internal fun installedApp(
    packageName: String,
    user: UserId = OWNER,
    lastUpdateTime: Long = 2_000,
    enabledSetting: AppEnabledSetting = AppEnabledSetting.DEFAULT,
) = InstalledApp(
    key = AppKey(packageName, user),
    uid = 10_000 + user.value * 100_000,
    label = "Label of $packageName",
    versionName = "1.0",
    versionCode = 1,
    isSystem = false,
    enabled = !enabledSetting.isDisabled,
    enabledSetting = enabledSetting,
    apkPath = "/data/app/$packageName/base.apk",
    dataDir = null,
    firstInstallTime = Instant.fromEpochMilliseconds(1_000),
    lastUpdateTime = Instant.fromEpochMilliseconds(lastUpdateTime),
    minSdk = 26,
    targetSdk = 35,
)

internal fun declaration(simpleName: String, type: ComponentType = ComponentType.ACTIVITY, packageName: String = PKG) = ComponentDeclaration(ComponentId(packageName, "$packageName.$simpleName"), type, exported = true, enabledInManifest = true)

/** The repositories under test over a real in-memory cache, a fake system and a real rule store. */
abstract class DeviceRepositoryTestBase(queryExecutor: Executor? = null) {
    internal val db: ComponentCacheDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ComponentCacheDatabase::class.java)
        .allowMainThreadQueries()
        .apply { queryExecutor?.let { setQueryExecutor(it).setTransactionExecutor(it) } }
        .build()
    internal val dao = db.componentCacheDao()
    internal val recordsDb: BlockerRecordsDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BlockerRecordsDatabase::class.java)
        .allowMainThreadQueries()
        .apply { queryExecutor?.let { setQueryExecutor(it).setTransactionExecutor(it) } }
        .build()
    internal val recordDao = recordsDb.pmBlockRecordDao()
    internal val pmBlocks = PmBlockRecords(recordDao)
    internal val applicationContext: Context = ApplicationProvider.getApplicationContext()
    internal val appProperties = InMemoryDataStore(AppProperties.getDefaultInstance())
    internal val properties = BlockerAppPropertiesDataStore(appProperties)
    internal val preferences = BlockerPreferencesDataSource(InMemoryDataStore(UserPreferences.getDefaultInstance()))
    internal val wakeDb: WakeHistoryDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WakeHistoryDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    internal val seeder = InstalledAppDbPmBlockRecordSeeder(applicationContext, Dispatchers.IO, properties, preferences, pmBlocks, wakeDb.history())
    internal val system = FakeSystem()
    internal val files = FakeIfwFileSystem()
    internal val store = IfwModule.providesIfwStore(files, IfwXmlSerializer(), IfwXmlDeserializer(), "com.merxury.blocker.test")
    internal val channel = FakePrivilegedChannel()
    internal val packageLocks = PackageLocks()
    internal val componentControl = FakeComponentControl(packageLocks, system)
    internal val appControl = FakeAppControl(system)
    internal val users = LocalDeviceUserRepository(dao, system, channel, pmBlocks)
    internal var elapsedRealtime: () -> Long = { 0L }
    internal val controlActivity = PackageControlActivity { elapsedRealtime() }
    internal val apps = LocalInstalledAppRepository(dao, system, users, appControl, pmBlocks, controlActivity, packageLocks)

    /** Starts collecting the rule store's changes in [scope], as the app scope would. */
    internal fun components(scope: CoroutineScope, claimedWakeBlocks: suspend (UserId, Set<ComponentId>) -> List<ComponentId> = { _, _ -> emptyList() }) = LocalComponentStatusRepository(dao, apps, PackageStateReader(dao, system, store, channel, apps), componentControl, pmBlocks, channel, store, scope, AppKey(SELF_PKG, OWNER), system, controlActivity, packageLocks, claimedWakeBlocks)

    @After
    fun closeDatabase() {
        db.close()
        recordsDb.close()
        wakeDb.close()
    }
}

/**
 * Users, apps, declarations and PackageManager settings in memory, read as [SystemPackageReader].
 * An absent setting is [PmSetting.DEFAULT].
 */
internal class FakeSystem : SystemPackageReader {
    val users: MutableList<DeviceUser> = Collections.synchronizedList(mutableListOf(deviceUser(OWNER), deviceUser(WORK)))
    internal val apps = ConcurrentHashMap<AppKey, InstalledApp>()
    val hidden = ConcurrentHashMap.newKeySet<AppKey>()
    val declarations = ConcurrentHashMap<String, List<ComponentDeclaration>>()
    val declarationsByUser = ConcurrentHashMap<AppKey, List<ComponentDeclaration>>()
    val settings = ConcurrentHashMap<Pair<UserId, ComponentId>, PmSetting>()

    /** Each read as `users`, `apps <user>`, `app <key>`, `declarations <package>` or `pm <key>`. */
    val log: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Volatile var usersFailure: ReadFailure? = null
    val appsFailures = ConcurrentHashMap<UserId, ReadFailure>()
    val installationFailures = ConcurrentHashMap<AppKey, ReadFailure>()
    val declarationFailures = ConcurrentHashMap<String, ReadFailure>()
    val pmFailures = ConcurrentHashMap<String, ReadFailure>()

    var beforePmRead: suspend (AppKey) -> Unit = {}

    /** Runs after the settings were read and before they are returned. */
    @Volatile var afterPmRead: suspend (AppKey) -> Unit = {}

    fun install(app: InstalledApp, vararg components: ComponentDeclaration) {
        apps[app.key] = app
        if (components.isNotEmpty()) declarations[app.key.packageName] = components.toList()
    }

    fun put(user: UserId, component: ComponentDeclaration, setting: PmSetting) {
        settings[user to component.id] = setting
    }

    fun reads(prefix: String): Int = log.count { it.startsWith(prefix) }

    override suspend fun users(): ReadResult<List<DeviceUser>> {
        log += "users"
        return usersFailure?.let { ReadResult.Failure(it) } ?: ReadResult.Success(users.toList())
    }

    override suspend fun apps(user: UserId): ReadResult<List<InstalledApp>> {
        log += "apps ${user.value}"
        appsFailures[user]?.let { return ReadResult.Failure(it) }
        return ReadResult.Success(apps.values.filter { it.key.userId == user && it.key !in hidden }.sortedBy { it.key.packageName })
    }

    var beforeRevisionsRead: suspend (UserId) -> Unit = {}

    override suspend fun packageRevisions(user: UserId): ReadResult<Map<String, PackageRevision>> {
        log += "revisions ${user.value}"
        beforeRevisionsRead(user)
        appsFailures[user]?.let { return ReadResult.Failure(it) }
        return ReadResult.Success(
            apps.values.filter { it.key.userId == user && it.key !in hidden }.associate { it.key.packageName to PackageRevision(it.versionCode, it.lastUpdateTime) },
        )
    }

    override suspend fun app(key: AppKey): ReadResult<InstalledApp> {
        log += "app $key"
        appsFailures[key.userId]?.let { return ReadResult.Failure(it) }
        return apps[key]?.takeUnless { key in hidden }?.let { ReadResult.Success(it) } ?: ReadResult.Failure(ReadFailure.PackageNotFound(key))
    }

    override suspend fun isInstalled(key: AppKey): ReadResult<Boolean> {
        installationFailures[key]?.let { return ReadResult.Failure(it) }
        appsFailures[key.userId]?.let { return ReadResult.Failure(it) }
        return ReadResult.Success(key in apps.keys)
    }

    override suspend fun declarations(app: AppKey): ReadResult<List<ComponentDeclaration>> {
        log += "declarations ${app.packageName}"
        declarationFailures[app.packageName]?.let { return ReadResult.Failure(it) }
        if (!apps.containsKey(app)) return ReadResult.Failure(ReadFailure.PackageNotFound(app))
        return ReadResult.Success(declarationsByUser[app] ?: declarations[app.packageName].orEmpty())
    }

    override suspend fun pmSettings(app: AppKey, components: Collection<ComponentId>): ReadResult<Map<ComponentId, PmSetting>> {
        log += "pm $app"
        beforePmRead(app)
        pmFailures[app.packageName]?.let { return ReadResult.Failure(it) }
        if (!apps.containsKey(app)) return ReadResult.Failure(ReadFailure.PackageNotFound(app))
        val read = components.associateWith { settings[app.userId to it] ?: PmSetting.DEFAULT }
        afterPmRead(app)
        return ReadResult.Success(read)
    }
}

/** Answers each request with [apply]; [state] is not used by the repositories. */
internal class FakeComponentControl(private val locks: PackageLocks, private val system: FakeSystem) : ComponentControl {
    var apply: (ControlRequest) -> Flow<ControlEvent> = { error("No control expected: $it") }

    override suspend fun state(app: AppKey, components: Collection<ComponentDeclaration>): Map<ComponentId, ControlState> = error("Not used")

    override fun apply(
        request: ControlRequest,
        writeObserver: ComponentWriteObserver?,
        persistence: ComponentControlPersistence?,
    ): Flow<ControlEvent> = flow {
        val before = system.settings.toMap()
        apply.invoke(request).collect { event ->
            if (event is ControlEvent.PackageDone && persistence != null) {
                locks.withLock(event.packageName) {
                    val app = AppKey(event.packageName, request.user)
                    val components = request.components.associateBy { it.id }
                    val intended = event.outcomes.mapNotNull { outcome ->
                        val id = outcome.key.id
                        val declaration = components.getValue(id)
                        id.takeIf {
                            request.goal == ControlGoal.BLOCK &&
                                ControlLayer.PACKAGE_MANAGER in declaration.layersFor(request.layers) &&
                                before[request.user to id] != PmSetting.DISABLED
                        }
                    }
                    withContext(NonCancellable) {
                        if (intended.isNotEmpty()) persistence.beforePmBlock(app, intended)
                        val alreadyDisabled = event.outcomes.mapNotNullTo(mutableSetOf()) { outcome ->
                            val id = outcome.key.id
                            id.takeIf {
                                request.goal == ControlGoal.BLOCK &&
                                    ControlLayer.PACKAGE_MANAGER in components.getValue(id).layersFor(request.layers) &&
                                    before[request.user to id] == PmSetting.DISABLED
                            }
                        }
                        persistence.afterPackage(event, alreadyDisabled)
                    }
                }
            }
            emit(event)
        }
    }
}

/**
 * Changes the apps of [system]. [answer], when set, replaces what [setEnabled] returns, so tests
 * can tell the call's answer apart from the state read afterwards.
 */
internal class FakeAppControl(private val system: FakeSystem) : AppControl {
    var callFailure: CallFailure? = null
    var answer: AppEnabledSetting? = null

    override suspend fun setEnabled(app: AppKey, enabled: Boolean): CallResult<AppEnabledSetting> {
        callFailure?.let { return CallResult.Failure(it) }
        val installed = system.apps[app] ?: return CallResult.Failure(CallFailure.TargetNotFound(app.toString()))
        val setting = if (enabled) AppEnabledSetting.ENABLED else AppEnabledSetting.DISABLED_USER
        system.apps[app] = installed.copy(enabled = !setting.isDisabled, enabledSetting = setting)
        return CallResult.Success(answer ?: setting)
    }

    override suspend fun uninstall(app: AppKey): CallResult<Unit> {
        callFailure?.let { return CallResult.Failure(it) }
        system.apps.remove(app) ?: return CallResult.Failure(CallFailure.TargetNotFound(app.toString()))
        return CallResult.Success(Unit)
    }

    override suspend fun forceStop(app: AppKey): CallResult<Unit> = error("Not used")

    override suspend fun clearCache(app: AppKey): CallResult<Unit> = error("Not used")

    override suspend fun clearData(app: AppKey): CallResult<Unit> = error("Not used")

    override suspend fun runningPackages(user: UserId): CallResult<Set<String>> = error("Not used")

    override suspend fun startActivity(key: ComponentKey): CallResult<Unit> = error("Not used")
}
