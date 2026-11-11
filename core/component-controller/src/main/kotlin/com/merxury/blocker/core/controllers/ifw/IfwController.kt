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

package com.merxury.blocker.core.controllers.ifw

import android.content.ComponentName
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.model.ComponentState
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.model.IfwComponentType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class IfwController @Inject constructor(
    private val intentFirewall: IIntentFirewall,
) : IController {

    override suspend fun switchComponent(
        component: ComponentInfo,
        state: ComponentState,
    ): Boolean {
        val packageName = component.packageName
        val componentName = component.name
        return when (state) {
            ComponentState.DISABLED -> intentFirewall.addComponentFilter(packageName, componentName)

            // IFW has no manifest default; DEFAULT means "stop intercepting" — same as ENABLED.
            ComponentState.ENABLED,
            ComponentState.DEFAULT,
            -> intentFirewall.removeComponentFilter(packageName, componentName)
        }
    }

    override suspend fun enable(component: ComponentInfo): Boolean = switchComponent(
        component,
        ComponentState.ENABLED,
    )

    override suspend fun disable(component: ComponentInfo): Boolean = switchComponent(
        component,
        ComponentState.DISABLED,
    )

    override suspend fun batchEnable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit,
    ): Int {
        if (componentList.isEmpty()) {
            Timber.w("No component to enable")
            return 0
        }
        var succeededCount = 0
        val componentsByName = componentList.associateBy { ComponentName(it.packageName, it.name) }
        intentFirewall.removeAllComponentFilters(componentsByName.keys.toList()) {
            succeededCount++
            val component = componentsByName[it]
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
        val componentsByName = componentList.associateBy { ComponentName(it.packageName, it.name) }
        intentFirewall.addAllComponentFilters(componentsByName.keys.toList()) {
            succeededCount++
            val component = componentsByName[it]
            if (component != null) {
                action(component)
            }
        }
        return succeededCount
    }

    override suspend fun checkComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean = intentFirewall.getComponentEnableState(packageName, componentName)

    override suspend fun checkComponentEnableStates(components: List<ComponentInfo>): Map<ComponentInfo, Boolean> = buildMap {
        components.groupBy { it.packageName }.forEach { (packageName, packageComponents) ->
            val rules = intentFirewall.getRules(packageName)
            val blocked = IfwComponentType.entries.flatMap { rules.componentFiltersFor(it) }.toSet()
            packageComponents.forEach { put(it, "$packageName/${it.name}" !in blocked) }
        }
    }
}
