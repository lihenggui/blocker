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

package com.merxury.blocker.core.data.respository.componentdetail.datasource

import com.merxury.blocker.core.data.di.RuleBaseFolder
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.utils.listFilesRecursively
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.SortedMap
import javax.inject.Inject
import javax.inject.Singleton

private const val EXTENSION = "json"
private const val COMPONENT_FOLDER = "components"
private const val BUNDLE_FOLDER = "component-details"

@Singleton
internal class LocalComponentDetailDataSource @Inject constructor(
    private val userDataRepository: UserDataRepository,
    private val json: Json,
    @FilesDir private val filesDir: File,
    @RuleBaseFolder private val ruleBaseFolder: String,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ComponentDetailDataSource {
    private val bundleMutex = Mutex()
    private var cachedKey: BundleKey? = null
    private var cachedBundle: SortedMap<String, ComponentDetail>? = null

    private val workingDir: File by lazy {
        filesDir.resolve(ruleBaseFolder)
            .resolve(COMPONENT_FOLDER)
    }

    override fun getByPackageName(packageName: String): Flow<List<ComponentDetail>> = flow {
        val language = userDataRepository.getLibDisplayLanguage()
        val bundle = getBundle(language)
        if (bundle != null) {
            val prefix = "$packageName."
            emit(bundle.tailMap(prefix).values.takeWhile { it.name.startsWith(prefix) })
            return@flow
        }
        val path = packageName.replace(".", File.separator)
            .plus(File.separator)
        val folder = workingDir
            .resolve(language)
            .resolve(path)
        if (!folder.exists()) {
            Timber.v("Component folder for $packageName does not exist")
            emit(emptyList())
            return@flow
        }
        // Check if folder contains any json file
        val files = folder.listFilesRecursively()
            .filter { it.extension == EXTENSION }
        if (files.isEmpty()) {
            Timber.v("No component info for $packageName found")
            emit(emptyList())
            return@flow
        }
        val componentDetails = mutableListOf<ComponentDetail>()
        files.forEach { file ->
            try {
                val componentDetail = json.decodeFromString<ComponentDetail>(file.readText())
                componentDetails.add(componentDetail)
            } catch (e: SerializationException) {
                Timber.e(e, "given JSON string is not a valid JSON input for the type")
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "decoded input cannot be represented as a valid instance of type")
            }
        }
        emit(componentDetails)
    }
        .flowOn(ioDispatcher)

    override fun getByComponentName(name: String): Flow<ComponentDetail?> = flow {
        val language = userDataRepository.getLibDisplayLanguage()
        val bundle = getBundle(language)
        if (bundle != null) {
            emit(bundle[name])
            return@flow
        }
        if (!workingDir.exists()) {
            Timber.w("The component folder does not exist")
            emit(null)
            return@flow
        }
        val path = name.replace(".", File.separator)
            .plus(".$EXTENSION")
        val file = workingDir
            .resolve(language)
            .resolve(path)
        if (file.exists()) {
            try {
                val componentDetail = json.decodeFromString<ComponentDetail>(file.readText())
                emit(componentDetail)
            } catch (e: SerializationException) {
                Timber.e("Given JSON string is not a valid JSON input for the type")
                Timber.e("File path: $file, error: ${e.message}")
                emit(null)
            } catch (e: IllegalArgumentException) {
                Timber.e("Decoded input cannot be represented as a valid instance of type, file path: $file")
                Timber.e("File path: $file, error: ${e.message}")
                emit(null)
            }
        } else {
            emit(null)
        }
    }
        .flowOn(ioDispatcher)

    private suspend fun getBundle(language: String): SortedMap<String, ComponentDetail>? = bundleMutex.withLock {
        val file = filesDir.resolve(ruleBaseFolder)
            .resolve(BUNDLE_FOLDER)
            .resolve("$language.$EXTENSION")
        if (!file.isFile) {
            cachedKey = null
            cachedBundle = null
            return@withLock null
        }
        val key = BundleKey(language, file.lastModified(), file.length())
        if (key != cachedKey) {
            cachedBundle = loadBundle(file)
            // Remember parse failures too; retry when the file changes.
            cachedKey = key
        }
        cachedBundle
    }

    private fun loadBundle(file: File): SortedMap<String, ComponentDetail>? = try {
        val components = sortedMapOf<String, ComponentDetail>()
        var started = false
        var ended = false
        var hasEntries = false
        // Published bundles have one compact object per line, so a bad entry can be
        // skipped without losing subsequent entries or reading the whole file at once.
        file.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                var entry = line.trim()
                if (entry.isEmpty()) return@forEach
                require(!ended) { "Unexpected content after component detail array" }
                if (!started) {
                    require(entry.startsWith("[")) { "Component detail bundle must be an array" }
                    started = true
                    entry = entry.removePrefix("[").trimStart()
                }
                if (entry == "]" || entry.endsWith("}]")) {
                    ended = true
                    entry = entry.removeSuffix("]").trimEnd()
                }
                if (entry.isEmpty()) return@forEach
                hasEntries = true
                try {
                    val component = json.decodeFromString<ComponentDetail>(entry.removeSuffix(","))
                    components[component.name] = component
                } catch (e: SerializationException) {
                    Timber.e(e, "Invalid component detail entry in $file")
                } catch (e: IllegalArgumentException) {
                    Timber.e(e, "Component detail entry cannot be represented in $file")
                }
            }
        }
        require(started && ended) { "Incomplete component detail array" }
        require(!hasEntries || components.isNotEmpty()) { "No valid component detail entries" }
        components
    } catch (e: IOException) {
        Timber.e(e, "Cannot read component detail bundle $file")
        null
    } catch (e: IllegalArgumentException) {
        Timber.e(e, "Cannot parse component detail bundle $file")
        null
    }

    private data class BundleKey(val language: String, val lastModified: Long, val length: Long)

    override fun saveComponentData(component: ComponentDetail): Flow<Boolean> {
        Timber.e("Not support saving component detail in LocalComponentDetailDataSource")
        return flowOf(false)
    }
}
