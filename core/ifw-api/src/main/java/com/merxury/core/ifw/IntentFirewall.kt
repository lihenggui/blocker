/*
 * Copyright 2023 Blocker
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
import android.content.pm.PackageManager
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.FileUtils
import com.merxury.blocker.core.utils.PermissionUtils
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XML
import timber.log.Timber
import javax.inject.Inject

private const val EXTENSION = ".xml"

class IntentFirewall @Inject constructor(
    private val pm: PackageManager,
    private val xmlParser: XML,
    @Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
) : IIntentFirewall {
    // Cache for the loaded rules to avoid IO operations
    // Key: package name, Value: Rules
    private val ruleCache: MutableMap<String, Rules> = mutableMapOf()

    private suspend fun load(packageName: String): Rules = withContext(dispatcher) {
        val filename = "$packageName$EXTENSION"
        val destFile = SuFile(IfwStorageUtils.ifwFolder + filename)
        if (!PermissionUtils.isRootAvailable()) {
            Timber.v("Root unavailable, cannot load rule")
            return@withContext Rules()
        }
        if (!destFile.exists()) {
            Timber.v("Rule file $filename not exists")
            return@withContext Rules()
        }
        return@withContext try {
            Timber.v("Load rule from $destFile")
            val input = SuFileInputStream.open(destFile)
            val fileContent = input.readBytes().toString(Charsets.UTF_8)
            val rule = xmlParser.decodeFromString<Rules>(fileContent)
            ruleCache[packageName] = rule
            rule
        } catch (e: SerializationException) {
            Timber.e(e, "Failed to decode $destFile")
            return@withContext Rules()
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "the decoded input is not a valid instance of Rules: $destFile")
            return@withContext Rules()
        } catch (e: Exception) {
            Timber.e(e, "Error reading rules file $destFile")
            return@withContext Rules()
        }
    }

    override suspend fun save(packageName: String, rule: Rules) = withContext(dispatcher) {
        val isActivityEmpty = rule.activity.componentFilter.isEmpty()
        val isBroadcastEmpty = rule.broadcast.componentFilter.isEmpty()
        val isServiceEmpty = rule.service.componentFilter.isEmpty()
        if (isActivityEmpty && isBroadcastEmpty && isServiceEmpty) {
            // If there is no rules presented, delete rule file (if exists)
            clear(packageName)
            return@withContext
        }
        val filename = "$packageName$EXTENSION"
        val destFile = SuFile(IfwStorageUtils.ifwFolder + filename)
        // Write xml content to file
        val fileContent = xmlParser.encodeToString(rule)
        SuFileOutputStream.open(destFile).use {
            // Write file content to output stream
            it.write(fileContent.toByteArray(Charsets.UTF_8))
        }
        FileUtils.chmod(destFile.absolutePath, 644, false)
        ruleCache.remove(packageName)
        Timber.i("Saved IFW rules to $destFile")
    }

    override suspend fun clear(packageName: String): Unit = withContext(dispatcher) {
        val filename = "$packageName$EXTENSION"
        val destFile = SuFile(IfwStorageUtils.ifwFolder + filename)
        if (!PermissionUtils.isRootAvailable()) {
            throw RootUnavailableException()
        }
        Timber.d("Clear IFW rule $filename")
        if (destFile.exists()) {
            destFile.delete()
        }
        ruleCache.remove(packageName)
    }

    override suspend fun add(
        packageName: String,
        componentName: String,
    ): Boolean {
        if (!PermissionUtils.isRootAvailable()) {
            Timber.e("Root unavailable, cannot add rule")
            throw RootUnavailableException()
        }
        Timber.i("Add rule for ${formatName(packageName, componentName)}")
        val rule = ruleCache[packageName] ?: load(packageName)
        when (getComponentType(pm, packageName, componentName)) {
            RECEIVER -> rule.broadcast.componentFilter.add(ComponentFilter(componentName))
            SERVICE -> rule.service.componentFilter.add(ComponentFilter(componentName))
            ACTIVITY -> rule.activity.componentFilter.add(ComponentFilter(componentName))
            else -> return false
        }
        save(packageName, rule)
        return true
    }

    override suspend fun remove(
        packageName: String,
        componentName: String,
    ): Boolean {
        if (!PermissionUtils.isRootAvailable()) {
            Timber.e("Root unavailable, cannot remove rule")
            throw RootUnavailableException()
        }
        Timber.i("Remove rule for ${formatName(packageName, componentName)}")
        val rule = ruleCache[packageName] ?: load(packageName)
        when (getComponentType(pm, packageName, componentName)) {
            RECEIVER -> rule.broadcast.componentFilter.remove(ComponentFilter(componentName))
            SERVICE -> rule.service.componentFilter.remove(ComponentFilter(componentName))
            ACTIVITY -> rule.activity.componentFilter.remove(ComponentFilter(componentName))
            else -> return false
        }
        save(packageName, rule)
        return true
    }

    override suspend fun addAll(
        list: List<ComponentName>,
        callback: suspend (ComponentName) -> Unit) {
        if (!PermissionUtils.isRootAvailable()) {
            Timber.e("Root unavailable, cannot add rules")
            throw RootUnavailableException()
        }
        Timber.i("Add rules for ${list.size} components")
        // the list may contain components from different packages (not likely to happen)
        val groupedMap = list.groupBy { it.packageName }
        groupedMap.keys.forEach { packageName ->
            val rule = ruleCache[packageName] ?: load(packageName)
            groupedMap[packageName]?.forEach { component ->
                val filter = ComponentFilter(component.flattenToString())
                when (getComponentType(pm, packageName, component.className)) {
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
        callback: suspend (ComponentName) -> Unit) {
        if (!PermissionUtils.isRootAvailable()) {
            Timber.e("Root unavailable, cannot remove rules")
            throw RootUnavailableException()
        }
        Timber.i("Remove rules for ${list.size} components")
        // the list may contain components from different packages (not likely to happen)
        val groupedMap = list.groupBy { it.packageName }
        groupedMap.keys.forEach { packageName ->
            val rule = ruleCache[packageName] ?: load(packageName)
            groupedMap[packageName]?.forEach { component ->
                val filter = ComponentFilter(component.flattenToString())
                when (getComponentType(pm, packageName, component.className)) {
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

    private suspend fun getComponentType(
        pm: PackageManager,
        packageName: String,
        componentName: String,
    ): ComponentType = withContext(cpuDispatcher) {
        val formattedName = formatName(packageName, componentName)
        // Check by type, start from receiver
        val receivers = ApplicationUtil.getReceiverList(pm, packageName, dispatcher)
        for (receiver in receivers) {
            if (formattedName == receiver.name) {
                return@withContext RECEIVER
            }
        }
        val services = ApplicationUtil.getServiceList(pm, packageName, dispatcher)
        for (service in services) {
            if (formattedName == service.name) {
                return@withContext SERVICE
            }
        }
        val activities = ApplicationUtil.getActivityList(pm, packageName, dispatcher)
        for (activity in activities) {
            if (formattedName == activity.name) {
                return@withContext ACTIVITY
            }
        }
        val providers = ApplicationUtil.getProviderList(pm, packageName, dispatcher)
        for (provider in providers) {
            if (formattedName == provider.name) {
                return@withContext PROVIDER
            }
        }
        Timber.e("Cannot find component type for $formattedName")
        return@withContext PROVIDER
    }

    private fun formatName(packageName: String, name: String): String {
        return "$packageName/$name"
    }
}
