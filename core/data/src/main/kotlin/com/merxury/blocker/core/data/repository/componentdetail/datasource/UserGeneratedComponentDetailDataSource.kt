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

package com.merxury.blocker.core.data.repository.componentdetail.datasource

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.di.GeneratedRuleBaseFolder
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.data.ComponentDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val EXTENSION = "json"

@Singleton
internal class UserGeneratedComponentDetailDataSource @Inject constructor(
    private val userDataRepository: UserDataRepository,
    private val components: ComponentStatusRepository,
    @FilesDir private val filesDir: File,
    @GeneratedRuleBaseFolder private val generatedRuleBaseFolder: String,
    private val json: Json,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val _eventFlow =
        MutableSharedFlow<ComponentDetail>(replay = 1, onBufferOverflow = DROP_OLDEST)
    val eventFlow = _eventFlow.asSharedFlow()

    fun getByApp(app: AppKey): Flow<List<ComponentDetail>> = components.components(app)
        .map { statuses -> statuses.map { it.declaration.id.className }.toSet() }
        .distinctUntilChanged()
        .map { names -> findByNames(names, userDataRepository.getLibDisplayLanguage()).values.toList() }
        .flowOn(ioDispatcher)

    suspend fun findByNames(names: Set<String>, language: String): Map<String, ComponentDetail> {
        val workingDir = filesDir.resolve(generatedRuleBaseFolder).resolve(language)
        if (!workingDir.isDirectory) return emptyMap()
        return buildMap {
            for (name in names) {
                currentCoroutineContext().ensureActive()
                readDetail(workingDir, name)?.let { put(name, it) }
            }
        }
    }

    private fun readDetail(workingDir: File, name: String): ComponentDetail? {
        val file = workingDir.resolve(name.replace(".", File.separator) + ".$EXTENSION")
        if (!file.isFile) return null
        return try {
            json.decodeFromString<ComponentDetail>(file.readText())
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Cannot decode user component detail %s", file)
            null
        } catch (e: IOException) {
            Timber.w(e, "Cannot read user component detail %s", file)
            null
        }
    }

    fun saveComponentData(component: ComponentDetail): Flow<Boolean> = flow {
        val workingDir = getWorkingDirWithLang()
        val name = component.name
        val path = name.replace(".", File.separator)
            .plus(".$EXTENSION")
        try {
            if (!workingDir.exists()) {
                workingDir.mkdirs()
            }
            val file = workingDir.resolve(path)
            if (!file.exists()) {
                file.parentFile?.mkdirs()
            }
            val content = json.encodeToString(component)
            file.writeText(content)
            val result = _eventFlow.tryEmit(component)
            if (result) {
                Timber.d("Successfully emit event for component detail: $name")
            } else {
                Timber.d("Failed to emit event for component detail: $name")
            }
            emit(true)
        } catch (e: IOException) {
            Timber.e(e, "Failed to save component detail: $name")
            emit(false)
        }
    }
        .flowOn(ioDispatcher)

    private suspend fun getWorkingDirWithLang(): File = filesDir.resolve(generatedRuleBaseFolder)
        .resolve(userDataRepository.getLibDisplayLanguage())
}
