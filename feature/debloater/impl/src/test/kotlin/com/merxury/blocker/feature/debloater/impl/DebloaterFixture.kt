/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.feature.debloater.impl

import androidx.lifecycle.SavedStateHandle
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.DebloaterApk
import com.merxury.blocker.core.data.device.DebloaterMetadataRepository
import com.merxury.blocker.core.data.device.PackageControlActivity
import com.merxury.blocker.core.data.util.PermissionMonitor
import com.merxury.blocker.core.data.util.PermissionStatus
import com.merxury.blocker.core.domain.control.ComponentBatchRunner
import com.merxury.blocker.core.domain.control.ControlComponentUseCase
import com.merxury.blocker.core.domain.control.GetControlLayersUseCase
import com.merxury.blocker.core.domain.search.ComponentIndex
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.manifest.AndroidManifest
import com.merxury.blocker.core.model.manifest.IntentFilterData
import com.merxury.blocker.core.model.manifest.ManifestActivity
import com.merxury.blocker.core.model.manifest.ManifestApplication
import com.merxury.blocker.core.model.manifest.ManifestIntentFilter
import com.merxury.blocker.core.model.manifest.ManifestService
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlin.time.Instant

internal val owner = UserId.SYSTEM
internal val work = UserId(10)
internal val ownerUser = DeviceUser(owner, null, UserKind.OWNER, null, true)
internal val workUser = DeviceUser(work, null, UserKind.WORK_PROFILE, owner, true)
internal const val SELF = "example.blocker"
internal const val APP = "example.app"

internal class DebloaterFixture(appScope: CoroutineScope, dispatcher: CoroutineDispatcher, savedStateHandle: SavedStateHandle = SavedStateHandle()) {
    val users = FakeSelectedUserRepository(owner).apply { sendUsers(listOf(ownerUser, workUser)) }
    val apps = FakeInstalledAppRepository()
    val components = FakeComponentStatusRepository()
    val channel = FakePrivilegedChannel()
    val preferences = TestUserDataRepository().apply { sendUserData(defaultUserData.copy(controllerType = ControllerType.PM)) }
    val permission = MutableStateFlow<PermissionStatus?>(PermissionStatus.ROOT_USER)
    val metadata = FakeMetadata()
    var controlGate = CompletableDeferred(Unit)
    var preparationGate = CompletableDeferred(Unit)
    var preparationFailure: Exception? = null
    val started = mutableListOf<ControlRequest>()
    val repository = object : ComponentStatusRepository by components {
        override fun control(request: ControlRequest) = flow {
            started += request
            controlGate.await()
            emitAll(components.control(request))
        }

        override fun components(app: AppKey) = flow {
            preparationGate.await()
            preparationFailure?.let { throw it }
            emitAll(components.components(app))
        }
    }
    val batches = ComponentBatchRunner(repository, appScope, PackageControlActivity())
    val index = ComponentIndex(repository, channel, appScope)
    private val layers = GetControlLayersUseCase(preferences)
    val viewModel = DebloaterViewModel(
        selectedUsers = users,
        matches = DebloaterMatches(apps, repository, metadata, preferences, users, SELF, dispatcher),
        componentIndex = index,
        componentStatuses = repository,
        batches = batches,
        controlComponent = ControlComponentUseCase(repository, layers),
        layersFor = layers,
        channel = channel,
        permissionMonitor = object : PermissionMonitor {
            override val permissionStatus = permission.filterNotNull()
        },
        analyticsHelper = TestAnalyticsHelper(),
        cpuDispatcher = dispatcher,
        savedStateHandle = savedStateHandle,
    )

    fun install(vararg installed: InstalledApp) {
        apps.sendApps(installed.toList())
        installed.forEach { app ->
            val packageName = app.key.packageName
            val share = ManifestIntentFilter(
                actions = listOf("android.intent.action.SEND"),
                categories = listOf("android.intent.category.DEFAULT"),
                data = listOf(IntentFilterData(mimeType = "text/plain")),
            )
            val deepLink = ManifestIntentFilter(
                actions = listOf("android.intent.action.VIEW"),
                categories = listOf("android.intent.category.DEFAULT", "android.intent.category.BROWSABLE"),
                data = listOf(IntentFilterData(scheme = "https")),
            )
            val launcher = ManifestIntentFilter(
                actions = listOf("android.intent.action.MAIN"),
                categories = listOf("android.intent.category.LAUNCHER"),
            )
            val manifest = AndroidManifest(
                packageName,
                application = ManifestApplication(
                    activities = listOf(
                        ManifestActivity(".Share", label = "Send to other apps", intentFilters = listOf(share)),
                        ManifestActivity(".Link", intentFilters = listOf(deepLink)),
                        ManifestActivity(".Home", intentFilters = listOf(deepLink, launcher)),
                    ),
                    services = listOf(ManifestService(".PushService")),
                ),
            )
            metadata.apks[app.key] = DebloaterApk(app.key, "/live/$packageName/base.apk", 1, 0, emptyList())
            metadata.manifests[app.key] = manifest
        }
        installed.groupBy { it.key.userId }.forEach { (user, group) ->
            metadata.order[user] = group.map { it.key.packageName }
            components.sendComponents(
                user,
                group.flatMap { app ->
                    listOf(status(app, "Share"), status(app, "Link"), status(app, "Home"), status(app, "PushService", ComponentType.SERVICE))
                },
            )
        }
    }
}

internal class FakeMetadata : DebloaterMetadataRepository {
    val order = mutableMapOf<UserId, List<String>>()
    val apks = mutableMapOf<AppKey, DebloaterApk>()
    val manifests = mutableMapOf<AppKey, AndroidManifest>()
    val readApks = mutableListOf<DebloaterApk>()
    val reads get() = readApks.map { it.app }
    var failure: Exception? = null
    var orderFailure: Exception? = null

    override suspend fun packageOrder(user: UserId): List<String> {
        orderFailure?.let { throw it }
        return order[user].orEmpty()
    }

    override suspend fun apk(app: AppKey): DebloaterApk? {
        failure?.let { throw it }
        return apks[app]
    }

    override suspend fun read(apk: DebloaterApk): AndroidManifest? {
        readApks += apk
        return manifests[apk.app]
    }
}

internal fun installed(packageName: String = APP, user: UserId = owner, label: String = "Example", system: Boolean = false) = InstalledApp(
    key = AppKey(packageName, user),
    uid = null,
    label = label,
    versionName = "1",
    versionCode = 1,
    isSystem = system,
    enabled = true,
    enabledSetting = AppEnabledSetting.DEFAULT,
    apkPath = null,
    dataDir = null,
    firstInstallTime = Instant.fromEpochMilliseconds(0),
    lastUpdateTime = Instant.fromEpochMilliseconds(0),
    minSdk = 21,
    targetSdk = 35,
)

internal fun status(
    app: InstalledApp,
    name: String,
    type: ComponentType = ComponentType.ACTIVITY,
    shippedEnabled: Boolean = true,
    pm: PmSetting = PmSetting.DEFAULT,
    ifw: IfwBlock = IfwBlock.NOT_BLOCKED,
) = ComponentStatus(
    ComponentDeclaration(ComponentId(app.key.packageName, "${app.key.packageName}.$name"), type, exported = false, enabledInManifest = shippedEnabled),
    ControlState(pm, ifw),
)
