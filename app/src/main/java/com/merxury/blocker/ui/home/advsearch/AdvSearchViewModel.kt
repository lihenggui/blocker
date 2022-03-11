package com.merxury.blocker.ui.home.advsearch

import android.content.Context
import android.content.pm.ComponentInfo
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.ui.detail.component.ComponentData
import com.merxury.ifw.IntentFirewall
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.libkit.entity.Application
import com.merxury.libkit.entity.getSimpleName
import com.merxury.libkit.utils.ApplicationUtil
import com.merxury.libkit.utils.ServiceHelper
import java.util.regex.PatternSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdvSearchViewModel : ViewModel() {
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

    fun load(context: Context) {
        viewModelScope.launch {
            val appList = ApplicationUtil.getApplicationList(context)
            processData(context, appList)
        }
    }

    @Throws(PatternSyntaxException::class)
    fun filter(keyword: String) {
        logger.i("filter: $keyword")
        if (keyword.isEmpty()) {
            _filteredData.value = _finalData.value
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            val regex = keyword.toRegex()
            val searchResult = mutableMapOf<Application, List<ComponentData>>()
            val dataSource = finalData.value ?: return@launch
            dataSource.forEach {
                logger.d("filter: ${it.key.packageName}")
                val app = it.key
                val componentList = it.value
                val filteredComponentList = mutableListOf<ComponentData>()
                componentList.forEach { component ->
                    if (regex.containsMatchIn(component.name) || regex.containsMatchIn(component.packageName)) {
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

    private suspend fun processData(context: Context, appList: List<Application>) {
        notifyDataProcessing(appList)
        val result = mutableMapOf<Application, List<ComponentData>>()
        withContext(Dispatchers.Default) {
            val pmController = ComponentControllerProxy.getInstance(EControllerMethod.PM, context)
            appList.forEachIndexed { index, application ->
                val packageName = application.packageName
                val ifwController = IntentFirewallImpl(packageName).load()
                _currentProcessApplication.postValue(application)
                _current.postValue(index + 1)
                val components = mutableListOf<ComponentData>()
                val serviceHelper = ServiceHelper(packageName)
                serviceHelper.refresh()
                val activities = ApplicationUtil
                    .getActivityList(context.packageManager, packageName)
                    .convertToComponentDataList(ifwController, pmController, null)
                val services = ApplicationUtil.getServiceList(context.packageManager, packageName)
                    .convertToComponentDataList(
                        ifwController,
                        pmController,
                        ServiceHelper(packageName)
                    )
                val providers = ApplicationUtil.getProviderList(context.packageManager, packageName)
                    .convertToComponentDataList(ifwController, pmController, null)
                val receivers = ApplicationUtil.getReceiverList(context.packageManager, packageName)
                    .convertToComponentDataList(ifwController, pmController, null)
                val componentTotalList = components.plus(services)
                    .plus(receivers)
                    .plus(activities)
                    .plus(providers)
                if (componentTotalList.isNotEmpty()) {
                    logger.i("Add ${application.packageName} ${componentTotalList.size} components")
                    result[application] = componentTotalList
                }
            }
        }
        _finalData.postValue(result)
        _isLoading.postValue(false)
    }

    private suspend fun List<ComponentInfo>.convertToComponentDataList(
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
                pmBlocked = !pmController.checkComponentEnableState(it.packageName, it.name),
                isRunning = serviceHelper?.isServiceRunning(it.name) ?: false
            )
        }
    }

    private fun notifyDataProcessing(appList: List<Application>) {
        _appList.value = appList
        _total.value = appList.size
        _current.value = 0
        _isLoading.value = true
    }
}