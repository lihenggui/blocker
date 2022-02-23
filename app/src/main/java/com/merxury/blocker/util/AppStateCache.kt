package com.merxury.blocker.util

import android.content.ComponentName
import android.content.Context
import com.merxury.blocker.ui.applist.AppState
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.libkit.utils.ApplicationUtil
import com.merxury.libkit.utils.ServiceHelper

object AppStateCache {
    private val cache = mutableMapOf<String, AppState>()

    fun getInCache(packageName: String): AppState? {
        return cache[packageName]
    }

    suspend fun get(context: Context, packageName: String): AppState {
        val cachedResult = cache[packageName]
        val result: AppState
        if (cachedResult == null) {
            result = getServiceStatus(context, packageName)
            cache[packageName] = result
        } else {
            result = cachedResult
        }
        return result
    }

    private suspend fun getServiceStatus(context: Context, packageName: String): AppState {
        val pm = context.packageManager
        val serviceHelper = ServiceHelper(packageName)
        serviceHelper.refresh()
        val ifwImpl = IntentFirewallImpl.getInstance(context, packageName)
        val services = ApplicationUtil.getServiceList(pm, packageName)
        var running = 0
        var blocked = 0
        for (service in services) {
            if (!ifwImpl.getComponentEnableState(packageName, service.name) ||
                !ApplicationUtil.checkComponentIsEnabled(
                    pm,
                    ComponentName(packageName, service.name)
                )
            ) {
                blocked++
            }
            if (serviceHelper.isServiceRunning(service.name)) {
                running++
            }
        }
        return AppState(running, blocked, services.count(), packageName)
    }

    fun clear() {
        cache.clear()
    }
}