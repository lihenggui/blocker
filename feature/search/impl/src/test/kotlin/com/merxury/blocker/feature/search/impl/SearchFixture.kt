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

package com.merxury.blocker.feature.search.impl

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.domain.SearchGeneralRuleUseCase
import com.merxury.blocker.core.domain.applist.CountServicesUseCase
import com.merxury.blocker.core.domain.applist.PerformAppActionUseCase
import com.merxury.blocker.core.domain.applist.ReadRunningPackagesUseCase
import com.merxury.blocker.core.domain.control.ComponentBatchRunner
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
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.testing.controller.FakeAppControl
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.controller.FakeServiceControl
import com.merxury.blocker.core.testing.repository.TestGeneralRuleRepository
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.io.File
import kotlin.time.Instant

internal val OWNER = UserId(0)
internal val WORK = UserId(10)

/** The package name the view model is told Blocker has. */
internal const val SELF_PACKAGE = "com.merxury.blocker.test"

internal val ownerUser = DeviceUser(OWNER, name = "Owner", kind = UserKind.OWNER, parent = null, running = true)
internal val workUser = DeviceUser(WORK, name = "Work profile", kind = UserKind.WORK_PROFILE, parent = OWNER, running = true)

internal fun installedApp(
    packageName: String,
    user: UserId = OWNER,
    label: String = packageName,
    isSystem: Boolean = false,
) = InstalledApp(
    key = AppKey(packageName, user),
    uid = 10_000 + user.value * 100_000,
    label = label,
    versionName = "1.0",
    versionCode = 1,
    isSystem = isSystem,
    enabled = true,
    enabledSetting = AppEnabledSetting.DEFAULT,
    apkPath = "/data/app/$packageName/base.apk",
    dataDir = "/data/user/${user.value}/$packageName",
    firstInstallTime = Instant.fromEpochMilliseconds(1_000),
    lastUpdateTime = Instant.fromEpochMilliseconds(2_000),
    minSdk = 21,
    targetSdk = 34,
)

internal fun id(packageName: String, name: String) = ComponentId(packageName, "$packageName.$name")

internal fun component(
    packageName: String,
    name: String,
    type: ComponentType = ComponentType.RECEIVER,
    pm: PmSetting = PmSetting.DEFAULT,
    ifw: IfwBlock = IfwBlock.NOT_BLOCKED,
    enabledInManifest: Boolean = true,
) = ComponentStatus(
    declaration = ComponentDeclaration(id(packageName, name), type, exported = false, enabledInManifest = enabledInManifest),
    state = ControlState(pm, ifw),
)

internal fun rule(id: Int, name: String, matchedAppCount: Int) = GeneralRule(id = id, name = name, matchedAppCount = matchedAppCount)

/** Sends each control request through [delegate] once [gate] completes, as a slow device would. */
internal class GatedControl(private val delegate: FakeComponentStatusRepository) : ComponentStatusRepository by delegate {
    var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)

    override fun control(request: ControlRequest): Flow<ControlEvent> = flow {
        gate.await()
        emitAll(delegate.control(request))
    }
}

/** The fakes behind a [SearchViewModel], with root ready unless set otherwise. */
internal class SearchFixture(dispatcher: CoroutineDispatcher) {
    val selectedUsers = FakeSelectedUserRepository(OWNER)
    val installedApps = FakeInstalledAppRepository()
    val components = FakeComponentStatusRepository()
    val appControl = FakeAppControl()
    val serviceControl = FakeServiceControl()
    val channel = FakePrivilegedChannel(ChannelState.Ready(PrivilegedIdentity.ROOT))
    val rules = TestGeneralRuleRepository().apply { sendRuleList(emptyList()) }
    val userData = TestUserDataRepository().apply { sendUserData(defaultUserData) }
    val analytics = TestAnalyticsHelper()

    /** The application's scope, which batches and indexing run in; it outlives every view model. */
    val applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    val control = GatedControl(components)
    val batches = ComponentBatchRunner(control, applicationScope)
    val index = ComponentIndex(components, channel, applicationScope)

    val viewModel = SearchViewModel(
        selectedUsers = selectedUsers,
        installedApps = installedApps,
        componentStatuses = components,
        componentIndex = index,
        batches = batches,
        layersFor = GetControlLayersUseCase(userData),
        searchRules = SearchGeneralRuleUseCase(rules, userData, File("/files"), "rules"),
        performAppAction = PerformAppActionUseCase(installedApps, appControl, channel),
        readRunningPackages = ReadRunningPackagesUseCase(appControl, channel),
        countAppServices = CountServicesUseCase(components, serviceControl, channel),
        channel = channel,
        userDataRepository = userData,
        analyticsHelper = analytics,
        cpuDispatcher = dispatcher,
        appPackageName = SELF_PACKAGE,
    )

    /** The results shown, or fails when none are loaded. */
    fun loaded(): SearchResults.Loaded = viewModel.uiState.value.results as SearchResults.Loaded
}
