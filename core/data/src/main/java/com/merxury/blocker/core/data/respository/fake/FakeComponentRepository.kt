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

package com.merxury.blocker.core.data.respository.fake

import com.merxury.blocker.core.controllers.ifw.IfwController
import com.merxury.blocker.core.controllers.root.RootController
import com.merxury.blocker.core.controllers.shizuku.ShizukuController
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.component.LocalComponentDataSource
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.database.app.AppComponentDao
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.result.Result.Success
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class FakeComponentRepository@Inject constructor(
) : ComponentRepository {
    override fun getComponent(name: String): Flow<ComponentInfo?> = flowOf()

    override fun getComponentList(packageName: String): Flow<List<ComponentInfo>> = flowOf(emptyList())

    override fun getComponentList(
        packageName: String,
        type: ComponentType,
    ): Flow<List<ComponentInfo>> = flowOf(emptyList())

    override fun updateComponentList(packageName: String, type: ComponentType): Flow<Result<Unit>> = flowOf(
        Success(Unit))

    override fun updateComponentList(packageName: String): Flow<Result<Unit>> = flowOf(
        Success(Unit))

    override fun controlComponent(
        packageName: String,
        componentName: String,
        newState: Boolean,
    ): Flow<Boolean> = flowOf(false)

    override fun batchControlComponent(
        components: List<ComponentInfo>,
        newState: Boolean,
    ): Flow<Int> = flowOf(0)

    override fun searchComponent(keyword: String): Flow<List<ComponentInfo>> = flowOf(emptyList())

    override suspend fun saveComponents(components: List<ComponentInfo>) { /* no-op */ }

    override suspend fun deleteComponents(packageName: String) { /* no-op */ }
}