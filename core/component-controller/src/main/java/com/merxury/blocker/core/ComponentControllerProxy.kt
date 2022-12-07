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

package com.merxury.blocker.core

import android.content.Context
import android.content.pm.ComponentInfo
import com.merxury.blocker.core.ifw.IfwController
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.core.root.RootController
import com.merxury.blocker.core.shizuku.ShizukuController

/**
 * Created by Mercury on 2018/3/10.
 */

class ComponentControllerProxy private constructor(
    method: EControllerMethod,
    context: Context
) : IController {

    private var controller: IController = when (method) {
        EControllerMethod.IFW -> IfwController(context)
        EControllerMethod.PM -> RootController(context)
        EControllerMethod.SHIZUKU -> ShizukuController(context)
    }

    override suspend fun switchComponent(
        packageName: String,
        componentName: String,
        state: Int
    ): Boolean {
        return controller.switchComponent(packageName, componentName, state)
    }

    override suspend fun enable(packageName: String, componentName: String): Boolean {
        return controller.enable(packageName, componentName)
    }

    override suspend fun disable(packageName: String, componentName: String): Boolean {
        return controller.disable(packageName, componentName)
    }

    override suspend fun checkComponentEnableState(
        packageName: String,
        componentName: String
    ): Boolean {
        return controller.checkComponentEnableState(packageName, componentName)
    }

    override suspend fun batchEnable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit
    ): Int {
        return controller.batchEnable(componentList) { action(it) }
    }

    override suspend fun batchDisable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit
    ): Int {
        return controller.batchDisable(componentList) { action(it) }
    }

    companion object {
        @Volatile
        private var instance: IController? = null
        var controllerMethod: EControllerMethod? = null

        fun getInstance(method: EControllerMethod, context: Context): IController =
            synchronized(this) {
                if (method != controllerMethod) {
                    getComponentControllerProxy(method, context)
                } else {
                    instance ?: getComponentControllerProxy(method, context)
                }
            }

        private fun getComponentControllerProxy(
            method: EControllerMethod,
            context: Context
        ): ComponentControllerProxy {
            return ComponentControllerProxy(method, context).also {
                controllerMethod = method
                instance = it
            }
        }
    }
}
