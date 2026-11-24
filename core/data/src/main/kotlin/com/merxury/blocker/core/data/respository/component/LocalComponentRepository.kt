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
import com.merxury.blocker.core.database.app.toAppComponentEntity
import com.merxury.blocker.core.database.app.toComponentInfo
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.result.Result.Success
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.zip
import timber.log.Timber
import javax.inject.Inject

internal class LocalComponentRepository @Inject constructor(
    private val localDataSource: LocalComponentDataSource,
    private val appComponentDao: AppComponentDao,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ComponentRepository {

    override fun getComponent(name: String): Flow<ComponentInfo?> = appComponentDao.getByName(name)
        .map { it?.toComponentInfo() }
        .flowOn(ioDispatcher)

    override fun getComponentList(packageName: String, type: ComponentType) = appComponentDao.getByPackageNameAndType(packageName, type)
        .mapNotNull { list ->
            list.map { it.toComponentInfo() }
        }
        .map { list ->
            // If the list is empty in the db, try to get the list from local data source
            list.ifEmpty {
                localDataSource.getComponentList(packageName, type)
                    .first()
            }
        }
        .flowOn(ioDispatcher)

    override fun updateComponentList(packageName: String, type: ComponentType): Flow<Result<Unit>> = flow {
        val cachedComponents = appComponentDao.getByPackageNameAndType(packageName, type)
            .first()
        val latestComponents = localDataSource.getComponentList(packageName, type)
            .map { list ->
                list.map { it.toAppComponentEntity() }
            }
            .first()
        val diff = (latestComponents + cachedComponents).groupBy { it.componentName }
            .filter { it.value.size == 1 }
            .flatMap { it.value }
        if (diff.isNotEmpty()) {
            Timber.d("Found ${diff.size} components to delete for $packageName in type $type")
            appComponentDao.delete(diff)
        }
        appComponentDao.upsertComponentList(latestComponents)
        emit(Success(Unit))
    }
        .flowOn(ioDispatcher)

    override fun updateComponentList(packageName: String): Flow<Result<Unit>> = localDataSource.getComponentList(packageName)
        .map { list ->
            list.map { it.toAppComponentEntity() }
        }
        .zip(appComponentDao.getByPackageName(packageName)) { latest, cached ->
            val diff = (latest + cached).groupBy { it.componentName }
                .filter { it.value.size == 1 }
                .flatMap { it.value }
            if (diff.isNotEmpty()) {
                Timber.d("Found ${diff.size} components to delete for $packageName")
                appComponentDao.delete(diff)
            }
            Timber.d("Update component list for $packageName, size: ${latest.size}")
            appComponentDao.upsertComponentList(latest)
            Success(Unit)
        }
        .flowOn(ioDispatcher)

    override suspend fun saveComponents(components: List<ComponentInfo>) {
        val entities = components.map { it.toAppComponentEntity() }
        appComponentDao.upsertComponentList(entities)
    }

    override suspend fun deleteComponents(packageName: String) {
        appComponentDao.deleteByPackageName(packageName)
    }
}
