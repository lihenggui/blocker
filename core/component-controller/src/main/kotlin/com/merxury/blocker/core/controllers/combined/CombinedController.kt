/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.controllers.combined

import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.di.IfwControl
import com.merxury.blocker.core.controllers.di.RootApiControl
import com.merxury.blocker.core.model.data.ComponentInfo
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Combined controller that uses both IFW and PM controllers for dual-layer component blocking.
 * - When disabling: blocks component using both IFW and PM
 * - When enabling: enables component using both IFW and PM
 * - Component state check: returns true if EITHER controller reports enabled
 */
@Singleton
internal class CombinedController @Inject constructor(
    @IfwControl private val ifwController: IController,
    @RootApiControl private val pmController: IController,
) : IController {

    override suspend fun init() {
        pmController.init()
    }

    override suspend fun switchComponent(
        component: ComponentInfo,
        state: Int,
    ): Boolean {
        val ifwResult = ifwController.switchComponent(component, state)
        val pmResult = pmController.switchComponent(component, state)
        return ifwResult && pmResult
    }

    override suspend fun enable(component: ComponentInfo): Boolean {
        val ifwResult = ifwController.enable(component)
        val pmResult = pmController.enable(component)
        return ifwResult && pmResult
    }

    override suspend fun disable(component: ComponentInfo): Boolean {
        val ifwResult = ifwController.disable(component)
        val pmResult = pmController.disable(component)
        return ifwResult && pmResult
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
        componentList.forEach { component ->
            val ifwResult = ifwController.enable(component)
            val pmResult = pmController.enable(component)
            if (ifwResult && pmResult) {
                succeededCount++
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
        componentList.forEach { component ->
            val ifwResult = ifwController.disable(component)
            val pmResult = pmController.disable(component)
            if (ifwResult && pmResult) {
                succeededCount++
                action(component)
            }
        }
        return succeededCount
    }

    override suspend fun checkComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean {
        val ifwEnabled = ifwController.checkComponentEnableState(packageName, componentName)
        val pmEnabled = pmController.checkComponentEnableState(packageName, componentName)
        return ifwEnabled || pmEnabled
    }
}
