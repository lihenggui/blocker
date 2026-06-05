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
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RootApiServiceController @Inject constructor(
    private val rootApiClient: RootApiClient,
) : IServiceController {
    private val runningServices = mutableListOf<RunningServiceState>()

    override suspend fun init() = rootApiClient.ensureAvailable()

    override suspend fun load(): Boolean {
        runningServices.clear()
        return try {
            runningServices.addAll(rootApiClient.execute(RefreshRunningServiceListCommand()).services)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh running service list")
            false
        }
    }

    override fun isServiceRunning(packageName: String, serviceName: String): Boolean = runningServices.any {
        it.packageName == packageName &&
            it.className == serviceName &&
            it.started
    }

    override suspend fun stopService(packageName: String, serviceName: String): Boolean = rootApiClient.execute(StopServiceCommand(packageName, serviceName)).value

    override suspend fun startService(packageName: String, serviceName: String): Boolean = rootApiClient.execute(StartServiceCommand(packageName, serviceName)).value
}
