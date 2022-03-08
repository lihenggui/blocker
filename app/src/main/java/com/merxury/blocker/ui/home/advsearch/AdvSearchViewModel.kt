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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

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
    private val _finalData = MutableLiveData<MutableList<Pair<Application, List<ComponentData>>>>()
    val finalData: LiveData<MutableList<Pair<Application, List<ComponentData>>>> = _finalData

    @OptIn(ExperimentalTime::class)
    fun load(context: Context) {
        val timeToLoad = measureTime {
            viewModelScope.launch {
                val appList = ApplicationUtil.getThirdPartyApplicationList(context)
                processData(context, appList)
            }
        }
        logger.i("load time: $timeToLoad")
    }

    private suspend fun processData(context: Context, appList: List<Application>) {
        notifyDataProcessing(appList)
        val result = mutableListOf<Pair<Application, List<ComponentData>>>()
        withContext(Dispatchers.Default) {
            val pmController = ComponentControllerProxy.getInstance(EControllerMethod.PM, context)
            appList.forEachIndexed { index, application ->
                val packageName = application.packageName
                val ifwController = IntentFirewallImpl(packageName).load()
                _currentProcessApplication.postValue(application)
                _current.postValue(index)
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
                components.plus(activities)
                    .plus(services)
                    .plus(providers)
                    .plus(receivers)
                result.add(Pair(application, components))
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