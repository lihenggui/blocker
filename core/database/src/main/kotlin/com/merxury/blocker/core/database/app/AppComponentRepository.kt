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

package com.merxury.blocker.core.database.app

import com.merxury.blocker.core.model.ComponentType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AppComponentRepository @Inject constructor(private val appComponentDao: AppComponentDao) {

    suspend fun getAppComponent(packageName: String, componentName: String): AppComponentEntity? {
        return appComponentDao.getByPackageNameAndComponentName(packageName, componentName)
    }

    fun getAppComponentByName(name: String): Flow<AppComponentEntity?> {
        return appComponentDao.getByName(name)
    }

    suspend fun addAppComponents(vararg appComponentEntities: AppComponentEntity) {
        appComponentDao.insert(*appComponentEntities)
    }

    suspend fun deleteAll() {
        appComponentDao.deleteAll()
    }

    fun getAppComponents(packageName: String): Flow<List<AppComponentEntity>> {
        return appComponentDao.getByPackageName(packageName)
    }

    fun getAppComponentByType(
        packageName: String,
        type: ComponentType,
    ): Flow<List<AppComponentEntity>> {
        return appComponentDao.getByPackageNameAndType(packageName, type)
    }
}
