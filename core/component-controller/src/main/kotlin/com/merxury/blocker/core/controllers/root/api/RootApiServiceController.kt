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

import com.merxury.blocker.core.controllers.IServiceController
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RootApiServiceController @Inject constructor(
    private val rootServiceConnection: RootServiceConnection,
) : IServiceController {

    override suspend fun init() = rootServiceConnection.ensureConnected()

    override suspend fun load(): Boolean {
        val rootService = rootServiceConnection.rootService ?: return false
        rootService.refreshRunningServiceList()
        return true
    }

    override fun isServiceRunning(packageName: String, serviceName: String): Boolean {
        val rootService = rootServiceConnection.rootService ?: return false
        return rootService.isServiceRunning(packageName, serviceName)
    }

    override suspend fun stopService(packageName: String, serviceName: String): Boolean {
        val rootService = rootServiceConnection.rootService
        if (rootService == null) {
            Timber.w("Cannot stop service, rootService is null")
            return false
        }
        return rootService.stopService(packageName, serviceName)
    }

    override suspend fun startService(packageName: String, serviceName: String): Boolean {
        val rootService = rootServiceConnection.rootService
        if (rootService == null) {
            Timber.w("Cannot start service, rootService is null")
            return false
        }
        return rootService.startService(packageName, serviceName)
    }
}
