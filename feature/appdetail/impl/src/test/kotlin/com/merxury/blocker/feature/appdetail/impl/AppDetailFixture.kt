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

package com.merxury.blocker.feature.appdetail.impl

import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import com.merxury.blocker.core.appcontrol.ServiceControl
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.PackageControlActivity
import com.merxury.blocker.core.domain.control.ComponentBatchRunner
import com.merxury.blocker.core.domain.control.ControlComponentUseCase
import com.merxury.blocker.core.domain.control.GetControlLayersUseCase
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UIDS_PER_USER
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.testing.controller.FakeAppControl
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.controller.FakeServiceControl
import com.merxury.blocker.core.testing.repository.TestComponentDetailRepository
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.device.FakeDeviceUserRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.mockito.kotlin.mock
import java.io.File
import kotlin.time.Instant

internal val OWNER = UserId(0)
internal val WORK = UserId(10)
internal const val PACKAGE = "com.example.app"
internal val OWN_APP = AppKey(PACKAGE, OWNER)
internal val WORK_APP = AppKey(PACKAGE, WORK)

internal val ownerUser = DeviceUser(OWNER, name = "Owner", kind = UserKind.OWNER, parent = null, running = true)
internal val workUser = DeviceUser(WORK, name = "Work", kind = UserKind.WORK_PROFILE, parent = OWNER, running = true)

internal fun installedApp(key: AppKey, label: String = "Example") = InstalledApp(
    key = key,
    uid = 10_100 + key.userId.value * UIDS_PER_USER,
    label = label,
    versionName = "1.0",
    versionCode = 1,
    isSystem = false,
    enabledSetting = AppEnabledSetting.DEFAULT,
    enabled = true,
    apkPath = "/data/app/${key.packageName}/base.apk",
    dataDir = "/data/user/${key.userId.value}/${key.packageName}",
    firstInstallTime = Instant.fromEpochMilliseconds(1_000),
    lastUpdateTime = Instant.fromEpochMilliseconds(2_000),
    minSdk = 24,
    targetSdk = 35,
)

internal fun id(name: String) = ComponentId(PACKAGE, "$PACKAGE.$name")

internal fun component(
    name: String,
    type: ComponentType = ComponentType.ACTIVITY,
    pm: PmSetting = PmSetting.DEFAULT,
    ifw: IfwBlock = IfwBlock.NOT_BLOCKED,
    enabledInManifest: Boolean = true,
) = ComponentStatus(
    declaration = ComponentDeclaration(id(name), type, exported = false, enabledInManifest = enabledInManifest),
    state = ControlState(pm, ifw),
)

/**
 * The fakes behind the three view models of the detail screen, with root ready unless set otherwise.
 *
 * @param rulesRead whether the general rules have been read; when false their flow emits nothing
 * until a test sends them
 * @param descriptionsRead whether the component descriptions can be read; when false each lookup
 * waits until a test sends them
 */
internal class AppDetailFixture(
    private val dispatcher: CoroutineDispatcher,
    val userId: Int? = null,
    tab: String = "receiver",
    searchKeyword: List<String> = emptyList(),
    rulesRead: Boolean = true,
    descriptionsRead: Boolean = true,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) {
    val selectedUsers = FakeSelectedUserRepository(OWNER)
    val deviceUsers = FakeDeviceUserRepository()
    val installedApps = FakeInstalledAppRepository()
    val components = FakeComponentStatusRepository()
    val appControl = FakeAppControl()
    val serviceControl = FakeServiceControl()
    val channel = FakePrivilegedChannel(ChannelState.Ready(PrivilegedIdentity.ROOT))
    val details = TestComponentDetailRepository().apply { if (descriptionsRead) sendComponentDetail(emptyList()) }
    val rules = TestGeneralRuleRepository().apply { if (rulesRead) sendRuleList(emptyList()) }
    val userData = TestUserDataRepository().apply { sendUserData(defaultUserData) }
    val analytics = TestAnalyticsHelper()
    val workManager = mock<WorkManager>()
    val user: UserId = userId?.let(::UserId) ?: OWNER

    /** Replaces [serviceControl] for the list when set before the list is first used. */
    var services: ServiceControl? = null

    /** Replaces [components] for the control view model and its batches when set before it is first used. */
    var controlStatuses: ComponentStatusRepository? = null

    /** The application's scope, which batches run in; it outlives every view model. */
    val applicationScope = CoroutineScope(SupervisorJob() + dispatcher)

    val batches by lazy { ComponentBatchRunner(controlStatuses ?: components, applicationScope, PackageControlActivity()) }

    val list by lazy {
        ComponentListViewModel(
            selectedUsers = selectedUsers,
            componentStatuses = components,
            serviceControl = services ?: serviceControl,
            appControl = appControl,
            channel = channel,
            componentDetails = details,
            generalRules = rules,
            userDataRepository = userData,
            analyticsHelper = analytics,
            filesDir = File("/files"),
            ruleBaseFolder = "rules",
            cpuDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            packageName = PACKAGE,
            userId = userId,
            tab = tab,
            searchKeyword = searchKeyword,
            savedStateHandle = savedStateHandle,
        )
    }

    val control by lazy {
        ComponentControlViewModel(
            selectedUsers = selectedUsers,
            componentStatuses = controlStatuses ?: components,
            batches = batches,
            controlComponent = ControlComponentUseCase(controlStatuses ?: components, GetControlLayersUseCase(userData)),
            layersFor = GetControlLayersUseCase(userData),
            analyticsHelper = analytics,
            packageName = PACKAGE,
            userId = userId,
        )
    }

    /** Caches [statuses] as the components of the screen's app. */
    fun sendComponents(vararg statuses: ComponentStatus) {
        components.sendComponents(user, statuses.toList())
    }

    fun sendDescriptions(vararg details: Pair<String, String>) {
        this.details.sendComponentDetail(details.map { (name, description) -> ComponentDetail(name = id(name).className, description = description) })
    }

    /** The rows the list shows, or fails while it loads. */
    fun loaded(): AppComponents = (list.uiState.value.components as ComponentsUiState.Loaded).components
}
