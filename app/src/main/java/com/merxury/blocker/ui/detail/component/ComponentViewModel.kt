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

package com.merxury.blocker.ui.detail.component

import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.database.app.AppComponentRepository
import com.merxury.blocker.core.extension.getSimpleName
import com.merxury.blocker.core.model.EComponentType
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.FileUtils
import com.merxury.blocker.core.utils.ServiceHelper
import com.merxury.blocker.data.component.OnlineComponentDataRepository
import com.merxury.blocker.util.ManagerUtils
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.ifw.IntentFirewallImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ComponentViewModel @Inject constructor(
    private val pm: PackageManager,
    private val appComponentRepository: AppComponentRepository
) : ViewModel() {
    private val logger = XLog.tag("ComponentViewModel")

    private val _data = MutableLiveData<List<ComponentData>>()
    val data: LiveData<List<ComponentData>>
        get() = _data
    private var originalList = mutableListOf<ComponentData>()
    private val errorStack = MutableLiveData<Throwable>()
    val error: LiveData<Throwable>
        get() = errorStack
    private val _updatedItem = MutableLiveData<ComponentData>()
    val updatedItem: LiveData<ComponentData>
        get() = _updatedItem
    private val _zippedRules = MutableSharedFlow<File?>()
    val zippedRules = _zippedRules.asSharedFlow()

    fun load(context: Context, packageName: String, type: EComponentType) {
        logger.i("Load $packageName $type")
        viewModelScope.launch {
            val origList = getComponents(packageName, type)
            val data = convertToComponentData(context, packageName, origList, type)
            originalList = data
            _data.value = data
        }
    }

    fun controlComponent(context: Context, component: ComponentData, enabled: Boolean) {
        logger.i("Control ${component.name} $enabled")
        viewModelScope.launch(Dispatchers.IO) {
            val appComponent =
                appComponentRepository.getAppComponent(component.packageName, component.name)
            val controllerType = PreferenceUtil.getControllerType(context)
            when (controllerType) {
                EControllerMethod.PM -> controlComponentInPmMode(context, component, enabled)
                EControllerMethod.IFW -> controlComponentInIfwMode(context, component, enabled)
                EControllerMethod.SHIZUKU -> controlComponentInShizukuMode(
                    context,
                    component,
                    enabled
                )
            }
            // Save the component status to database
            appComponent?.let {
                logger.i("Save ${component.name} $enabled")
                if (controllerType == EControllerMethod.IFW) {
                    appComponent.ifwBlocked = !enabled
                } else {
                    appComponent.pmBlocked = !enabled
                }
                appComponentRepository.addAppComponents(appComponent)
            }
        }
    }

    fun enableAll(context: Context, packageName: String, type: EComponentType) {
        logger.i("Enable all $packageName, type $type")
        doBatchOperation(context, packageName, type, true)
    }

    fun disableAll(context: Context, packageName: String, type: EComponentType) {
        logger.i("Disable all $packageName, type $type")
        doBatchOperation(context, packageName, type, false)
    }

    @Throws(RuntimeException::class)
    fun launchActivity(component: ComponentData) {
        viewModelScope.launch {
            logger.i("Launch ${component.packageName}/${component.name} without params")
            ManagerUtils.launchActivity(component.packageName, component.name)
        }
    }

    private fun doBatchOperation(
        context: Context,
        packageName: String,
        type: EComponentType,
        enable: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val controllerType = PreferenceUtil.getControllerType(context)
            val controller = ComponentControllerProxy.getInstance(controllerType, context)
            try {
                val dataList = data.value ?: return@launch
                val componentInfoList = convertToComponentInfo(data.value)
                if (enable) {
                    controller.batchEnable(componentInfoList) {
                        logger.i("Enabling ${it.name}")
                        updateComponentViewStatus(dataList, it, true, controllerType)
                    }
                } else {
                    controller.batchDisable(componentInfoList) {
                        logger.i("Disabling ${it.name}")
                        updateComponentViewStatus(dataList, it, false, controllerType)
                    }
                }

                _data.postValue(dataList)
            } catch (e: Throwable) {
                logger.e(
                    "Failed to control all components $packageName, type $type, enable $enable",
                    e
                )
                errorStack.postValue(e)
                return@launch
            }
        }
    }

    private fun updateComponentViewStatus(
        list: List<ComponentData>,
        component: ComponentInfo,
        status: Boolean,
        controllerType: EControllerMethod
    ) {
        val data = list.find { it.name == component.name }
        when (controllerType) {
            EControllerMethod.IFW -> data?.ifwBlocked = !status
            else -> data?.pmBlocked = !status
        }
    }

    fun filter(keyword: String?) {
        if (keyword.isNullOrEmpty()) {
            _data.value = originalList
            return
        }
        // Ignore spaces
        val clearedKeyword = keyword.trim().replace(" ", "")
        val filteredList = originalList.filter {
            it.simpleName.contains(clearedKeyword, true) || it.name.contains(clearedKeyword, true)
        }
        _data.value = filteredList
    }

    fun shareRule(context: Context) {
        viewModelScope.launch {
            val root = context.filesDir
                .resolve(OnlineComponentDataRepository.USER_GENERATED_FOLDER)
            _zippedRules.emit(FileUtils.zipFile("rule", root))
        }
    }

    private suspend fun controlComponentInPmMode(
        context: Context,
        component: ComponentData,
        enabled: Boolean,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) = withContext(dispatcher) {
        try {
            // First we need to change IFW state if it's been blocked by IFW
            val ifwController = ComponentControllerProxy.getInstance(EControllerMethod.IFW, context)
            val blockedByIfw =
                !ifwController.checkComponentEnableState(component.packageName, component.name)
            if (blockedByIfw && enabled) {
                // Unblock IFW first, then control component by using PM controller
                ifwController.enable(component.packageName, component.name)
            }
            // Use PM controller to control components
            val pmController = ComponentControllerProxy.getInstance(EControllerMethod.PM, context)
            if (enabled) {
                pmController.enable(component.packageName, component.name)
            } else {
                pmController.disable(component.packageName, component.name)
            }
        } catch (e: Throwable) {
            logger.e("Failed to control component ${component.name} to state $enabled", e)
            errorStack.postValue(e)
            _updatedItem.postValue(component)
        }
    }

    private suspend fun controlComponentInIfwMode(
        context: Context,
        component: ComponentData,
        enabled: Boolean,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) = withContext(dispatcher) {
        try {
            // Need to enable the component by PM controller first
            if (enabled && !ApplicationUtil.checkComponentIsEnabled(
                    context.packageManager,
                    ComponentName(component.packageName, component.name)
                )
            ) {
                ComponentControllerProxy.getInstance(EControllerMethod.PM, context)
                    .enable(component.packageName, component.name)
            }
            // Then use IFW controller to control the state
            val ifwController = ComponentControllerProxy.getInstance(EControllerMethod.IFW, context)
            if (enabled) {
                ifwController.enable(component.packageName, component.name)
            } else {
                ifwController.disable(component.packageName, component.name)
            }
        } catch (e: Throwable) {
            logger.e("Failed to control component ${component.name} to state $enabled", e)
            errorStack.postValue(e)
            _updatedItem.postValue(component)
        }
    }

    private suspend fun controlComponentInShizukuMode(
        context: Context,
        component: ComponentData,
        enabled: Boolean,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) = withContext(dispatcher) {
        try {
            // In Shizuku mode, use root privileges as little as possible
            val controller =
                ComponentControllerProxy.getInstance(EControllerMethod.SHIZUKU, context)
            if (enabled) {
                controller.enable(component.packageName, component.name)
            } else {
                controller.disable(component.packageName, component.name)
            }
        } catch (e: Throwable) {
            logger.e("Failed to control component ${component.name} to state $enabled", e)
            errorStack.postValue(e)
            _updatedItem.postValue(component)
        }
    }

    private suspend fun convertToComponentData(
        context: Context,
        packageName: String,
        components: MutableList<out ComponentInfo>,
        type: EComponentType
    ): MutableList<ComponentData> {
        val ifwController = IntentFirewallImpl(packageName).load()
        val pmController = ComponentControllerProxy.getInstance(EControllerMethod.PM, context)
        val serviceHelper = if (type == EComponentType.SERVICE) {
            ServiceHelper(packageName).also { it.refresh() }
        } else {
            null
        }
        val showEnabledFirst = PreferenceUtil.getShowEnabledComponentShowFirst(context)
        // Order priority: running, enabled, name
        return withContext(Dispatchers.Default) {
            components.map {
                ComponentData(
                    name = it.name,
                    simpleName = it.getSimpleName(),
                    packageName = it.packageName,
                    ifwBlocked = !ifwController.getComponentEnableState(packageName, it.name),
                    pmBlocked = !pmController.checkComponentEnableState(packageName, it.name),
                    isRunning = serviceHelper?.isServiceRunning(it.name) ?: false,
                    type = type,
                )
            }
                .sortedWith(
                    compareBy(
                        { !it.isRunning },
                        {
                            val blocked = (it.ifwBlocked || it.pmBlocked)
                            if (showEnabledFirst) {
                                blocked
                            } else {
                                !blocked
                            }
                        },
                        { it.simpleName }
                    )
                )
                .toMutableList()
        }
    }

    private suspend fun convertToComponentInfo(list: List<ComponentData>?): List<ComponentInfo> {
        return withContext(Dispatchers.Default) {
            list?.map {
                ComponentInfo().apply {
                    packageName = it.packageName
                    name = it.name
                }
            } ?: listOf()
        }
    }

    private suspend fun getComponents(
        packageName: String,
        type: EComponentType,
    ): MutableList<out ComponentInfo> {
        val components = when (type) {
            EComponentType.RECEIVER -> ApplicationUtil.getReceiverList(pm, packageName)
            EComponentType.ACTIVITY -> ApplicationUtil.getActivityList(pm, packageName)
            EComponentType.SERVICE -> ApplicationUtil.getServiceList(pm, packageName)
            EComponentType.PROVIDER -> ApplicationUtil.getProviderList(pm, packageName)
        }
        return withContext(Dispatchers.Default) {
            components.asSequence()
                .sortedBy { it.getSimpleName() }
                .toMutableList()
        }
    }
}
