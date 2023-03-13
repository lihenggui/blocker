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

import com.merxury.blocker.core.controllers.ifw.IfwController
import com.merxury.blocker.core.controllers.root.RootController
import com.merxury.blocker.core.controllers.shizuku.ShizukuController
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.database.app.AppComponentDao
import com.merxury.blocker.core.database.app.toAppComponentEntity
import com.merxury.blocker.core.database.app.toComponentInfo
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.result.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

class LocalComponentRepository @Inject constructor(
    private val localDataSource: LocalComponentDataSource,
    private val appComponentDao: AppComponentDao,
    private val userDataRepository: UserDataRepository,
    private val pmController: RootController,
    private val ifwController: IfwController,
    private val shizukuController: ShizukuController,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ComponentRepository {

    override fun getComponentList(packageName: String): Flow<List<ComponentInfo>> =
        appComponentDao.getByPackageName(packageName)
            .map { list ->
                list.map { it.toComponentInfo() }
            }
            .flowOn(ioDispatcher)

    override fun getComponentList(packageName: String, type: ComponentType) =
        appComponentDao.getByPackageNameAndType(packageName, type)
            .map { list ->
                list.map { it.toComponentInfo() }
            }
            .flowOn(ioDispatcher)

    override fun updateComponentList(packageName: String, type: ComponentType): Flow<Result<Unit>> =
        flow {
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
            Timber.d("Found ${diff.size} components to delete for $packageName in type $type")
            appComponentDao.delete(diff)
            appComponentDao.upsertComponentList(latestComponents)
            emit(Result.Success(Unit))
        }
            .flowOn(ioDispatcher)

    override fun updateComponentList(packageName: String): Flow<Result<Unit>> =
        flow {
            val cachedComponents = appComponentDao.getByPackageName(packageName)
                .first()
            val latestComponents = localDataSource.getComponentList(packageName)
                .map { list ->
                    list.map { it.toAppComponentEntity() }
                }
                .first()
            // Find difference between cached and latest component list
            val diff = (latestComponents + cachedComponents).groupBy { it.componentName }
                .filter { it.value.size == 1 }
                .flatMap { it.value }
            Timber.d("Found ${diff.size} components to delete for $packageName")
            appComponentDao.delete(diff)
            Timber.d("Update component list for $packageName, size: ${latestComponents.size}")
            appComponentDao.upsertComponentList(latestComponents)
            emit(Result.Success(Unit))
        }
            .flowOn(ioDispatcher)

    override fun controlComponent(
        packageName: String,
        componentName: String,
        newState: Boolean,
    ): Flow<Boolean> = flow {
        Timber.d("Control $packageName/$componentName to state $newState")
        val userData = userDataRepository.userData.first()
        val result = when (userData.controllerType) {
            IFW -> controlInIfwMode(packageName, componentName, newState)
            PM -> controlInPmMode(packageName, componentName, newState)
            SHIZUKU -> controlInShizukuMode(packageName, componentName, newState)
        }
        updateComponentStatus(packageName, componentName)
        emit(result)
    }

    override fun searchComponent(keyword: String) = appComponentDao.searchByKeyword(keyword)
        .map { list ->
            list.map { it.toComponentInfo() }
        }
        .flowOn(ioDispatcher)

    override suspend fun saveComponents(components: List<ComponentInfo>) {
        val entities = components.map { it.toAppComponentEntity() }
        appComponentDao.upsertComponentList(entities)
    }

    override suspend fun deleteComponents(packageName: String) {
        appComponentDao.deleteByPackageName(packageName)
    }

    private suspend fun controlInIfwMode(
        packageName: String,
        componentName: String,
        newState: Boolean,
    ): Boolean {
        return if (newState) {
            // Need to enable the component by PM controller first
            val blockedByPm = !pmController.checkComponentEnableState(packageName, componentName)
            if (blockedByPm) {
                pmController.enable(packageName, componentName)
            }
            ifwController.enable(packageName, componentName)
        } else {
            ifwController.disable(packageName, componentName)
        }
    }

    private suspend fun controlInPmMode(
        packageName: String,
        componentName: String,
        newState: Boolean,
    ): Boolean {
        return if (newState) {
            // Need to enable the component by PM controller first
            val blockedByIfw = !ifwController.checkComponentEnableState(packageName, componentName)
            if (blockedByIfw) {
                ifwController.enable(packageName, componentName)
            }
            pmController.enable(packageName, componentName)
        } else {
            pmController.disable(packageName, componentName)
        }
    }

    private suspend fun controlInShizukuMode(
        packageName: String,
        componentName: String,
        newState: Boolean,
    ): Boolean {
        // In Shizuku mode, use root privileges as little as possible
        return if (newState) {
            shizukuController.enable(packageName, componentName)
        } else {
            shizukuController.disable(packageName, componentName)
        }
    }

    private suspend fun updateComponentStatus(packageName: String, componentName: String) {
        val component = appComponentDao.getByPackageNameAndComponentName(packageName, componentName)
            ?: return
        val newState = component.copy(
            pmBlocked = !pmController.checkComponentEnableState(packageName, componentName),
            ifwBlocked = !ifwController.checkComponentEnableState(packageName, componentName),
        )
        appComponentDao.update(newState)
    }
}
