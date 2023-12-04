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

package com.merxury.blocker.core.domain.permissionmonitor

import android.content.Context
import com.merxury.blocker.core.controllers.IAppController
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.IServiceController
import com.merxury.blocker.core.controllers.di.RootApiAppControl
import com.merxury.blocker.core.controllers.di.RootApiControl
import com.merxury.blocker.core.controllers.di.RootApiServiceControl
import com.merxury.blocker.core.controllers.shizuku.ShizukuInitializer
import com.merxury.blocker.core.controllers.util.PermissionMonitor
import com.merxury.blocker.core.controllers.util.PermissionStatus
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.data.ControllerType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

class AppPermissionMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userDataRepository: UserDataRepository,
    private val shizukuInitializer: ShizukuInitializer,
    @RootApiControl private val rootApiController: IController,
    @RootApiAppControl private val rootApiAppController: IAppController,
    @RootApiServiceControl private val rootApiServiceController: IServiceController,
) : PermissionMonitor {
    private val controllerInitStatus = mutableMapOf<ControllerType, PermissionStatus>()
    override val permissionStatus: Flow<PermissionStatus> = userDataRepository.userData
        .map { it.controllerType }
        .distinctUntilChanged()
        .transform { type ->
        }

    private suspend fun initController(type: ControllerType) {
        if (type == ControllerType.SHIZUKU) {
            if (!shizukuInitializer.hasPermission()) {
                shizukuInitializer.registerShizuku()
            } else {
            }
        } else {
            rootApiController.init()
            rootApiAppController.init()
            rootApiServiceController.init()
        }
    }
}
