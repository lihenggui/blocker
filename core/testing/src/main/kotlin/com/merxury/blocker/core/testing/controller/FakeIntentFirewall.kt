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

package com.merxury.blocker.core.testing.controller

import android.content.ComponentName
import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules

class FakeIntentFirewall : IIntentFirewall {
    val rules = mutableMapOf<String, IfwRules>()

    override suspend fun getRules(packageName: String): IfwRules = rules[packageName] ?: IfwRules.empty()

    override suspend fun saveRules(packageName: String, rules: IfwRules) {
        if (rules.isEmpty()) {
            this.rules.remove(packageName)
        } else {
            this.rules[packageName] = rules
        }
    }

    override suspend fun addComponentFilter(packageName: String, componentName: String): Boolean {
        val current = getRules(packageName)
        val filter = IfwFilter.ComponentFilter("$packageName/$componentName")
        val existingRule = current.rulesFor(IfwComponentType.BROADCAST).firstOrNull()
        val updated = if (existingRule != null) {
            val updatedRule = existingRule.copy(filters = existingRule.filters + filter)
            IfwRules(current.rules.map { if (it === existingRule) updatedRule else it })
        } else {
            IfwRules(
                current.rules + IfwRule(
                    componentType = IfwComponentType.BROADCAST,
                    filters = listOf(filter),
                ),
            )
        }
        saveRules(packageName, updated)
        return true
    }

    override suspend fun removeComponentFilter(packageName: String, componentName: String): Boolean {
        val current = getRules(packageName)
        val filterName = "$packageName/$componentName"
        val updatedRules = current.rules.map { rule ->
            rule.copy(
                filters = rule.filters.filterNot {
                    it is IfwFilter.ComponentFilter && it.name == filterName
                },
            )
        }.filter { it.filters.isNotEmpty() }
        saveRules(packageName, IfwRules(updatedRules))
        return true
    }

    override suspend fun addAllComponentFilters(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit,
    ) {
        list.forEach { component ->
            addComponentFilter(component.packageName, component.className)
            callback(component)
        }
    }

    override suspend fun removeAllComponentFilters(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit,
    ) {
        list.forEach { component ->
            removeComponentFilter(component.packageName, component.className)
            callback(component)
        }
    }

    override suspend fun getComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean {
        val current = getRules(packageName)
        val formattedName = "$packageName/$componentName"
        return IfwComponentType.entries.none { type ->
            formattedName in current.componentFiltersFor(type)
        }
    }

    override suspend fun clear(packageName: String) {
        rules.remove(packageName)
    }

    override fun resetCache() {
        rules.clear()
    }
}
