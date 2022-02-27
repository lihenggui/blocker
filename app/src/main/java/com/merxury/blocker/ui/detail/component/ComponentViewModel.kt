package com.merxury.blocker.ui.detail.component

import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import com.elvishew.xlog.XLog
import com.merxury.libkit.entity.getSimpleName
import com.merxury.libkit.utils.ApplicationUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ComponentViewModel(private val pm: PackageManager) : ViewModel() {
    private val logger = XLog.tag("ComponentViewModel")

    override fun onCleared() {
        super.onCleared()
    }

    fun load(type: EComponentType) {

    }

    private suspend fun getComponents(
        packageName: String,
        type: EComponentType,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): MutableList<out ComponentInfo> {
        return withContext(dispatcher) {
            val components = when (type) {
                EComponentType.RECEIVER -> ApplicationUtil.getReceiverList(pm, packageName)
                EComponentType.ACTIVITY -> ApplicationUtil.getActivityList(pm, packageName)
                EComponentType.SERVICE -> ApplicationUtil.getServiceList(pm, packageName)
                EComponentType.PROVIDER -> ApplicationUtil.getProviderList(pm, packageName)
            }
            return@withContext components.asSequence().sortedBy { it.getSimpleName() }
                .toMutableList()
        }
    }
}