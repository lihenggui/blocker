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

package com.merxury.blocker.feature.applist.impl

import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.domain.applist.CountServicesUseCase
import com.merxury.blocker.core.domain.applist.PerformAppActionUseCase
import com.merxury.blocker.core.domain.applist.ReadRunningPackagesUseCase
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.testing.controller.FakeAppControl
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.controller.FakeServiceControl
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.device.FakeDeviceUserRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.util.TestAnalyticsHelper
import com.merxury.blocker.core.ui.applist.AppRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.time.Instant

internal val OWNER = UserId(0)

/** The package name the view model is told Blocker has. */
internal const val SELF_PACKAGE = "com.merxury.blocker.test"
internal val WORK = UserId(10)

internal val ownerUser = DeviceUser(OWNER, name = "Owner", kind = UserKind.OWNER, parent = null, running = true)
internal val workUser = DeviceUser(WORK, name = "Work profile", kind = UserKind.WORK_PROFILE, parent = OWNER, running = true)

internal fun installedApp(
    packageName: String,
    user: UserId = OWNER,
    label: String = packageName,
    isSystem: Boolean = false,
    enabledSetting: AppEnabledSetting = AppEnabledSetting.DEFAULT,
    enabled: Boolean = !enabledSetting.isDisabled,
    firstInstallTime: Long = 1_000,
    lastUpdateTime: Long = 2_000,
) = InstalledApp(
    key = AppKey(packageName, user),
    uid = 10_000 + user.value * 100_000,
    label = label,
    versionName = "1.0",
    versionCode = 1,
    isSystem = isSystem,
    enabled = enabled,
    enabledSetting = enabledSetting,
    apkPath = "/data/app/$packageName/base.apk",
    dataDir = "/data/user/${user.value}/$packageName",
    firstInstallTime = Instant.fromEpochMilliseconds(firstInstallTime),
    lastUpdateTime = Instant.fromEpochMilliseconds(lastUpdateTime),
    minSdk = 21,
    targetSdk = 34,
)

internal fun service(
    packageName: String,
    name: String,
    pm: PmSetting = PmSetting.DEFAULT,
    ifw: IfwBlock = IfwBlock.NOT_BLOCKED,
    enabledInManifest: Boolean = true,
) = ComponentStatus(
    declaration = ComponentDeclaration(
        id = ComponentId(packageName, "$packageName.$name"),
        type = ComponentType.SERVICE,
        exported = false,
        enabledInManifest = enabledInManifest,
    ),
    state = ControlState(pm, ifw),
)

/** The fakes behind an [AppListViewModel], with Shizuku ready as root unless set otherwise. */
internal class AppListFixture(private val dispatcher: CoroutineDispatcher) {
    val selectedUsers = FakeSelectedUserRepository(OWNER)
    val deviceUsers = FakeDeviceUserRepository()
    val installedApps = FakeInstalledAppRepository()
    val components = FakeComponentStatusRepository()
    val appControl = FakeAppControl()
    val serviceControl = FakeServiceControl()
    val channel = FakePrivilegedChannel(ChannelState.Ready(PrivilegedIdentity.ROOT))
    val userData = TestUserDataRepository().apply { sendUserData(defaultUserData) }
    val analytics = TestAnalyticsHelper()

    val viewModel by lazy { createViewModel(selectedUsers) }

    fun createViewModel(selectedUsers: SelectedUserRepository) = AppListViewModel(
        selectedUsers = selectedUsers,
        deviceUsers = deviceUsers,
        installedApps = installedApps,
        performAppAction = PerformAppActionUseCase(installedApps, appControl, channel),
        readRunningPackages = ReadRunningPackagesUseCase(appControl, channel),
        countAppServices = CountServicesUseCase(components, serviceControl, channel),
        channel = channel,
        userDataRepository = userData,
        analyticsHelper = analytics,
        cpuDispatcher = dispatcher,
        appPackageName = SELF_PACKAGE,
    )

    /** The rows shown, or fails when the list is not loaded. */
    fun rows(): List<AppRow> = (viewModel.uiState.value.apps as AppsUiState.Loaded).apps
}
