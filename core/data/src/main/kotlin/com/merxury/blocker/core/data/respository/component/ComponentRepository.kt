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

package com.merxury.blocker.core.data.respository.component

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.result.Result
import kotlinx.coroutines.flow.Flow

interface ComponentRepository {

    fun getComponent(name: String): Flow<ComponentInfo?>

    fun getComponentList(packageName: String): Flow<List<ComponentInfo>>

    fun getComponentList(packageName: String, type: ComponentType): Flow<List<ComponentInfo>>

    fun updateComponentList(packageName: String, type: ComponentType): Flow<Result<Unit>>

    fun updateComponentList(packageName: String): Flow<Result<Unit>>

    fun controlComponent(
        component: ComponentInfo,
        newState: Boolean,
    ): Flow<Boolean>

    fun batchControlComponent(
        components: List<ComponentInfo>,
        newState: Boolean,
    ): Flow<ComponentInfo>

    fun searchComponent(keyword: String): Flow<List<ComponentInfo>>

    suspend fun saveComponents(components: List<ComponentInfo>)

    suspend fun deleteComponents(packageName: String)
}
