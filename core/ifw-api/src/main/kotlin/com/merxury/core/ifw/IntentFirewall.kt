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
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.utils.RootAvailabilityChecker
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XML
import timber.log.Timber
import javax.inject.Inject

internal class IntentFirewall @Inject constructor(
    private val xmlParser: XML,
    private val rootChecker: RootAvailabilityChecker,
    private val componentTypeResolver: ComponentTypeResolver,
    private val fileSystem: IfwFileSystem,
) : IIntentFirewall {
    // Cache for the loaded rules to avoid IO operations
    // Key: package name, Value: Rules
    private val ruleCache: MutableMap<String, Rules> = mutableMapOf()

    private suspend fun load(packageName: String): Rules {
        if (!rootChecker.isRootAvailable()) {
            Timber.v("Root unavailable, cannot load rule")
            return emptyRule(packageName)
        }
        val fileContent = fileSystem.readRules(packageName)
        if (fileContent == null) {
            Timber.v("Rule file for $packageName not exists or read failed")
            return emptyRule(packageName)
        }
        return try {
            Timber.v("Load rule for $packageName")
            val rule = xmlParser.decodeFromString<Rules>(fileContent)
            ruleCache[packageName] = rule
            rule
        } catch (e: SerializationException) {
            Timber.e(e, "Failed to decode rules for $packageName")
            emptyRule(packageName)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "the decoded input is not a valid instance of Rules: $packageName")
            emptyRule(packageName)
        } catch (e: Exception) {
            Timber.e(e, "Error reading rules for $packageName")
            emptyRule(packageName)
        }
    }

    private fun emptyRule(packageName: String): Rules {
        val newRule = Rules()
        ruleCache[packageName] = newRule
        return newRule
    }

    override suspend fun save(packageName: String, rule: Rules) {
        val isActivityEmpty = rule.activity.componentFilter.isEmpty()
        val isBroadcastEmpty = rule.broadcast.componentFilter.isEmpty()
        val isServiceEmpty = rule.service.componentFilter.isEmpty()
        if (isActivityEmpty && isBroadcastEmpty && isServiceEmpty) {
            // If there is no rules presented, delete rule file (if exists)
            clear(packageName)
            return
        }
        // Write xml content to file
        val fileContent = xmlParser.encodeToString(rule)
        fileSystem.writeRules(packageName, fileContent)
        ruleCache.remove(packageName)
        Timber.i("Saved IFW rules for $packageName")
    }

    override suspend fun clear(packageName: String) {
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
        Timber.d("Clear IFW rule for $packageName")
        fileSystem.deleteRules(packageName)
        ruleCache.remove(packageName)
    }

    override suspend fun add(
        packageName: String,
        componentName: String,
    ): Boolean {
        if (!rootChecker.isRootAvailable()) {
            Timber.e("Root unavailable, cannot add rule")
            throw RootUnavailableException()
        }
        val formattedName = formatName(packageName, componentName)
        Timber.i("Add rule for $formattedName")
        val rule = ruleCache[packageName] ?: load(packageName)
        when (componentTypeResolver.getComponentType(packageName, componentName)) {
            RECEIVER -> rule.broadcast.componentFilter.add(ComponentFilter(formattedName))
            SERVICE -> rule.service.componentFilter.add(ComponentFilter(formattedName))
            ACTIVITY -> rule.activity.componentFilter.add(ComponentFilter(formattedName))
            else -> return false
        }
        save(packageName, rule)
        return true
    }

    override suspend fun remove(
        packageName: String,
        componentName: String,
    ): Boolean {
        if (!rootChecker.isRootAvailable()) {
            Timber.e("Root unavailable, cannot remove rule")
            throw RootUnavailableException()
        }
        val formattedName = formatName(packageName, componentName)
        Timber.i("Remove rule for $formattedName")
        val rule = ruleCache[packageName] ?: load(packageName)
        when (componentTypeResolver.getComponentType(packageName, componentName)) {
            RECEIVER -> rule.broadcast.componentFilter.remove(ComponentFilter(formattedName))
            SERVICE -> rule.service.componentFilter.remove(ComponentFilter(formattedName))
            ACTIVITY -> rule.activity.componentFilter.remove(ComponentFilter(formattedName))
            else -> return false
        }
        save(packageName, rule)
        return true
    }

    override suspend fun addAll(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit,
    ) {
        if (!rootChecker.isRootAvailable()) {
            Timber.e("Root unavailable, cannot add rules")
            throw RootUnavailableException()
        }
        Timber.i("Add rules for ${list.size} components")
        // the list may contain components from different packages (not likely to happen)
        val groupedMap = list.groupBy { it.packageName }
        groupedMap.keys.forEach { packageName ->
            val rule = ruleCache[packageName] ?: load(packageName)
            groupedMap[packageName]?.forEach componentLoop@{ component ->
                val filter = ComponentFilter(component.flattenToString())
                val type = componentTypeResolver.getComponentType(packageName, component.className)
                if (type == PROVIDER) {
                    Timber.d("Cannot add IFW rule for $component")
                    return@componentLoop
                }
                when (type) {
                    RECEIVER -> rule.broadcast.componentFilter.add(filter)
                    SERVICE -> rule.service.componentFilter.add(filter)
                    ACTIVITY -> rule.activity.componentFilter.add(filter)
                    else -> {}
                }
                callback(component)
            }
            save(packageName, rule)
        }
    }

    override suspend fun removeAll(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit,
    ) {
        if (!rootChecker.isRootAvailable()) {
            Timber.e("Root unavailable, cannot remove rules")
            throw RootUnavailableException()
        }
        Timber.i("Remove rules for ${list.size} components")
        // the list may contain components from different packages (not likely to happen)
        val groupedMap = list.groupBy { it.packageName }
        groupedMap.keys.forEach { packageName ->
            val rule = ruleCache[packageName] ?: load(packageName)
            groupedMap[packageName]?.forEach componentLoop@{ component ->
                val filter = ComponentFilter(component.flattenToString())
                val type = componentTypeResolver.getComponentType(packageName, component.className)
                if (type == PROVIDER) {
                    Timber.d("Cannot remove IFW rule for $component")
                    return@componentLoop
                }
                when (type) {
                    RECEIVER -> rule.broadcast.componentFilter.remove(filter)
                    SERVICE -> rule.service.componentFilter.remove(filter)
                    ACTIVITY -> rule.activity.componentFilter.remove(filter)
                    else -> {}
                }
                callback(component)
            }
            save(packageName, rule)
        }
    }

    override suspend fun getComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean {
        val rule = ruleCache[packageName] ?: load(packageName)
        val formattedName = formatName(packageName, componentName)
        for (receiver in rule.broadcast.componentFilter) {
            if (formattedName == receiver.name) {
                return false
            }
        }
        for (service in rule.service.componentFilter) {
            if (formattedName == service.name) {
                return false
            }
        }
        for (activity in rule.activity.componentFilter) {
            if (formattedName == activity.name) {
                return false
            }
        }
        return true
    }

    private fun formatName(packageName: String, name: String): String = "$packageName/$name"

    override fun resetCache() {
        Timber.d("Reset IFW cache")
        ruleCache.clear()
    }
}
