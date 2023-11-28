/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.core.data.appstate

import android.content.pm.PackageManager
import com.merxury.blocker.core.controllers.ifw.IfwController
import com.merxury.blocker.core.controllers.root.RootController
import com.merxury.blocker.core.controllers.root.RootServiceController
import com.merxury.blocker.core.controllers.shizuku.ShizukuServiceController
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.PermissionUtils
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AppStateCache @Inject constructor(
    private val userDataRepository: UserDataRepository,
    private val packageManager: PackageManager,
    private val ifwController: IfwController,
    private val rootController: RootController,
    private val rootServiceController: RootServiceController,
    private val shizukuServiceController: ShizukuServiceController,
) : IAppStateCache {
    private val cache = mutableMapOf<String, AppState>()

    override fun getOrNull(packageName: String): AppState? {
        return cache[packageName]
    }

    override suspend fun get(packageName: String): AppState {
        val cachedResult = cache[packageName]
        val result: AppState
        if (cachedResult == null) {
            result = getServiceStatus(packageName)
            cache[packageName] = result
        } else {
            result = cachedResult
        }
        return result
    }

    private suspend fun getServiceStatus(packageName: String): AppState {
        val controllerType = userDataRepository.userData.first().controllerType
        val services = ApplicationUtil.getServiceList(packageManager, packageName)
        var running = 0
        var blocked = 0
        for (service in services) {
            val ifwState = if (PermissionUtils.isRootAvailable()) {
                ifwController.checkComponentEnableState(packageName, service.name)
            } else {
                true
            }
            val pmState = rootController.checkComponentEnableState(packageName, service.name)
            if (!ifwState || !pmState) {
                blocked++
            }
            val currentServiceController = if (controllerType == SHIZUKU) {
                shizukuServiceController
            } else {
                rootServiceController
            }
            if (currentServiceController.isServiceRunning(packageName, service.name)) {
                running++
            }
        }
        return AppState(
            running,
            blocked,
            services.count(),
            packageName,
        )
    }
}
