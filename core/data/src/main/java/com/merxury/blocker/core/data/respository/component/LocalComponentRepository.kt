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

package com.merxury.blocker.core.data.respository.component

import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.ifw.IfwController
import com.merxury.blocker.core.controllers.root.RootController
import com.merxury.blocker.core.controllers.shizuku.ShizukuController
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject

class LocalComponentRepository @Inject constructor(
    private val localDataSource: LocalComponentDataSource,
    private val userDataRepository: UserDataRepository,
    private val pmController: RootController,
    private val ifwController: IfwController,
    private val shizukuController: ShizukuController,
) : ComponentRepository {
    override fun getComponentList(
        packageName: String,
        type: ComponentType,
    ): Flow<List<ComponentInfo>> = localDataSource.getComponentList(packageName, type)

    override fun controlComponent(
        packageName: String,
        componentName: String,
        enabled: Boolean,
    ): Flow<Boolean> = flow {
        Timber.d("Control $packageName/$componentName to state $enabled")
        val controller = getController()
        val result = if (enabled) {
            controller.enable(packageName, componentName)
        } else {
            controller.disable(packageName, componentName)
        }
        emit(result)
    }

    private suspend fun getController(): IController {
        val userData = userDataRepository.userData.first()
        return when (userData.controllerType) {
            IFW -> ifwController
            PM -> pmController
            SHIZUKU -> shizukuController
        }
    }
}
