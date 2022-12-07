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

package com.merxury.blocker.util

import android.content.ComponentName
import android.content.Context
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.ServiceHelper
import com.merxury.blocker.ui.home.applist.AppState
import com.merxury.ifw.IntentFirewallImpl

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
        val ifwImpl = IntentFirewallImpl(packageName).load()
        val services = ApplicationUtil.getServiceList(pm, packageName)
        var running = 0
        var blocked = 0
        for (service in services) {
            val component = ComponentName(packageName, service.name)
            val ifwState = ifwImpl.getComponentEnableState(packageName, service.name)
            val pmState = ApplicationUtil.checkComponentIsEnabled(pm, component)
            if (!ifwState || !pmState) {
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
