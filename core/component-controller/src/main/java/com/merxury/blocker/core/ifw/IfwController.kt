/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.core.ifw

import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.ifw.IntentFirewall
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.ifw.entity.ComponentType
import timber.log.Timber

class IfwController(private val context: Context) : IController {
    private lateinit var controller: IntentFirewall
    private lateinit var packageInfo: PackageInfo

    override suspend fun switchComponent(
        packageName: String,
        componentName: String,
        state: Int
    ): Boolean {
        init(packageName)
        val type = getComponentType(componentName)
        if (type == ComponentType.PROVIDER) {
            return when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED ->
                    ComponentControllerProxy.getInstance(
                        ControllerType.PM,
                        context
                    ).disable(packageName, componentName)

                PackageManager.COMPONENT_ENABLED_STATE_ENABLED ->
                    ComponentControllerProxy.getInstance(
                        ControllerType.PM,
                        context
                    ).enable(packageName, componentName)

                else -> false
            }
        }
        val result = when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED ->
                controller.add(packageName, componentName, type)

            PackageManager.COMPONENT_ENABLED_STATE_ENABLED ->
                controller.remove(packageName, componentName, type)

            else -> false
        }
        if (result) {
            try {
                controller.save()
                Timber.i("Save rule for $packageName success")
            } catch (e: Exception) {
                throw e
            }
        }
        return result
    }

    override suspend fun enable(packageName: String, componentName: String): Boolean {
        return switchComponent(
            packageName,
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        )
    }

    override suspend fun disable(packageName: String, componentName: String): Boolean {
        return switchComponent(
            packageName,
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        )
    }

    override suspend fun batchEnable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit
    ): Int {
        var succeededCount = 0
        if (componentList.isEmpty()) {
            return 0
        }
        componentList.forEach {
            init(it.packageName)
            val type = getComponentType(it.name)
            if (controller.remove(it.packageName, it.name, type)) {
                succeededCount++
            } else {
                Timber.w("Failed to remove in the ifw list: ${it.packageName}/${it.name}")
            }
            action(it)
        }
        controller.save()
        return succeededCount
    }

    override suspend fun batchDisable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit
    ): Int {
        var succeededCount = 0
        if (componentList.isEmpty()) {
            return 0
        }
        componentList.forEach {
            init(it.packageName)
            val type = getComponentType(it.name)
            if (controller.add(it.packageName, it.name, type)) {
                succeededCount++
            } else {
                Timber.w("Failed to add in the ifw list: ${it.packageName}/${it.name}")
            }
            action(it)
        }
        controller.save()
        return succeededCount
    }

    override suspend fun checkComponentEnableState(
        packageName: String,
        componentName: String
    ): Boolean {
        init(packageName)
        return controller.getComponentEnableState(packageName, componentName)
    }

    private suspend fun init(packageName: String) {
        initController(packageName)
        initPackageInfo(packageName)
    }

    private suspend fun initController(packageName: String) {
        if (!::controller.isInitialized || controller.packageName != packageName) {
            if (::controller.isInitialized) {
                Timber.i("Save previous rule for ${controller.packageName}")
                controller.save()
            }
            Timber.i("initController: $packageName")
            controller = IntentFirewallImpl(packageName).load()
            return
        }
    }

    private suspend fun initPackageInfo(packageName: String) {
        if (!::packageInfo.isInitialized || packageInfo.packageName != packageName) {
            packageInfo =
                ApplicationUtil.getApplicationComponents(context.packageManager, packageName)
        }
    }

    private fun getComponentType(componentName: String): ComponentType {
        packageInfo.receivers?.forEach {
            if (it.name == componentName) {
                return ComponentType.BROADCAST
            }
        }
        packageInfo.services?.forEach {
            if (it.name == componentName) {
                return ComponentType.SERVICE
            }
        }
        packageInfo.activities?.forEach {
            if (it.name == componentName) {
                return ComponentType.ACTIVITY
            }
        }
        packageInfo.providers?.forEach {
            if (it.name == componentName) {
                return ComponentType.PROVIDER
            }
        }
        return ComponentType.UNKNOWN
    }
}
