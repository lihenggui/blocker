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
import com.merxury.core.ifw.ComponentFilter
import com.merxury.core.ifw.IIntentFirewall
import com.merxury.core.ifw.Rules

class FakeIntentFirewall : IIntentFirewall {
    val rules = mutableMapOf<String, Rules>()

    override suspend fun save(packageName: String, rule: Rules) {
        rules[packageName] = rule
    }

    override suspend fun add(packageName: String, componentName: String): Boolean {
        val rule = rules.getOrPut(packageName) { Rules() }
        rule.broadcast.componentFilter.add(ComponentFilter("$packageName/$componentName"))
        return true
    }

    override suspend fun remove(packageName: String, componentName: String): Boolean {
        val rule = rules[packageName] ?: return true
        val filter = ComponentFilter("$packageName/$componentName")
        rule.activity.componentFilter.remove(filter)
        rule.broadcast.componentFilter.remove(filter)
        rule.service.componentFilter.remove(filter)
        return true
    }

    override suspend fun addAll(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit,
    ) {
        list.forEach { component ->
            add(component.packageName, component.className)
            callback(component)
        }
    }

    override suspend fun removeAll(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit,
    ) {
        list.forEach { component ->
            remove(component.packageName, component.className)
            callback(component)
        }
    }

    override suspend fun getComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean {
        val rule = rules[packageName] ?: return true
        val formattedName = "$packageName/$componentName"
        for (filter in rule.activity.componentFilter) {
            if (filter.name == formattedName) return false
        }
        for (filter in rule.broadcast.componentFilter) {
            if (filter.name == formattedName) return false
        }
        for (filter in rule.service.componentFilter) {
            if (filter.name == formattedName) return false
        }
        return true
    }

    override suspend fun clear(packageName: String) {
        rules.remove(packageName)
    }

    override fun resetCache() {
        rules.clear()
    }
}
