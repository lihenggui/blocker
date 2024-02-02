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

import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.di.IfwControl
import com.merxury.blocker.core.controllers.di.RootApiControl
import com.merxury.blocker.core.controllers.di.ShizukuControl
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
    private val userDataRepository: UserDataRepository,
    @RootApiControl private val pmController: IController,
    @IfwControl private val ifwController: IController,
    @ShizukuControl private val shizukuController: IController,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ComponentRepository {

    override fun getComponent(name: String): Flow<ComponentInfo?> {
        return appComponentDao.getByName(name)
            .map { it?.toComponentInfo() }
            .flowOn(ioDispatcher)
    }

    override fun getComponentList(packageName: String): Flow<List<ComponentInfo>> =
        appComponentDao.getByPackageName(packageName)
            .mapNotNull { list ->
                list.map { it.toComponentInfo() }
            }
            .map { list ->
                // If the list is empty in the db, try to get the list from local data source
                list.ifEmpty {
                    localDataSource.getComponentList(packageName)
                        .first()
                }
            }
            .flowOn(ioDispatcher)

    override fun getComponentList(packageName: String, type: ComponentType) =
        appComponentDao.getByPackageNameAndType(packageName, type)
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
            if (diff.isNotEmpty()) {
                Timber.d("Found ${diff.size} components to delete for $packageName in type $type")
                appComponentDao.delete(diff)
            }
            appComponentDao.upsertComponentList(latestComponents)
            emit(Success(Unit))
        }
            .flowOn(ioDispatcher)

    override fun updateComponentList(packageName: String): Flow<Result<Unit>> {
        return localDataSource.getComponentList(packageName)
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
    }

    override fun controlComponent(
        component: ComponentInfo,
        newState: Boolean,
    ): Flow<Boolean> = flow {
        val packageName = component.packageName
        val componentName = component.name
        Timber.d("Control $packageName/$componentName to state $newState")
        val userData = userDataRepository.userData.first()
        val result = when (userData.controllerType) {
            IFW -> controlInIfwMode(component, newState)
            PM -> controlInPmMode(component, newState)
            SHIZUKU -> controlInShizukuMode(component, newState)
        }
        if (result) {
            val controllerType = userData.controllerType
            val updatedComponent = component.copy(
                pmBlocked = if (controllerType != IFW) {
                    !newState
                } else {
                    component.pmBlocked
                },
                ifwBlocked = if (controllerType == IFW) {
                    !newState
                } else {
                    component.ifwBlocked
                }
            )
            appComponentDao.update(updatedComponent.toAppComponentEntity())
        }
        emit(result)
    }

    override fun batchControlComponent(
        components: List<ComponentInfo>,
        newState: Boolean,
    ): Flow<ComponentInfo> = flow {
        Timber.i("Batch control ${components.size} components to state $newState")
        val userData = userDataRepository.userData.first()
        val controller = when (userData.controllerType) {
            IFW -> ifwController
            PM -> pmController
            SHIZUKU -> shizukuController
        }
        // Filter providers first in the list if preferred controller is IFW
        if (userData.controllerType == IFW) {
            // IFW doesn't have the ability to enable/disable providers
            val providers = components.filter { it.type == ComponentType.PROVIDER }
            providers.forEach {
                if (newState) {
                    pmController.enable(it)
                } else {
                    pmController.disable(it)
                }
                emit(it)
            }
            // if users want to enable the component, check if it's blocked by PM controller
            if (newState) {
                val blockedByPm = components.filter {
                    it.pmBlocked && it.type != ComponentType.PROVIDER
                }
                blockedByPm.forEach {
                    pmController.enable(it)
                    emit(it)
                }
            }
        }
        if (newState) {
            controller.batchEnable(components) {
                emit(it)
            }
        } else {
            controller.batchDisable(components) {
                emit(it)
            }
        }
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
        component: ComponentInfo,
        newState: Boolean,
    ): Boolean {
        val packageName = component.packageName
        val componentName = component.name
        val type = component.type
        if (type == ComponentType.PROVIDER) {
            Timber.v("Component $packageName/$componentName is provider.")
            return controlInPmMode(component, newState)
        }
        return if (newState) {
            // Need to enable the component by PM controller first
            val blockedByPm = !pmController.checkComponentEnableState(packageName, componentName)
            if (blockedByPm) {
                pmController.enable(component)
            }
            ifwController.enable(component)
        } else {
            ifwController.disable(component)
        }
    }

    private suspend fun controlInPmMode(
        component: ComponentInfo,
        newState: Boolean,
    ): Boolean {
        return if (newState) {
            // Need to enable the component by PM controller first
            val blockedByIfw = !ifwController.checkComponentEnableState(
                component.packageName,
                component.name,
            )
            if (blockedByIfw) {
                ifwController.enable(component)
            }
            pmController.enable(component)
        } else {
            pmController.disable(component)
        }
    }

    private suspend fun controlInShizukuMode(
        component: ComponentInfo,
        newState: Boolean,
    ): Boolean {
        // In Shizuku mode, use root privileges as little as possible
        return if (newState) {
            shizukuController.enable(component)
        } else {
            shizukuController.disable(component)
        }
    }
}
