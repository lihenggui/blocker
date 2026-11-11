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

import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.di.CombinedControl
import com.merxury.blocker.core.controllers.di.IfwControl
import com.merxury.blocker.core.controllers.di.RootApiControl
import com.merxury.blocker.core.controllers.di.ShizukuControl
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.database.app.AppComponentDao
import com.merxury.blocker.core.database.app.toAppComponentEntity
import com.merxury.blocker.core.database.app.toComponentInfo
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.ComponentState
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.IFW_PLUS_PM
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.result.Result.Success
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.TimeSource

internal class LocalComponentRepository @Inject constructor(
    private val localDataSource: LocalComponentDataSource,
    private val appComponentDao: AppComponentDao,
    private val userDataRepository: UserDataRepository,
    @RootApiControl private val pmController: IController,
    @IfwControl private val ifwController: IController,
    @ShizukuControl private val shizukuController: IController,
    @CombinedControl private val combinedController: IController,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ComponentRepository {

    override fun getAllComponents(): Flow<List<ComponentInfo>> = appComponentDao.getAll()
        .map { list -> list.map { it.toComponentInfo() } }
        .flowOn(ioDispatcher)

    override fun getComponent(name: String): Flow<ComponentInfo?> = appComponentDao.getByName(name)
        .map { it?.toComponentInfo() }
        .flowOn(ioDispatcher)

    override fun getComponentList(packageName: String): Flow<List<ComponentInfo>> = appComponentDao.getByPackageName(packageName)
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

    override fun controlComponent(
        component: ComponentInfo,
        newState: Boolean,
        controllerType: ControllerType?,
    ): Flow<Boolean> = flow {
        val packageName = component.packageName
        val componentName = component.name
        Timber.d("Control $packageName/$componentName to state $newState")
        val effectiveControllerType = controllerType ?: userDataRepository.userData.first().controllerType
        val result = when (effectiveControllerType) {
            IFW -> controlInIfwMode(component, newState)
            PM -> controlInPmMode(component, newState)
            SHIZUKU -> controlInShizukuMode(component, newState)
            IFW_PLUS_PM -> controlInCombinedMode(component, newState)
        }
        updateComponentStatus(packageName, componentName)
        emit(result)
    }

    override fun batchControlComponent(
        components: List<ComponentInfo>,
        newState: Boolean,
        controllerType: ControllerType?,
    ): Flow<ComponentInfo> = flow {
        Timber.i("Batch control ${components.size} components to state $newState")
        val effectiveControllerType = controllerType ?: userDataRepository.userData.first().controllerType
        val controller = when (effectiveControllerType) {
            IFW -> ifwController
            PM -> pmController
            SHIZUKU -> shizukuController
            IFW_PLUS_PM -> combinedController
        }
        val packages = components.distinctBy { it.packageName to it.name }.groupBy { it.packageName }
        packages.values.forEach { packageComponents ->
            val started = TimeSource.Monotonic.markNow()
            var reconciled = emptyList<ComponentInfo>()
            var controlFailure: Throwable? = null
            val applied = mutableSetOf<Pair<String, String>>()
            val onApplied: suspend (ComponentInfo) -> Unit = { applied.add(it.packageName to it.name) }
            try {
                if (effectiveControllerType == IFW) {
                    val providers = packageComponents.filter { it.type == ComponentType.PROVIDER }
                    if (newState) pmController.batchEnable(providers, onApplied) else pmController.batchDisable(providers, onApplied)
                    val ifwComponents = packageComponents.filter { it.type != ComponentType.PROVIDER }
                    if (newState) {
                        val pmStates = pmController.checkComponentEnableStates(ifwComponents)
                        pmController.batchEnable(ifwComponents.filter { pmStates[it] == false }) {}
                        controller.batchEnable(ifwComponents, onApplied)
                    } else {
                        controller.batchDisable(ifwComponents, onApplied)
                    }
                } else {
                    if (newState) controller.batchEnable(packageComponents, onApplied) else controller.batchDisable(packageComponents, onApplied)
                }
                Timber.d("Controlled ${packageComponents.size} components in ${started.elapsedNow()}")
            } catch (exception: Throwable) {
                controlFailure = exception
                throw exception
            } finally {
                try {
                    // A cancellation can arrive after a controller has already changed the device.
                    // Finish reconciliation for this package before propagating it to the caller.
                    withContext(NonCancellable) {
                        val verificationStarted = TimeSource.Monotonic.markNow()
                        val pmStates = pmController.checkComponentEnableStates(packageComponents)
                        val ifwStates = ifwController.checkComponentEnableStates(packageComponents)
                        reconciled = packageComponents.map {
                            it.copy(pmBlocked = !pmStates.getValue(it), ifwBlocked = !ifwStates.getValue(it))
                        }
                        val verificationTime = verificationStarted.elapsedNow()
                        val saveStarted = TimeSource.Monotonic.markNow()
                        appComponentDao.upsertComponentList(reconciled.map { it.toAppComponentEntity() })
                        Timber.d("Reconciled ${packageComponents.size} components: verification=$verificationTime, save=${saveStarted.elapsedNow()}")
                    }
                } catch (exception: Throwable) {
                    val previousFailure = controlFailure
                    if (previousFailure == null) throw exception else previousFailure.addSuppressed(exception)
                }
            }
            val failed = reconciled.count { (it.packageName to it.name) !in applied || !it.hasRequestedState(effectiveControllerType, newState) }
            if (failed > 0) error("Failed to change $failed components in ${packageComponents.first().packageName}")
            reconciled.forEach { emit(it) }
            Timber.d("Controlled and reconciled ${packageComponents.size} components in ${started.elapsedNow()}")
        }
    }
        .flowOn(ioDispatcher)

    private fun ComponentInfo.hasRequestedState(controllerType: ControllerType, enabled: Boolean): Boolean = when (controllerType) {
        IFW -> if (type == ComponentType.PROVIDER) {
            pmBlocked != enabled
        } else if (enabled) {
            !pmBlocked && !ifwBlocked
        } else {
            ifwBlocked
        }

        PM -> pmBlocked != enabled

        SHIZUKU -> pmBlocked != enabled

        IFW_PLUS_PM -> pmBlocked != enabled && (type == ComponentType.PROVIDER || ifwBlocked != enabled)
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

    override fun restorePmBlockedComponents(): Flow<ComponentInfo> = flow {
        val entities = appComponentDao.getPmBlockedComponents()
        Timber.i("Restoring ${entities.size} PM-blocked components to default state")
        if (entities.isEmpty()) return@flow
        val useShizuku = userDataRepository.userData.first().controllerType == SHIZUKU
        val controller = if (useShizuku) shizukuController else pmController
        controller.init()
        // ComponentState.DEFAULT maps to `pm default-state`, which clears user overrides so the
        // component falls back to its manifest-declared state. Errors propagate to the caller.
        entities.forEach { entity ->
            val component = entity.toComponentInfo()
            controller.switchComponent(component, ComponentState.DEFAULT)
            appComponentDao.update(entity.copy(pmBlocked = false))
            emit(component)
        }
    }.flowOn(ioDispatcher)

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
    ): Boolean = if (newState) {
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

    private suspend fun controlInCombinedMode(
        component: ComponentInfo,
        newState: Boolean,
    ): Boolean = if (newState) {
        combinedController.enable(component)
    } else {
        combinedController.disable(component)
    }

    private suspend fun updateComponentStatus(
        packageName: String,
        componentName: String,
    ): ComponentInfo? {
        val component = appComponentDao.getByPackageNameAndComponentName(packageName, componentName)
            .first()
        if (component == null) {
            Timber.e("Component $packageName/$componentName not found in database")
            return null
        }
        val newState = component.copy(
            pmBlocked = !pmController.checkComponentEnableState(packageName, componentName),
            ifwBlocked = !ifwController.checkComponentEnableState(packageName, componentName),
        )
        appComponentDao.update(newState)
        return newState.toComponentInfo()
    }
}
