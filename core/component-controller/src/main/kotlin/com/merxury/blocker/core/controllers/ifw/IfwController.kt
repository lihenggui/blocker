/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.controllers.ifw

import android.content.ComponentName
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.core.ifw.IIntentFirewall
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class IfwController @Inject constructor(
    private val intentFirewall: IIntentFirewall,
) : IController {

    override suspend fun switchComponent(
        component: ComponentInfo,
        state: Int,
    ): Boolean {
        val packageName = component.packageName
        val componentName = component.name
        return when (state) {
            COMPONENT_ENABLED_STATE_DISABLED -> intentFirewall.add(packageName, componentName)
            COMPONENT_ENABLED_STATE_ENABLED -> intentFirewall.remove(packageName, componentName)
            else -> false
        }
    }

    override suspend fun enable(component: ComponentInfo): Boolean {
        return switchComponent(
            component,
            COMPONENT_ENABLED_STATE_ENABLED,
        )
    }

    override suspend fun disable(component: ComponentInfo): Boolean {
        return switchComponent(
            component,
            COMPONENT_ENABLED_STATE_DISABLED,
        )
    }

    override suspend fun batchEnable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit,
    ): Int {
        if (componentList.isEmpty()) {
            Timber.w("No component to enable")
            return 0
        }
        var succeededCount = 0
        val list = componentList.map { ComponentName(it.packageName, it.name) }
        intentFirewall.removeAll(list) {
            succeededCount++
            val component = componentList
                .find { info -> info.packageName == it.packageName && info.name == it.className }
            if (component != null) {
                action(component)
            }
        }
        return succeededCount
    }

    override suspend fun batchDisable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit,
    ): Int {
        if (componentList.isEmpty()) {
            Timber.w("No component to disable")
            return 0
        }
        var succeededCount = 0
        val list = componentList.map { ComponentName(it.packageName, it.name) }
        intentFirewall.addAll(list) {
            succeededCount++
            val component = componentList
                .find { info -> info.packageName == it.packageName && info.name == it.className }
            if (component != null) {
                action(component)
            }
        }
        return succeededCount
    }

    override suspend fun checkComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean {
        return intentFirewall.getComponentEnableState(packageName, componentName)
    }
}
