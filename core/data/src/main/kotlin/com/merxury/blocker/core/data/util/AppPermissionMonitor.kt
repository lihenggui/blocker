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

package com.merxury.blocker.core.data.util

import com.merxury.blocker.core.controllers.IAppController
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.IServiceController
import com.merxury.blocker.core.controllers.di.RootApiAppControl
import com.merxury.blocker.core.controllers.di.RootApiControl
import com.merxury.blocker.core.controllers.di.RootApiServiceControl
import com.merxury.blocker.core.controllers.shizuku.IShizukuInitializer
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.data.util.PermissionStatus.NO_PERMISSION
import com.merxury.blocker.core.data.util.PermissionStatus.ROOT_USER
import com.merxury.blocker.core.data.util.PermissionStatus.SHELL_USER
import com.merxury.blocker.core.data.util.PermissionStatus.SYSTEM_USER
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val SHELL_UID = 2000
private const val SYSTEM_UID = 1000
private const val ROOT_UID = 0

@Singleton
class AppPermissionMonitor @Inject constructor(
    userDataRepository: UserDataRepository,
    private val shizukuInitializer: IShizukuInitializer,
    @RootApiControl private val rootApiController: IController,
    @RootApiAppControl private val rootApiAppController: IAppController,
    @RootApiServiceControl private val rootApiServiceController: IServiceController,
    @ApplicationScope private val appScope: CoroutineScope,
) : PermissionMonitor {
    private val controllerStatus = mutableMapOf<ControllerType, PermissionStatus>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val permissionStatus: Flow<PermissionStatus> = userDataRepository.userData
        .map { it.controllerType }
        .distinctUntilChanged()
        // A new controller type cancels an unfinished initialization, such as a Shizuku
        // permission dialog, so its status is never held back by the previous type.
        .transformLatest { type ->
            // In IFW mode, we will still use root apis
            // Make sure that we initialize once
            val controllerType = if (type == SHIZUKU) SHIZUKU else PM
            // If Shizuku is not running, report that, then register again once the user starts
            // it. NotRunning only comes after the binder wait has timed out, so a Shizuku that
            // keeps restarting cannot spin this loop.
            while (initController(type) == RegisterShizukuResult.NotRunning) {
                emit(NO_PERMISSION)
                shizukuInitializer.awaitBinder()
            }
            emit(controllerStatus[controllerType] ?: NO_PERMISSION)
        }
        .shareIn(
            scope = appScope,
            started = SharingStarted.Lazily,
            replay = 1,
        )

    /** Returns the Shizuku registration result, or null if no registration was needed. */
    private suspend fun initController(type: ControllerType): RegisterShizukuResult? {
        Timber.d("Initialize controller: $type")
        if (type == SHIZUKU) {
            when (controllerStatus[SHIZUKU]) {
                ROOT_USER,
                SYSTEM_USER,
                SHELL_USER,
                -> {
                    Timber.i("No need to re-initialize shizuku controller")
                    return null
                }

                NO_PERMISSION,
                null,
                -> Unit
            }
            if (!shizukuInitializer.hasPermission()) {
                val result = shizukuInitializer.registerShizuku()
                Timber.i("Shizuku register result: $result")
                if (result is RegisterShizukuResult.Granted) {
                    updatePermissionStatusFromUid(result.uid)
                } else {
                    controllerStatus[SHIZUKU] = NO_PERMISSION
                }
                return result
            } else {
                val uid = shizukuInitializer.getUid()
                Timber.d("Shizuku permission granted: true, uid: $uid")
                updatePermissionStatusFromUid(uid)
            }
        } else {
            val apiPermissionStatus = controllerStatus[PM]
            if (apiPermissionStatus == ROOT_USER) {
                Timber.w("No need to re-initialize root api controller")
                return null
            }
            try {
                rootApiController.init()
                rootApiAppController.init()
                rootApiServiceController.init()
                controllerStatus[PM] = ROOT_USER
                Timber.d("Root api controller initialized")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Cannot initialize root api controller")
                controllerStatus[PM] = NO_PERMISSION
            }
        }
        return null
    }

    private fun updatePermissionStatusFromUid(uid: Int) {
        when (uid) {
            ROOT_UID -> controllerStatus[SHIZUKU] = ROOT_USER
            SYSTEM_UID -> controllerStatus[SHIZUKU] = SYSTEM_USER
            SHELL_UID -> controllerStatus[SHIZUKU] = SHELL_USER
            else -> controllerStatus[SHIZUKU] = NO_PERMISSION
        }
    }
}
