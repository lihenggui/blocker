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
import com.merxury.blocker.core.data.repository.userdata.observeLibDisplayLanguage
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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

    fun getByApp(app: AppKey): Flow<List<ComponentDetail>> {
        val names = components.components(app)
            .map { statuses -> statuses.map { it.declaration.id.className }.toSet() }
            .distinctUntilChanged()
        return combine(names, userDataRepository.observeLibDisplayLanguage(), eventFlow.map { Unit }.onStart { emit(Unit) }) { currentNames, language, _ ->
            findByNames(currentNames, language).values.toList()
        }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)
    }

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
        val file = workingDir.detailFile(name) ?: return null
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
        val name = component.name
        val file = filesDir.resolve(generatedRuleBaseFolder)
            .resolve(userDataRepository.getLibDisplayLanguage())
            .detailFile(name)
        if (file == null) {
            Timber.w("Invalid component name for a user detail")
            emit(false)
            return@flow
        }
        val saved = try {
            val parent = checkNotNull(file.parentFile)
            if (!parent.mkdirs() && !parent.isDirectory) throw IOException("Cannot create $parent")
            val temporary = File.createTempFile("component-", ".tmp", parent)
            try {
                temporary.writeText(json.encodeToString(component))
                if (!temporary.renameTo(file)) throw IOException("Cannot replace $file")
            } finally {
                if (temporary.exists() && !temporary.delete()) Timber.w("Cannot remove %s", temporary)
            }
            _eventFlow.tryEmit(component)
            true
        } catch (e: IOException) {
            Timber.e(e, "Failed to save component detail: $name")
            false
        }
        emit(saved)
    }
        .flowOn(ioDispatcher)

    private fun File.detailFile(name: String): File? {
        if (name.isBlank() || name.startsWith('.') || name.endsWith('.') || ".." in name ||
            '/' in name || '\\' in name || '\u0000' in name
        ) {
            return null
        }
        return resolve(name.replace('.', File.separatorChar) + ".$EXTENSION")
    }
}
