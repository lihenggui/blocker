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

import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.utils.FileUtils
import com.merxury.blocker.core.utils.PermissionUtils
import com.merxury.ifw.util.IfwStorageUtils
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.XML
import timber.log.Timber

class IntentFirewall @AssistedInject constructor(
    @Assisted val packageName: String,
    @Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
) : IIntentFirewall {

    private val filename: String = "$packageName$EXTENSION"
    private val destFile = SuFile(IfwStorageUtils.ifwFolder + filename)
    private var rule: Rules = Rules()

    @OptIn(ExperimentalXmlUtilApi::class)
    private val xml = XML {
        policy = JacksonPolicy
    }

    override suspend fun load() = withContext(dispatcher) {
        if (PermissionUtils.isRootAvailable() && destFile.exists()) {
            try {
                val input = SuFileInputStream.open(destFile)
                val fileContent = input.readBytes().toString(Charsets.UTF_8)
                rule = xml.decodeFromString<Rules>(fileContent)
            } catch (e: SerializationException) {
                Timber.e(e, "Failed to decode $destFile")
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "the decoded input is not a valid instance of Rules: $destFile")
            } catch (e: Exception) {
                Timber.e(e, "Error reading rules file $destFile")
            }
        }
        return@withContext this@IntentFirewall
    }

    override suspend fun save() = withContext(dispatcher) {
        val isActivityEmpty = rule.activity.componentFilter.isEmpty()
        val isBroadcastEmpty = rule.broadcast.componentFilter.isEmpty()
        val isServiceEmpty = rule.service.componentFilter.isEmpty()
        if (isActivityEmpty && isBroadcastEmpty && isServiceEmpty) {
            // If there is no rules presented, delete rule file (if exists)
            clear()
            return@withContext
        }
        // Write xml content to file
        val fileContent = xml.encodeToString(rule)
        SuFileOutputStream.open(destFile).use {
            // Write file content to output stream
            it.write(fileContent.toByteArray(Charsets.UTF_8))
        }
        FileUtils.chmod(destFile.absolutePath, 644, false)
        Timber.i("Saved $destFile")
    }

    override suspend fun clear() = withContext(dispatcher) {
        if (!PermissionUtils.isRootAvailable()) {
            throw RootUnavailableException()
        }
        Timber.d("Clear IFW rule $filename")
        if (destFile.exists()) {
            destFile.delete()
        }
        rule = Rules()
    }

    override suspend fun add(
        packageName: String,
        componentName: String,
        type: ComponentType,
    ): Boolean {
        if (!PermissionUtils.isRootAvailable()) {
            Timber.e("Root unavailable, cannot add rule")
            throw RootUnavailableException()
        }
        return when (type) {
            ComponentType.ACTIVITY -> addComponentFilter(packageName, componentName, rule.activity)
            ComponentType.RECEIVER -> addComponentFilter(packageName, componentName, rule.broadcast)
            ComponentType.SERVICE -> addComponentFilter(packageName, componentName, rule.service)
            else -> false
        }
    }

    override suspend fun remove(
        packageName: String,
        componentName: String,
        type: ComponentType,
    ): Boolean {
        if (!PermissionUtils.isRootAvailable()) {
            Timber.e("Root unavailable, cannot remove rule")
            throw RootUnavailableException()
        }
        return when (type) {
            ComponentType.ACTIVITY -> removeComponentFilter(
                packageName,
                componentName,
                rule.activity,
            )

            ComponentType.RECEIVER -> removeComponentFilter(
                packageName,
                componentName,
                rule.broadcast,
            )

            ComponentType.SERVICE -> removeComponentFilter(
                packageName,
                componentName,
                rule.service,
            )

            else -> false
        }
    }

    override suspend fun getComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean {
        val filters = mutableListOf<ComponentFilter>()
        rule.activity.let {
            filters.addAll(it.componentFilter)
        }
        rule.broadcast.let {
            filters.addAll(it.componentFilter)
        }
        rule.service.let {
            filters.addAll(it.componentFilter)
        }
        return getFilterEnableState(packageName, componentName, filters)
    }

    private fun addComponentFilter(
        packageName: String,
        componentName: String,
        component: Component?,
    ): Boolean {
        if (component == null) {
            return false
        }
        val filters = component.componentFilter
        val filterRule = formatName(packageName, componentName)
        // Duplicate filter detection
        for (filter in filters) {
            if (filter.name == filterRule) {
                return false
            }
        }
        filters.add(ComponentFilter(filterRule))
        Timber.i("Added component:$packageName/$componentName")
        return true
    }

    private fun removeComponentFilter(
        packageName: String,
        componentName: String,
        component: Component?,
    ): Boolean {
        if (component == null) {
            return false
        }
        val filters = component.componentFilter
        val filterRule = formatName(packageName, componentName)
        for (filter in ArrayList(filters)) {
            if (filterRule == filter.name) {
                filters.remove(filter)
            }
        }
        return true
    }

    private fun getFilterEnableState(
        packageName: String,
        componentName: String,
        componentFilters: List<ComponentFilter>?,
    ): Boolean {
        if (componentFilters == null) {
            return true
        }
        for (filter in componentFilters) {
            val filterName = formatName(packageName, componentName)
            if (filterName == filter.name) {
                return false
            }
        }
        return true
    }

    private fun formatName(packageName: String, name: String): String {
        return "$packageName/$name"
    }

    companion object {
        private const val EXTENSION = ".xml"
    }
}
