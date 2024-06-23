/*
 * Copyright 2024 Blocker
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
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.IServiceController
import com.merxury.blocker.core.controllers.di.IfwControl
import com.merxury.blocker.core.controllers.di.RootApiControl
import com.merxury.blocker.core.controllers.di.RootApiServiceControl
import com.merxury.blocker.core.controllers.di.ShizukuServiceControl
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.AppServiceStatus
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.PermissionUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import javax.inject.Inject

internal class AppStateCache @Inject constructor(
    private val packageManager: PackageManager,
    private val userDataRepository: UserDataRepository,
    @IfwControl private val ifwController: IController,
    @RootApiControl private val rootController: IController,
    @RootApiServiceControl private val rootServiceController: IServiceController,
    @ShizukuServiceControl private val shizukuServiceController: IServiceController,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : IAppStateCache {
    private val cache = mutableMapOf<String, AppServiceStatus>()

    override fun getOrNull(packageName: String): AppServiceStatus? = cache[packageName]

    override suspend fun get(packageName: String): AppServiceStatus {
        val cachedResult = cache[packageName]
        val result: AppServiceStatus
        if (cachedResult == null) {
            result = getServiceStatus(packageName)
            cache[packageName] = result
        } else {
            result = cachedResult
        }
        return result
    }

    private suspend fun getServiceStatus(packageName: String): AppServiceStatus {
        val controllerType = userDataRepository.userData.first().controllerType
        val services = ApplicationUtil.getServiceList(packageManager, packageName)
        var running = 0
        var blocked = 0
        for (service in services) {
            val ifwState = if (PermissionUtils.isRootAvailable(ioDispatcher)) {
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
        return AppServiceStatus(
            packageName = packageName,
            running = running,
            blocked = blocked,
            total = services.count(),
        )
    }
}
