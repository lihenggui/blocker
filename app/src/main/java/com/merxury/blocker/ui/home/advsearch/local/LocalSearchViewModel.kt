package com.merxury.blocker.ui.home.advsearch.local

import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.merxury.blocker.BlockerApplication
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.data.Event
import com.merxury.blocker.ui.detail.component.ComponentData
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.ifw.IntentFirewall
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.libkit.entity.Application
import com.merxury.libkit.entity.EComponentType
import com.merxury.libkit.entity.getSimpleName
import com.merxury.libkit.utils.ApplicationUtil
import com.merxury.libkit.utils.ServiceHelper
import java.util.regex.PatternSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalSearchViewModel : ViewModel() {
    private val logger = XLog.tag("AdvSearchViewModel")
    private val _appList = MutableLiveData<List<Application>>()
    val appList: LiveData<List<Application>> = _appList
    private val _total = MutableLiveData<Int>()
    val total: LiveData<Int> = _total
    private val _current = MutableLiveData<Int>()
    val current: LiveData<Int> = _current
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    private val _currentProcessApplication = MutableLiveData<Application>()
    val currentProcessApplication: LiveData<Application> = _currentProcessApplication
    private val _finalData = MutableLiveData<MutableMap<Application, List<ComponentData>>>()
    private val finalData: LiveData<MutableMap<Application, List<ComponentData>>> = _finalData
    private val _filteredData: MutableLiveData<MutableMap<Application, List<ComponentData>>> =
        MutableLiveData()
    val filteredData: LiveData<MutableMap<Application, List<ComponentData>>> = _filteredData
    private val _error = MutableLiveData<Event<Exception>>()
    val error: LiveData<Event<Exception>> = _error
    private val _operationDone = MutableLiveData<Event<Boolean>>()
    val operationDone: LiveData<Event<Boolean>> = _operationDone

    private var controller: IController? = null
    private var controllerType = EControllerMethod.IFW

    fun load(context: Context) {
        viewModelScope.launch {
            val appList = if (PreferenceUtil.getSearchSystemApps(context)) {
                ApplicationUtil.getApplicationList(context)
            } else {
                ApplicationUtil.getThirdPartyApplicationList(context)
            }
            processData(context, appList)
        }
        controllerType = PreferenceUtil.getControllerType(context)
        controller = ComponentControllerProxy.getInstance(controllerType, context)
    }

    @Throws(PatternSyntaxException::class)
    fun filter(keyword: String, useRegex: Boolean = false) {
        logger.i("filter: $keyword")
        if (keyword.isEmpty()) {
            _filteredData.value = mutableMapOf()
            return
        }
        if (useRegex) {
            // Check validity of this regex and throw exception earlier
            keyword.split(",")
                .filterNot { it.trim().isEmpty() }
                .map { it.trim().lowercase().toRegex() }
        }
        val keywords = keyword.split(",")
            .filterNot { it.trim().isEmpty() }
            .map { it.trim().lowercase() }
        viewModelScope.launch(Dispatchers.Default) {
            val searchResult = mutableMapOf<Application, List<ComponentData>>()
            val dataSource = finalData.value ?: return@launch
            dataSource.forEach {
                val app = it.key
                val componentList = it.value
                val filteredComponentList = mutableListOf<ComponentData>()
                componentList.forEach { component ->
                    if (containsKeyword(component, keywords, useRegex)) {
                        filteredComponentList.add(component)
                    }
                }
                if (filteredComponentList.isNotEmpty()) {
                    searchResult[app] = filteredComponentList
                }
            }
            _filteredData.postValue(searchResult)
        }
    }

    private fun containsKeyword(
        component: ComponentData,
        keywords: List<String>,
        useRegex: Boolean
    ): Boolean {
        return if (useRegex) {
            val regexes = keywords.map { it.toRegex() }
            regexes.any { it.containsMatchIn(component.name.lowercase()) } ||
                    regexes.any { it.containsMatchIn(component.packageName.lowercase()) }
        } else {
            keywords.any { component.name.lowercase().contains(it) } ||
                    keywords.any { component.packageName.lowercase().contains(it) }
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
            } catch (e: Exception) {
                logger.e("Failed to control component: $packageName to state $enabled", e)
                _error.postValue(Event(e))
            }
        }
    }

    fun doBatchOperation(enabled: Boolean) {
        logger.i("doBatchOperation: $enabled")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val componentList = mutableListOf<ComponentData>()
                filteredData.value?.forEach {
                    componentList.addAll(it.value)
                }
                val infoList = componentList.map {
                    val component = ComponentInfo()
                    component.packageName = it.packageName
                    component.name = it.name
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

    private suspend fun processProviders(list: List<ComponentData>, enabled: Boolean) {
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
                    logger.i("Enable provider: ${it.packageName}/${it.name}")
                    controller.enable(it.packageName, it.name)
                } else {
                    logger.i("Disable provider: ${it.packageName}/${it.name}")
                    controller.disable(it.packageName, it.name)
                }
            }
        }
    }

    private fun updateComponentStatus(component: ComponentInfo, enabled: Boolean) {
        val data = filteredData.value ?: return
        val app = data.keys.firstOrNull { it.packageName == component.packageName } ?: return
        val componentList = data[app] ?: return
        val componentData = componentList.firstOrNull { it.name == component.name } ?: return
        if (controllerType == EControllerMethod.IFW && componentData.type != EComponentType.PROVIDER) {
            componentData.ifwBlocked = !enabled
        } else {
            componentData.pmBlocked = !enabled
        }
    }

    private suspend fun processData(context: Context, appList: List<Application>) {
        notifyDataProcessing(appList)
        val result = mutableMapOf<Application, List<ComponentData>>()
        withContext(Dispatchers.Default) {
            val sortedList = appList.sortedBy { it.label }
            val pmController = ComponentControllerProxy.getInstance(EControllerMethod.PM, context)
            sortedList.forEachIndexed { index, application ->
                val packageName = application.packageName
                val ifwController = IntentFirewallImpl(packageName).load()
                _currentProcessApplication.postValue(application)
                _current.postValue(index + 1)
                val components = mutableListOf<ComponentData>()
                val serviceHelper = ServiceHelper(packageName)
                serviceHelper.refresh()
                val activities = ApplicationUtil
                    .getActivityList(context.packageManager, packageName)
                    .convertToComponentDataList(context, ifwController, pmController, null)
                val services = ApplicationUtil.getServiceList(context.packageManager, packageName)
                    .convertToComponentDataList(
                        context,
                        ifwController,
                        pmController,
                        ServiceHelper(packageName)
                    )
                val providers = ApplicationUtil.getProviderList(context.packageManager, packageName)
                    .convertToComponentDataList(context, ifwController, pmController, null)
                val receivers = ApplicationUtil.getReceiverList(context.packageManager, packageName)
                    .convertToComponentDataList(context, ifwController, pmController, null)
                val componentTotalList = components.plus(services)
                    .plus(receivers)
                    .plus(activities)
                    .plus(providers)
                if (componentTotalList.isNotEmpty()) {
                    result[application] = componentTotalList
                }
            }
        }
        _finalData.postValue(result)
        _isLoading.postValue(false)
    }

    private suspend fun List<ComponentInfo>.convertToComponentDataList(
        context: Context,
        ifwController: IntentFirewall,
        pmController: IController,
        serviceHelper: ServiceHelper?
    ): List<ComponentData> {
        return this.map {
            ComponentData(
                name = it.name,
                simpleName = it.getSimpleName(),
                packageName = it.packageName,
                ifwBlocked = !ifwController.getComponentEnableState(it.packageName, it.name),
                type = getComponentType(context.packageManager, it.packageName, it.name),
                pmBlocked = !pmController.checkComponentEnableState(it.packageName, it.name),
                isRunning = serviceHelper?.isServiceRunning(it.name) ?: false
            )
        }
            .sortedBy { it.simpleName }
    }

    private suspend fun getComponentType(
        pm: PackageManager,
        packageName: String,
        name: String
    ): EComponentType {
        return if (ApplicationUtil.isProvider(pm, packageName, name)) {
            EComponentType.PROVIDER
        } else {
            EComponentType.RECEIVER
        }
    }

    private fun notifyDataProcessing(appList: List<Application>) {
        _appList.value = appList
        _total.value = appList.size
        _current.value = 0
        _isLoading.value = true
    }
}