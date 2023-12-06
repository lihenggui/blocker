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

package com.merxury.blocker.core.data.util

import com.merxury.blocker.core.controllers.IAppController
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.IServiceController
import com.merxury.blocker.core.controllers.di.RootApiAppControl
import com.merxury.blocker.core.controllers.di.RootApiControl
import com.merxury.blocker.core.controllers.di.RootApiServiceControl
import com.merxury.blocker.core.controllers.shizuku.ShizukuInitializer
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.data.util.PermissionStatus.NO_PERMISSION
import com.merxury.blocker.core.data.util.PermissionStatus.ROOT_USER
import com.merxury.blocker.core.data.util.PermissionStatus.SHELL_USER
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val SHELL_UID = 2000
private const val ROOT_UID = 0

class AppPermissionMonitor @Inject constructor(
    userDataRepository: UserDataRepository,
    private val shizukuInitializer: ShizukuInitializer,
    @RootApiControl private val rootApiController: IController,
    @RootApiAppControl private val rootApiAppController: IAppController,
    @RootApiServiceControl private val rootApiServiceController: IServiceController,
) : PermissionMonitor {
    private val controllerStatus = mutableMapOf<ControllerType, PermissionStatus>()

    override val permissionStatus: Flow<PermissionStatus> = userDataRepository.userData
        .map { it.controllerType }
        .distinctUntilChanged()
        .transform { type ->
            initController(type)
            // In IFW mode, we will still use root apis
            // Make sure that we initialize once
            val controllerType = if (type == SHIZUKU) SHIZUKU else PM
            emit(controllerStatus[controllerType] ?: NO_PERMISSION)
        }

    private suspend fun initController(type: ControllerType) {
        Timber.d("Initialize controller: $type")
        if (type == SHIZUKU) {
            if (controllerStatus[SHIZUKU] != NO_PERMISSION) {
                Timber.i("No need to re-initialize shizuku controller")
                return
            }
            if (!shizukuInitializer.hasPermission()) {
                suspendCoroutine { cont ->
                    shizukuInitializer.registerShizuku { granted, uid ->
                        Timber.d("Shizuku permission granted: $granted, uid: $uid")
                        if (granted) {
                            updatePermissionStatusFromUid(uid)
                        } else {
                            controllerStatus[SHIZUKU] = NO_PERMISSION
                        }
                        cont.resume(Unit)
                    }
                }
            } else {
                val uid = shizukuInitializer.getUid()
                Timber.d("Shizuku permission granted: true, uid: $uid")
                updatePermissionStatusFromUid(uid)
            }
        } else {
            val apiPermissionStatus = controllerStatus[PM]
            if (apiPermissionStatus == ROOT_USER) {
                Timber.w("No need to re-initialize root api controller")
                return
            }
            try {
                rootApiController.init()
                rootApiAppController.init()
                rootApiServiceController.init()
                controllerStatus[PM] = ROOT_USER
            } catch (e: Exception) {
                Timber.e(e, "Cannot initialize root api controller")
                controllerStatus[PM] = NO_PERMISSION
            }
        }
    }

    private fun updatePermissionStatusFromUid(uid: Int) {
        when (uid) {
            ROOT_UID -> controllerStatus[SHIZUKU] = ROOT_USER
            SHELL_UID -> controllerStatus[SHIZUKU] = SHELL_USER
            else -> controllerStatus[SHIZUKU] = NO_PERMISSION
        }
    }
}
