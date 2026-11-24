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

package com.merxury.blocker.feature.ruledetail.impl

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.domain.control.ComponentBatchRunner
import com.merxury.blocker.core.domain.control.ControlComponentUseCase
import com.merxury.blocker.core.domain.control.GetControlLayersUseCase
import com.merxury.blocker.core.domain.rule.GeneralRuleMatcher
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

/** The rule the screen shows. */
internal val TRACKER = GeneralRule(id = 7, name = "Tracker", company = "Example", searchKeyword = listOf("com.tracker."), iconUrl = "icon/tracker.svg")

internal fun installedApp(packageName: String, user: UserId = OWNER, label: String = packageName, installedAt: Long = 1_000) = InstalledApp(
    key = AppKey(packageName, user),
    uid = 10_000 + user.value * 100_000,
    label = label,
    versionName = "1.0",
    versionCode = 1,
    isSystem = false,
    enabled = true,
    enabledSetting = AppEnabledSetting.DEFAULT,
    apkPath = "/data/app/$packageName/base.apk",
    dataDir = "/data/user/${user.value}/$packageName",
    firstInstallTime = Instant.fromEpochMilliseconds(installedAt),
    lastUpdateTime = Instant.fromEpochMilliseconds(installedAt),
    minSdk = 21,
    targetSdk = 34,
)

internal fun id(packageName: String, name: String) = ComponentId(packageName, "com.tracker.$name")

internal fun component(
    packageName: String,
    name: String,
    pm: PmSetting = PmSetting.DEFAULT,
    ifw: IfwBlock = IfwBlock.NOT_BLOCKED,
    enabledInManifest: Boolean = true,
) = ComponentStatus(
    declaration = ComponentDeclaration(id(packageName, name), ComponentType.RECEIVER, exported = false, enabledInManifest = enabledInManifest),
    state = ControlState(pm, ifw),
)

/** Sends each control request on once [gate] completes, as a slow device would. */
internal class GatedControl(private val delegate: FakeComponentStatusRepository) : ComponentStatusRepository by delegate {
    var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)

    override fun control(request: ControlRequest): Flow<ControlEvent> = flow {
        gate.await()
        emitAll(delegate.control(request))
    }
}

/** The fakes behind a [RuleDetailViewModel] showing [TRACKER], with root ready. */
internal class RuleDetailFixture(dispatcher: CoroutineDispatcher) {
    val selectedUsers = FakeSelectedUserRepository(OWNER)
    val installedApps = FakeInstalledAppRepository()
    val components = FakeComponentStatusRepository()
    val channel = FakePrivilegedChannel(ChannelState.Ready(PrivilegedIdentity.ROOT))
    val rules = TestGeneralRuleRepository().apply { sendRuleList(listOf(TRACKER)) }
    val userData = TestUserDataRepository().apply { sendUserData(defaultUserData) }
    val appControl = FakeAppControl()
    val serviceControl = FakeServiceControl()
    val analytics = TestAnalyticsHelper()

    /** The application's scope, which batches, indexing and matching run in. */
    val applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    val control = GatedControl(components)
    val batches = ComponentBatchRunner(control, applicationScope)
    val index = ComponentIndex(components, channel, applicationScope)
    private val matcher = GeneralRuleMatcher(components, installedApps, rules, userData, selectedUsers, SELF_PACKAGE, dispatcher, applicationScope)
    private val layers = GetControlLayersUseCase(userData)

    val viewModel = RuleDetailViewModel(
        selectedUsers = selectedUsers,
        ruleRepository = rules,
        ruleMatcher = matcher,
        installedApps = installedApps,
        componentIndex = index,
        batches = batches,
        controlComponent = ControlComponentUseCase(control, layers),
        layersFor = layers,
        appControl = appControl,
        serviceControl = serviceControl,
        channel = channel,
        userDataRepository = userData,
        analyticsHelper = analytics,
        filesDir = File("/files"),
        ruleBaseFolder = "rules",
        cpuDispatcher = dispatcher,
        ruleId = TRACKER.id.toString(),
        tab = "applicable_app",
    )

    /** The matched apps shown, or fails when they are not loaded. */
    fun apps(): List<MatchedApp> = ((viewModel.uiState.value.ruleInfo as RuleInfoUiState.Success).matchedAppsUiState as MatchedAppsUiState.Loaded).apps
}
