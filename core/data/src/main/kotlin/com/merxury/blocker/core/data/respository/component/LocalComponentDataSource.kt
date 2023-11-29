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

import android.content.pm.PackageManager
import com.merxury.blocker.core.controllers.ifw.IfwController
import com.merxury.blocker.core.controllers.root.command.RootController
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.getSimpleName
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.utils.ApplicationUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class LocalComponentDataSource @Inject constructor(
    private val pm: PackageManager,
    private val ifwController: IfwController,
    private val pmController: RootController,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ComponentDataSource {

    override fun getComponent(packageName: String, componentName: String): Flow<ComponentInfo?> {
        return flow {
            val list = ApplicationUtil.getApplicationComponents(pm, packageName, ioDispatcher)
            val component = list.activities?.find { it.name == componentName }
                ?.let { toComponentInfo(it, ACTIVITY, packageName) }
                ?: list.services?.find { it.name == componentName }
                    ?.let { toComponentInfo(it, SERVICE, packageName) }
                ?: list.receivers?.find { it.name == componentName }
                    ?.let { toComponentInfo(it, RECEIVER, packageName) }
                ?: list.providers?.find { it.name == componentName }
                    ?.let { toComponentInfo(it, PROVIDER, packageName) }
            emit(component)
        }
            .flowOn(ioDispatcher)
    }

    override fun getComponentList(
        packageName: String,
        type: ComponentType,
    ): Flow<List<ComponentInfo>> = flow {
        val list = when (type) {
            RECEIVER -> ApplicationUtil.getReceiverList(pm, packageName)
            SERVICE -> ApplicationUtil.getServiceList(pm, packageName)
            ACTIVITY -> ApplicationUtil.getActivityList(pm, packageName)
            PROVIDER -> ApplicationUtil.getProviderList(pm, packageName)
        }
        emit(
            list.map {
                toComponentInfo(it, type, packageName)
            },
        )
    }
        .flowOn(ioDispatcher)

    override fun getComponentList(packageName: String): Flow<List<ComponentInfo>> = flow {
        val packageInfo = ApplicationUtil.getApplicationComponents(pm, packageName, ioDispatcher)
        val activity = packageInfo.activities
            ?.mapNotNull { toComponentInfo(it, ACTIVITY, packageName) }
            ?: emptyList()
        val service = packageInfo.services
            ?.mapNotNull { toComponentInfo(it, SERVICE, packageName) }
            ?: emptyList()
        val receiver = packageInfo.receivers
            ?.mapNotNull { toComponentInfo(it, RECEIVER, packageName) }
            ?: emptyList()
        val provider = packageInfo.providers
            ?.mapNotNull { toComponentInfo(it, PROVIDER, packageName) }
            ?: emptyList()
        emit(activity + service + receiver + provider)
    }
        .flowOn(ioDispatcher)

    override fun getComponentType(
        packageName: String,
        componentName: String,
    ): Flow<ComponentType?> =
        flow {
            val components = ApplicationUtil.getApplicationComponents(pm, packageName, ioDispatcher)
            val isProvider = components.providers?.any { it.name == componentName } ?: false
            if (isProvider) {
                emit(PROVIDER)
                return@flow
            }
            val isReceiver = components.receivers?.any { it.name == componentName } ?: false
            if (isReceiver) {
                emit(RECEIVER)
                return@flow
            }
            val isService = components.services?.any { it.name == componentName } ?: false
            if (isService) {
                emit(SERVICE)
                return@flow
            }
            val isActivity = components.activities?.any { it.name == componentName } ?: false
            if (isActivity) {
                emit(ACTIVITY)
                return@flow
            }
            // If not found, emit null
            emit(null)
        }
            .flowOn(ioDispatcher)

    private suspend fun toComponentInfo(
        info: android.content.pm.ComponentInfo,
        type: ComponentType,
        packageName: String,
    ) = ComponentInfo(
        name = info.name,
        simpleName = info.getSimpleName(),
        packageName = info.packageName,
        type = type,
        exported = info.exported,
        pmBlocked = !pmController.checkComponentEnableState(packageName, info.name),
        ifwBlocked = !ifwController.checkComponentEnableState(packageName, info.name),
    )
}
