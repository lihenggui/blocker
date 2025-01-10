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

package com.merxury.blocker.core.data.respository.component

import com.merxury.blocker.core.database.app.AppComponentDao
import com.merxury.blocker.core.database.app.toComponentInfo
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class CacheComponentDataSource @Inject constructor(
    private val componentDao: AppComponentDao,
) : ComponentDataSource {
    override fun getComponent(packageName: String, componentName: String): Flow<ComponentInfo?> = componentDao.getByPackageNameAndComponentName(packageName, componentName)
        .map { it?.toComponentInfo() }

    override fun getComponentList(
        packageName: String,
        type: ComponentType,
    ): Flow<List<ComponentInfo>> = componentDao.getByPackageNameAndType(packageName, type)
        .map { list ->
            list.map { it.toComponentInfo() }
        }

    override fun getComponentList(
        packageName: String,
    ): Flow<List<ComponentInfo>> = componentDao.getByPackageName(packageName)
        .map { list ->
            list.map { it.toComponentInfo() }
        }

    override fun getComponentType(
        packageName: String,
        componentName: String,
    ): Flow<ComponentType?> = componentDao.getByPackageNameAndComponentName(packageName, componentName)
        .map { it?.type }
}
