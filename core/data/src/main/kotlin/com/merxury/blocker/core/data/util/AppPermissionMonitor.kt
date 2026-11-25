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

import com.merxury.blocker.core.controllers.shizuku.IShizukuInitializer
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.privileged.PrivilegedChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject
import javax.inject.Singleton

/** Requests access when a screen first observes [permissionStatus]. Background work uses the
 * channel's separate connection check, which only uses a previously granted permission.
 */
@Singleton
class AppPermissionMonitor @Inject constructor(
    userDataRepository: UserDataRepository,
    private val shizukuInitializer: IShizukuInitializer,
    private val channel: PrivilegedChannel,
    @ApplicationScope appScope: CoroutineScope,
) : PermissionMonitor {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val permissionStatus: Flow<PermissionStatus> = userDataRepository.userData
        .map { it.controllerType }
        .distinctUntilChanged()
        .transformLatest { type ->
            var state = channel.connect()
            while (type == SHIZUKU && state == ChannelState.Unavailable(UnavailableReason.NOT_RUNNING)) {
                emit(PermissionStatus.NO_PERMISSION)
                shizukuInitializer.awaitBinder()
                state = channel.connect()
            }
            emitAll(channel.state.map { it.toPermissionStatus() })
        }
        .distinctUntilChanged()
        .shareIn(appScope, SharingStarted.Lazily, replay = 1)

    private fun ChannelState.toPermissionStatus(): PermissionStatus = when (this) {
        is ChannelState.Unavailable -> PermissionStatus.NO_PERMISSION

        is ChannelState.Ready -> when (identity) {
            PrivilegedIdentity.ROOT -> PermissionStatus.ROOT_USER
            PrivilegedIdentity.SYSTEM -> PermissionStatus.SYSTEM_USER
            PrivilegedIdentity.SHELL -> PermissionStatus.SHELL_USER
        }
    }
}
