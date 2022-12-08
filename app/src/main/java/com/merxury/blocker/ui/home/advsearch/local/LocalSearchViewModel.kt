/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.ui.home.advsearch.local

import android.content.Context
import android.content.pm.ComponentInfo
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.merxury.blocker.BlockerApplication
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.model.EComponentType
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.ServiceHelper
import com.merxury.blocker.data.Event
import com.merxury.blocker.data.app.AppComponent
import com.merxury.blocker.data.app.AppComponentRepository
import com.merxury.blocker.data.app.InstalledApp
import com.merxury.blocker.data.app.InstalledAppRepository
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.ifw.IntentFirewallImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.regex.PatternSyntaxException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel
class LocalSearchViewModel @Inject constructor(
    private val appComponentRepository: AppComponentRepository,
    private val installedAppRepository: InstalledAppRepository
) : ViewModel() {
    private val logger = XLog.tag("AdvSearchViewModel")
    private val _filteredData: MutableLiveData<Map<InstalledApp?, List<AppComponent>>> =
        MutableLiveData()
    val filteredData: LiveData<Map<InstalledApp?, List<AppComponent>>> = _filteredData
    private val _error = MutableLiveData<Event<Exception>>()
    val error: LiveData<Event<Exception>> = _error

    // To notify the user that the batch operation is finished
    private val _operationDone = MutableLiveData<Event<Boolean>>()
    val operationDone: LiveData<Event<Boolean>> = _operationDone

    private val _loadingState = MutableLiveData<LocalSearchState>(LocalSearchState.NotStarted)
    val loadingState: LiveData<LocalSearchState> = _loadingState

    private var controller: IController? = null
    private var controllerType = EControllerMethod.IFW

    fun load(context: Context, forceInit: Boolean = false) {
        // Clear filtered data when loading
        _filteredData.value = mapOf()
        viewModelScope.launch {
            val countInDb = installedAppRepository.getInstalledAppCount()
            val countInSystem = ApplicationUtil.getApplicationList(context).size
            if (countInDb != countInSystem || forceInit) {
                // Data not initialized yet, fill the data
                logger.i("AppComponent data not initialized yet, fill the data")
                _loadingState.value = LocalSearchState.Loading(null)
                initializeDb(context)
            } else {
                logger.i("AppComponent data already initialized")
            }
            _loadingState.postValue(LocalSearchState.NotStarted)
        }
        controllerType = PreferenceUtil.getControllerType(context)
        controller = ComponentControllerProxy.getInstance(controllerType, context)
    }

    private suspend fun initializeDb(context: Context) {
        // Delete the old data first to make sure the data is clean
        installedAppRepository.deleteAll()
        appComponentRepository.deleteAll()
        // Rebuild the data
        val appList = ApplicationUtil.getApplicationList(context)
        val systemApp = ApplicationUtil.getSystemApplicationList(context)
        appList.map { app ->
            val isSystem = systemApp.any { it.packageName == app.packageName }
            InstalledApp(
                packageName = app.packageName,
                versionName = app.versionName,
                firstInstallTime = app.firstInstallTime,
                lastUpdateTime = app.lastUpdateTime,
                isEnabled = app.isEnabled,
                isSystem = isSystem,
                label = app.label
            )
        }.forEach { app ->
            _loadingState.value = LocalSearchState.Loading(app)
            installedAppRepository.addInstalledApp(app)
            updateComponentInfo(context, app)
        }
    }

    private suspend fun updateComponentInfo(context: Context, app: InstalledApp) {
        val serviceHelper = ServiceHelper(app.packageName)
        serviceHelper.refresh()
        val ifwController = IntentFirewallImpl(app.packageName).load()
        val pmController = ComponentControllerProxy.getInstance(EControllerMethod.PM, context)
        val activities = ApplicationUtil
            .getActivityList(context.packageManager, app.packageName)
            .map {
                AppComponent(
                    packageName = app.packageName,
                    componentName = it.name,
                    ifwBlocked = !ifwController.getComponentEnableState(app.packageName, it.name),
                    pmBlocked = !pmController.checkComponentEnableState(app.packageName, it.name),
                    type = EComponentType.ACTIVITY,
                    exported = it.exported,
                )
            }
        val services = ApplicationUtil
            .getServiceList(context.packageManager, app.packageName)
            .map {
                AppComponent(
                    packageName = app.packageName,
                    componentName = it.name,
                    ifwBlocked = !ifwController.getComponentEnableState(app.packageName, it.name),
                    pmBlocked = !pmController.checkComponentEnableState(app.packageName, it.name),
                    type = EComponentType.SERVICE,
                    exported = it.exported,
                )
            }
        val receivers = ApplicationUtil
            .getReceiverList(context.packageManager, app.packageName)
            .map {
                AppComponent(
                    packageName = app.packageName,
                    componentName = it.name,
                    ifwBlocked = !ifwController.getComponentEnableState(app.packageName, it.name),
                    pmBlocked = !pmController.checkComponentEnableState(app.packageName, it.name),
                    type = EComponentType.RECEIVER,
                    exported = it.exported,
                )
            }
        val providers = ApplicationUtil
            .getProviderList(context.packageManager, app.packageName)
            .map {
                AppComponent(
                    packageName = app.packageName,
                    componentName = it.name,
                    ifwBlocked = !ifwController.getComponentEnableState(app.packageName, it.name),
                    pmBlocked = !pmController.checkComponentEnableState(app.packageName, it.name),
                    type = EComponentType.PROVIDER,
                    exported = it.exported,
                )
            }
        val components = ArrayList<AppComponent>().apply {
            addAll(activities)
            addAll(services)
            addAll(receivers)
            addAll(providers)
        }
        appComponentRepository.addAppComponents(*components.toTypedArray())
    }

    @Throws(PatternSyntaxException::class)
    fun filter(context: Context, keyword: String) {
        logger.i("filter: $keyword")
        if (keyword.isEmpty()) {
            _filteredData.value = mutableMapOf()
            return
        }
        _loadingState.value = LocalSearchState.Searching
        val keywords = keyword.split(",")
            .filterNot { it.trim().isEmpty() }
            .map { it.trim().lowercase() }
        viewModelScope.launch(Dispatchers.IO) {
            val searchedComponents = mutableListOf<AppComponent>()
            keywords.forEach { keyword ->
                val appComponents = appComponentRepository.getAppComponentByName(keyword)
                searchedComponents.addAll(appComponents)
            }
            var finalResult = searchedComponents.groupBy { it.packageName }
                .mapKeys { installedAppRepository.getByPackageName(it.key) }
            if (!PreferenceUtil.getSearchSystemApps(context)) {
                // Remove system apps
                finalResult = finalResult.filterKeys { it?.isSystem == false }
            }
            _filteredData.postValue(finalResult)
            _loadingState.postValue(LocalSearchState.Finished(finalResult.size))
        }
    }

    fun switchComponent(packageName: String, name: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (enabled) {
                    controller?.enable(packageName, name)
                } else {
                    controller?.disable(packageName, name)
                }
                saveComponentInfoToDb(packageName, name, enabled)
            } catch (e: Exception) {
                logger.e("Failed to control component: $packageName to state $enabled", e)
                _loadingState.postValue(LocalSearchState.Error(Event(e)))
            }
        }
    }

    fun doBatchOperation(enabled: Boolean) {
        logger.i("doBatchOperation: $enabled")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val componentList = _filteredData.value?.entries?.flatMap { it.value } ?: listOf()
                val infoList = componentList.map {
                    val component = ComponentInfo()
                    component.packageName = it.packageName
                    component.name = it.componentName
                    component
                }
                if (enabled) {
                    controller?.batchEnable(infoList) {
                        logger.i("batch enable: ${it.name}")
                        updateComponentStatus(it, true)
                    }
                } else {
                    controller?.batchDisable(infoList) {
                        logger.i("batch disable: ${it.name}")
                        updateComponentStatus(it, false)
                    }
                }
                processProviders(componentList, enabled)
                _operationDone.postValue(Event(true))
            } catch (e: Exception) {
                logger.e("Failed to do batch operation to state $enabled", e)
                _error.postValue(Event(e))
            }
        }
    }

    private suspend fun processProviders(list: List<AppComponent>, enabled: Boolean) {
        val context = BlockerApplication.context
        val type = PreferenceUtil.getControllerType(context)
        if (type != EControllerMethod.IFW) {
            // Other controllers can handle providers
            return
        }
        // IFW cannot handle providers, do extra logics
        val providerList = list.filter { it.type == EComponentType.PROVIDER }
        if (providerList.isNotEmpty()) {
            val controller = ComponentControllerProxy.getInstance(EControllerMethod.PM, context)
            providerList.forEach {
                if (enabled) {
                    logger.i("Enable provider: ${it.packageName}/${it.componentName}")
                    controller.enable(it.packageName, it.componentName)
                } else {
                    logger.i("Disable provider: ${it.packageName}/${it.componentName}")
                    controller.disable(it.packageName, it.componentName)
                }
            }
        }
    }

    private suspend fun saveComponentInfoToDb(
        packageName: String,
        name: String,
        enabled: Boolean
    ) {
        val updatedComponent = appComponentRepository.getAppComponent(packageName, name) ?: run {
            logger.e("Component not found: $packageName, $name")
            return
        }
        if (controllerType == EControllerMethod.IFW) {
            updatedComponent.ifwBlocked = !enabled
        } else {
            updatedComponent.pmBlocked = !enabled
        }
        appComponentRepository.addAppComponents(updatedComponent)
    }

    private suspend fun updateComponentStatus(component: ComponentInfo, enabled: Boolean) {
        val data = filteredData.value ?: return
        val app = data.keys.firstOrNull { it?.packageName == component.packageName } ?: return
        val componentList = data[app] ?: return
        val componentData =
            componentList.firstOrNull { it.componentName == component.name } ?: return
        if (
            controllerType == EControllerMethod.IFW &&
            componentData.type != EComponentType.PROVIDER
        ) {
            componentData.ifwBlocked = !enabled
        } else {
            componentData.pmBlocked = !enabled
        }
        saveComponentInfoToDb(component.packageName, component.name, enabled)
    }
}
