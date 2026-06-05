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

package com.merxury.blocker.core.controllers.root.api

import android.content.pm.PackageManager
import com.merxury.blocker.core.controllers.IAppController
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RootApiAppController @Inject constructor(
    private val rootApiClient: RootApiClient,
) : IAppController {
    private val runningPackages = mutableSetOf<String>()

    override suspend fun init() = rootApiClient.ensureAvailable()

    override suspend fun disable(packageName: String): Boolean = rootApiClient.execute(
        SetApplicationEnabledSettingCommand(
            packageName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        ),
    ).value

    override suspend fun enable(packageName: String): Boolean = rootApiClient.execute(
        SetApplicationEnabledSettingCommand(
            packageName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        ),
    ).value

    override suspend fun forceStop(packageName: String): Boolean = rootApiClient.execute(ForceStopCommand(packageName)).value

    override suspend fun clearCache(packageName: String): Boolean = rootApiClient.execute(ClearCacheCommand(packageName)).value

    override suspend fun clearData(packageName: String): Boolean = rootApiClient.execute(ClearDataCommand(packageName)).value

    override suspend fun uninstallApp(packageName: String, versionCode: Long): Boolean = rootApiClient.execute(UninstallAppCommand(packageName, versionCode)).value

    override fun isAppRunning(packageName: String): Boolean = packageName in runningPackages

    override suspend fun refreshRunningAppList() {
        Timber.d("Refresh running app list")
        runningPackages.clear()
        runningPackages.addAll(rootApiClient.execute(RefreshRunningAppListCommand()).value)
    }
}
