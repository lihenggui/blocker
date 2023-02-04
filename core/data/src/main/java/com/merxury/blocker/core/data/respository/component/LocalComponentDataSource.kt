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
import com.merxury.blocker.core.controllers.root.RootController
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
                ComponentInfo(
                    name = it.name,
                    simpleName = it.getSimpleName(),
                    packageName = it.packageName,
                    type = type,
                    pmBlocked = !pmController.checkComponentEnableState(packageName, it.name),
                    ifwBlocked = !ifwController.checkComponentEnableState(packageName, it.name),
                )
            },
        )
    }
        .flowOn(ioDispatcher)
}
