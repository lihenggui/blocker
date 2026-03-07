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
    private val rootServiceConnection: RootServiceConnection,
) : IAppController {

    override suspend fun init() = rootServiceConnection.ensureConnected()

    override suspend fun disable(packageName: String): Boolean {
        val rootService = rootServiceConnection.rootService
        if (rootService == null) {
            Timber.w("Cannot disable app: root server is not initialized")
            return false
        }
        rootService.setApplicationEnabledSetting(
            packageName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        )
        return true
    }

    override suspend fun enable(packageName: String): Boolean {
        val rootService = rootServiceConnection.rootService
        if (rootService == null) {
            Timber.w("Cannot enable app: root server is not initialized")
            return false
        }
        rootService.setApplicationEnabledSetting(
            packageName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )
        return true
    }

    override suspend fun forceStop(packageName: String): Boolean {
        val rootService = rootServiceConnection.rootService
        if (rootService == null) {
            Timber.w("Cannot force stop app: root server is not initialized")
            return false
        }
        rootService.forceStop(packageName)
        return true
    }

    override suspend fun clearCache(packageName: String): Boolean {
        val rootService = rootServiceConnection.rootService
        if (rootService == null) {
            Timber.w("Cannot clear cache: root server is not initialized")
            return false
        }
        rootService.clearCache(packageName)
        return true
    }

    override suspend fun clearData(packageName: String): Boolean {
        val rootService = rootServiceConnection.rootService
        if (rootService == null) {
            Timber.w("Cannot clear data: root server is not initialized")
            return false
        }
        rootService.clearData(packageName)
        return true
    }

    override suspend fun uninstallApp(packageName: String, versionCode: Long): Boolean {
        val rootService = rootServiceConnection.rootService
        if (rootService == null) {
            Timber.w("Cannot uninstall app: root server is not initialized")
            return false
        }
        rootService.uninstallApp(packageName, versionCode)
        return true
    }

    override fun isAppRunning(packageName: String): Boolean {
        val rootService = rootServiceConnection.rootService ?: return false
        return rootService.isAppRunning(packageName)
    }

    override suspend fun refreshRunningAppList() {
        Timber.d("Refresh running app list")
        rootServiceConnection.rootService?.refreshRunningAppList()
    }
}
