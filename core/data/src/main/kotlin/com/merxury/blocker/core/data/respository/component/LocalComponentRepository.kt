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
import com.merxury.blocker.core.exception.RootUnavailableException
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
import kotlinx.coroutines.CancellationException
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
    ): Flow<ComponentControlResult> = flow {
        Timber.i("Batch control ${components.size} components to state $newState")
        val effectiveControllerType = controllerType ?: userDataRepository.userData.first().controllerType
        val packages = components.distinctBy { it.packageName to it.name }.groupBy { it.packageName }
        packages.values.forEach { packageComponents ->
            controlPackage(packageComponents, newState, effectiveControllerType).forEach { emit(it) }
        }
    }
        .flowOn(ioDispatcher)

    /**
     * Changes the components of one app, then reads back and saves the state the device reports
     * for them, also when the change failed or was cancelled part way. A failure is logged and
     * reported per component, so the caller can go on with the next app. Cancellation and a
     * missing root grant are rethrown once the state is saved, so no further app is started:
     * without root every other app would fail the same way.
     */
    private suspend fun controlPackage(
        packageComponents: List<ComponentInfo>,
        newState: Boolean,
        controllerType: ControllerType,
    ): List<ComponentControlResult> {
        val packageName = packageComponents.first().packageName
        val started = TimeSource.Monotonic.markNow()
        val applied = mutableSetOf<Pair<String, String>>()
        val failure: Exception? = try {
            changePackage(packageComponents, newState, controllerType) { applied += it.packageName to it.name }
            null
        } catch (e: CancellationException) {
            e
        } catch (e: Exception) {
            e
        }
        val reconciled = try {
            // Changes made before a cancellation or a lost root grant still have to be saved.
            withContext(NonCancellable) {
                if (failure is RootUnavailableException) {
                    savePmState(packageComponents)
                    null
                } else {
                    readBackAndSave(packageComponents)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot read back or save ${packageComponents.size} components of $packageName")
            null
        }
        if (failure is CancellationException || failure is RootUnavailableException) throw failure
        if (failure != null) Timber.e(failure, "Cannot change components of $packageName")
        val results = (reconciled ?: packageComponents).map { component ->
            val succeeded = reconciled != null &&
                (component.packageName to component.name) in applied &&
                component.hasRequestedState(controllerType, newState)
            ComponentControlResult(component, succeeded)
        }
        Timber.d("Controlled ${packageComponents.size} components of $packageName in ${started.elapsedNow()}, ${results.count { !it.succeeded }} failed")
        return results
    }

    /**
     * Changes one app's components. The controllers send nothing more once the caller is
     * cancelled, and let the change they have already sent finish, so the read-back that
     * follows sees every change that was made.
     */
    private suspend fun changePackage(
        components: List<ComponentInfo>,
        newState: Boolean,
        controllerType: ControllerType,
        onApplied: suspend (ComponentInfo) -> Unit,
    ) {
        val controller = when (controllerType) {
            IFW -> ifwController
            PM -> pmController
            SHIZUKU -> shizukuController
            IFW_PLUS_PM -> combinedController
        }
        if (controllerType != IFW) {
            controller.batchSwitch(components, newState, onApplied)
            return
        }
        // IFW cannot block providers, so PM changes them.
        val (providers, ifwComponents) = components.partition { it.type == ComponentType.PROVIDER }
        pmController.batchSwitch(providers, newState, onApplied)
        if (newState) {
            // Enabling also lifts PM blocks left on components that IFW controls.
            val pmStates = pmController.checkComponentEnableStates(ifwComponents)
            pmController.batchEnable(ifwComponents.filter { pmStates[it] == false }) {}
        }
        controller.batchSwitch(ifwComponents, newState, onApplied)
    }

    private suspend fun IController.batchSwitch(
        components: List<ComponentInfo>,
        enable: Boolean,
        onApplied: suspend (ComponentInfo) -> Unit,
    ) {
        if (enable) batchEnable(components, onApplied) else batchDisable(components, onApplied)
    }

    /** Reads the PM and IFW state of [components] from the device and saves it. */
    private suspend fun readBackAndSave(components: List<ComponentInfo>): List<ComponentInfo> {
        val pmStates = pmController.checkComponentEnableStates(components)
        val ifwStates = ifwController.checkComponentEnableStates(components)
        val reconciled = components.map {
            it.copy(pmBlocked = !pmStates.getValue(it), ifwBlocked = !ifwStates.getValue(it))
        }
        appComponentDao.upsertComponentList(reconciled.map { it.toAppComponentEntity() })
        return reconciled
    }

    /**
     * Saves only the PM state of [components], which is read without root, to the rows that
     * already exist for them. Without root the IFW rules cannot be read and an unreadable file
     * reads as not blocked, so no IFW state is written and no row with an unverified IFW state
     * is added. The callers' IFW flags may come from such a read.
     */
    private suspend fun savePmState(components: List<ComponentInfo>) {
        val pmStates = pmController.checkComponentEnableStates(components)
        components.forEach {
            appComponentDao.updatePmBlocked(it.packageName, it.name, pmBlocked = !pmStates.getValue(it))
        }
    }

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
