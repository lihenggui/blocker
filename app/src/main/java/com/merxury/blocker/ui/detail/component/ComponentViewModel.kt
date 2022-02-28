package com.merxury.blocker.ui.detail.component

import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.libkit.entity.getSimpleName
import com.merxury.libkit.utils.ApplicationUtil
import com.merxury.libkit.utils.ServiceHelper
import kotlinx.coroutines.launch

class ComponentViewModel(private val pm: PackageManager) : ViewModel() {
    private val logger = XLog.tag("ComponentViewModel")

    private val _data = MutableLiveData<List<ComponentData>>()
    val data: LiveData<List<ComponentData>>
        get() = _data
    private val _services = MutableLiveData<List<ComponentData>>()
    val services: LiveData<List<ComponentData>>
        get() = _services
    private val _receivers = MutableLiveData<List<ComponentData>>()
    val receivers: LiveData<List<ComponentData>>
        get() = _receivers
    private val _activities = MutableLiveData<List<ComponentData>>()
    val activities: LiveData<List<ComponentData>>
        get() = _activities
    private val _providers = MutableLiveData<List<ComponentData>>()
    val providers: LiveData<List<ComponentData>>
        get() = _providers

    fun load(context: Context, packageName: String, type: EComponentType) {
        logger.i("Load $packageName $type")
        viewModelScope.launch {
            val components = getComponents(packageName, type)
            val data = convertToComponentData(context, packageName, components, type)
            _data.value = data
            when (type) {
                EComponentType.ACTIVITY -> _activities.value = data
                EComponentType.SERVICE -> _services.value = data
                EComponentType.RECEIVER -> _receivers.value = data
                EComponentType.PROVIDER -> _providers.value = data
            }
        }
    }

    private fun convertToComponentData(
        context: Context,
        packageName: String,
        components: MutableList<out ComponentInfo>,
        type: EComponentType
    ): MutableList<ComponentData> {
        val ifwController = IntentFirewallImpl.getInstance(context, packageName)
        val pmController = ComponentControllerProxy.getInstance(EControllerMethod.PM, context)
        val serviceHelper = if (type == EComponentType.SERVICE) {
            ServiceHelper(packageName).also { it.refresh() }
        } else {
            null
        }
        return components.map {
            ComponentData(
                name = it.name,
                simpleName = it.getSimpleName(),
                packageName = it.packageName,
                ifwBlocked = !ifwController.getComponentEnableState(packageName, it.name),
                pmBlocked = !pmController.checkComponentEnableState(packageName, it.name),
                isRunning = serviceHelper?.isServiceRunning(it.name) ?: false
            )
        }.toMutableList()
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
        return components.asSequence()
            .sortedBy { it.getSimpleName() }
            .toMutableList()
    }

    @Suppress("UNCHECKED_CAST")
    class ComponentViewModelFactory(private val pm: PackageManager) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ComponentViewModel(pm) as T
        }
    }
}