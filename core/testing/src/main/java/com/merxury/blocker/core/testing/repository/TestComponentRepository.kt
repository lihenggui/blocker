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

package com.merxury.blocker.core.testing.repository

import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.result.Result
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class TestComponentRepository : ComponentRepository {

    private val componentList: MutableSharedFlow<List<ComponentInfo>> =
        MutableSharedFlow(replay = 1, onBufferOverflow = DROP_OLDEST)
    override fun getComponent(name: String): Flow<ComponentInfo?> {
        return componentList.map {
            it.find { componentInfo -> componentInfo.name == name }
        }
    }

    override fun getComponentList(packageName: String): Flow<List<ComponentInfo>> {
        return componentList.map {
            it.filter { componentInfo -> componentInfo.packageName == packageName }
        }
    }

    override fun getComponentList(
        packageName: String,
        type: ComponentType,
    ): Flow<List<ComponentInfo>> {
        return componentList.map {
            it.filter { componentInfo -> componentInfo.packageName == packageName && componentInfo.type == type }
        }
    }

    override fun updateComponentList(packageName: String, type: ComponentType): Flow<Result<Unit>> {
        return flowOf(Result.Success(Unit))
    }

    override fun updateComponentList(packageName: String): Flow<Result<Unit>> {
        return flowOf(Result.Success(Unit))
    }

    override fun controlComponent(
        packageName: String,
        componentName: String,
        newState: Boolean,
    ): Flow<Boolean> = flowOf(true)

    override fun batchControlComponent(
        components: List<ComponentInfo>,
        newState: Boolean,
    ): Flow<Int> = flowOf(components.size)

    override fun searchComponent(keyword: String): Flow<List<ComponentInfo>> {
        return componentList.map {
            it.filter { componentInfo -> componentInfo.name.contains(keyword) }
        }
    }

    override suspend fun saveComponents(components: List<ComponentInfo>) {
        componentList.emit(components)
    }

    override suspend fun deleteComponents(packageName: String) {
        componentList.emit(emptyList())
    }

    fun sendComponentList(componentList: List<ComponentInfo>) {
        this.componentList.tryEmit(componentList)
    }
}
