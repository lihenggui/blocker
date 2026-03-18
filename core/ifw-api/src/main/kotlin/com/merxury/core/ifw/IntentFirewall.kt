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

package com.merxury.core.ifw

import android.content.ComponentName
import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.utils.RootAvailabilityChecker
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.model.IfwRule
import com.merxury.core.ifw.model.IfwRules
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlParseException
import com.merxury.core.ifw.xml.IfwXmlSerializer
import timber.log.Timber
import javax.inject.Inject

internal class IntentFirewall @Inject constructor(
    private val rootChecker: RootAvailabilityChecker,
    private val componentTypeResolver: ComponentTypeResolver,
    private val fileSystem: IfwFileSystem,
    private val serializer: IfwXmlSerializer,
    private val deserializer: IfwXmlDeserializer,
) : IIntentFirewall {
    // Cache for the loaded rules to avoid IO operations
    // Key: package name, Value: IfwRules
    private val cache: MutableMap<String, IfwRules> = mutableMapOf()

    // ── Core API ───────────────────────────────────────────────────────

    override suspend fun getRules(packageName: String): IfwRules {
        cache[packageName]?.let { return it }
        if (!rootChecker.isRootAvailable()) {
            Timber.v("Root unavailable, cannot load rules")
            return cacheEmpty(packageName)
        }
        val fileContent = fileSystem.readRules(packageName)
        if (fileContent == null) {
            Timber.v("Rule file for $packageName not exists or read failed")
            return cacheEmpty(packageName)
        }
        return try {
            Timber.v("Load rules for $packageName")
            val rules = deserializer.deserialize(fileContent)
            cache[packageName] = rules
            rules
        } catch (e: IfwXmlParseException) {
            Timber.e(e, "Failed to parse IFW rules for $packageName")
            cacheEmpty(packageName)
        } catch (e: Exception) {
            Timber.e(e, "Error reading IFW rules for $packageName")
            cacheEmpty(packageName)
        }
    }

    override suspend fun saveRules(packageName: String, rules: IfwRules) {
        if (rules.isEmpty()) {
            clear(packageName)
            return
        }
        val xml = serializer.serialize(rules)
        fileSystem.writeRules(packageName, xml)
        cache.remove(packageName)
        Timber.i("Saved IFW rules for $packageName")
    }

    override suspend fun clear(packageName: String) {
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
        Timber.d("Clear IFW rule for $packageName")
        fileSystem.deleteRules(packageName)
        cache.remove(packageName)
    }

    override fun resetCache() {
        Timber.d("Reset IFW cache")
        cache.clear()
    }

    // ── Component Filter Convenience Methods ───────────────────────────

    override suspend fun addComponentFilter(
        packageName: String,
        componentName: String,
    ): Boolean {
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
        val ifwComponentType = resolveIfwComponentType(packageName, componentName) ?: return false
        val formattedName = formatName(packageName, componentName)
        Timber.i("Add component filter for $formattedName")

        val rules = getRules(packageName)
        val updatedRules = rules.addComponentFilter(ifwComponentType, formattedName)
        saveRules(packageName, updatedRules)
        return true
    }

    override suspend fun removeComponentFilter(
        packageName: String,
        componentName: String,
    ): Boolean {
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
        val ifwComponentType = resolveIfwComponentType(packageName, componentName) ?: return false
        val formattedName = formatName(packageName, componentName)
        Timber.i("Remove component filter for $formattedName")

        val rules = getRules(packageName)
        val updatedRules = rules.removeComponentFilter(ifwComponentType, formattedName)
        saveRules(packageName, updatedRules)
        return true
    }

    override suspend fun addAllComponentFilters(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit,
    ) {
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
        Timber.i("Add component filters for ${list.size} components")
        val groupedMap = list.groupBy { it.packageName }
        groupedMap.keys.forEach { packageName ->
            var rules = getRules(packageName)
            groupedMap[packageName]?.forEach componentLoop@{ component ->
                val type = componentTypeResolver.getComponentType(packageName, component.className)
                if (type == PROVIDER) {
                    Timber.d("Cannot add IFW rule for $component")
                    return@componentLoop
                }
                val ifwType = type.toIfwComponentType() ?: return@componentLoop
                val formattedName = component.flattenToString()
                rules = rules.addComponentFilter(ifwType, formattedName)
                callback(component)
            }
            saveRules(packageName, rules)
        }
    }

    override suspend fun removeAllComponentFilters(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit,
    ) {
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
        Timber.i("Remove component filters for ${list.size} components")
        val groupedMap = list.groupBy { it.packageName }
        groupedMap.keys.forEach { packageName ->
            var rules = getRules(packageName)
            groupedMap[packageName]?.forEach componentLoop@{ component ->
                val type = componentTypeResolver.getComponentType(packageName, component.className)
                if (type == PROVIDER) {
                    Timber.d("Cannot remove IFW rule for $component")
                    return@componentLoop
                }
                val ifwType = type.toIfwComponentType() ?: return@componentLoop
                val formattedName = component.flattenToString()
                rules = rules.removeComponentFilter(ifwType, formattedName)
                callback(component)
            }
            saveRules(packageName, rules)
        }
    }

    override suspend fun getComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean {
        val rules = getRules(packageName)
        val formattedName = formatName(packageName, componentName)
        return IfwComponentType.entries.none { type ->
            formattedName in rules.componentFiltersFor(type)
        }
    }

    // ── Global Query ───────────────────────────────────────────────────

    override suspend fun getAllRules(): Map<String, IfwRules> {
        if (!rootChecker.isRootAvailable()) {
            Timber.v("Root unavailable, cannot list rules")
            return emptyMap()
        }
        val packages = fileSystem.listRuleFiles()
        return packages.associateWith { getRules(it) }
            .filter { it.value.rules.isNotEmpty() }
    }

    // ── Private Helpers ────────────────────────────────────────────────

    private fun cacheEmpty(packageName: String): IfwRules {
        val empty = IfwRules.empty()
        cache[packageName] = empty
        return empty
    }

    private suspend fun resolveIfwComponentType(
        packageName: String,
        componentName: String,
    ): IfwComponentType? = componentTypeResolver.getComponentType(packageName, componentName).toIfwComponentType()

    private fun ComponentType.toIfwComponentType(): IfwComponentType? = when (this) {
        RECEIVER -> IfwComponentType.BROADCAST
        SERVICE -> IfwComponentType.SERVICE
        ACTIVITY -> IfwComponentType.ACTIVITY
        PROVIDER -> null
    }

    private fun formatName(packageName: String, name: String): String = "$packageName/$name"
}

/**
 * Adds a component-filter to the rules for the given component type.
 * Returns a new [IfwRules] instance with the filter added.
 */
private fun IfwRules.addComponentFilter(
    componentType: IfwComponentType,
    name: String,
): IfwRules {
    val filter = IfwFilter.ComponentFilter(name)
    val existingRule = rulesFor(componentType).firstOrNull()
    return if (existingRule != null) {
        if (existingRule.filters.contains(filter)) return this
        val updatedRule = existingRule.copy(filters = existingRule.filters + filter)
        IfwRules(rules.map { if (it === existingRule) updatedRule else it })
    } else {
        IfwRules(rules + IfwRule(componentType = componentType, filters = listOf(filter)))
    }
}

/**
 * Removes a component-filter from the rules for the given component type.
 * Returns a new [IfwRules] instance with the filter removed.
 */
private fun IfwRules.removeComponentFilter(
    componentType: IfwComponentType,
    name: String,
): IfwRules {
    val filter = IfwFilter.ComponentFilter(name)
    val existingRule = rulesFor(componentType).firstOrNull() ?: return this
    val updatedFilters = existingRule.filters - filter
    return if (updatedFilters.isEmpty()) {
        IfwRules(rules.filter { it !== existingRule })
    } else {
        val updatedRule = existingRule.copy(filters = updatedFilters)
        IfwRules(rules.map { if (it === existingRule) updatedRule else it })
    }
}
