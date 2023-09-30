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

package com.merxury.blocker.core.data.respository.componentdetail.datasource

import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import com.merxury.blocker.core.data.di.FilesDir
import com.merxury.blocker.core.data.respository.component.CacheComponentDataSource
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.utils.listFilesRecursively
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchService
import javax.inject.Inject

private const val EXTENSION = "json"
private const val BASE_FOLDER = "user-generated-rules"

class UserGeneratedComponentDetailDataSource @Inject constructor(
    private val userDataRepository: UserDataRepository,
    private val componentDataSource: CacheComponentDataSource,
    @FilesDir private val filesDir: File,
    private val json: Json,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ComponentDetailDataSource {

    override fun getByPackageName(packageName: String): Flow<List<ComponentDetail>> = flow {
        val workingDir = getWorkingDirWithLang()
        val customizedComponents = workingDir.listFilesRecursively()
            .map { file ->
                val relativePath = file.relativeTo(workingDir)
                val componentName = relativePath.path
                    .replace(File.separator, ".")
                    .removeSuffix(".$EXTENSION")
                componentName
            }
            .mapNotNull { componentName ->
                componentDataSource.getComponent(packageName, componentName).first()
            }
            .mapNotNull { componentInfo ->
                val path = componentInfo.name.replace(".", File.separator)
                    .plus(".$EXTENSION")
                val file = workingDir.resolve(path)
                if (!file.exists()) {
                    return@mapNotNull null
                }
                return@mapNotNull try {
                    json.decodeFromString<ComponentDetail>(file.readText())
                } catch (e: SerializationException) {
                    Timber.e(
                        e,
                        "Error in decoding contents in ${file.absolutePath} " +
                            "for the ComponentDetail",
                    )
                    null
                } catch (e: IllegalArgumentException) {
                    Timber.e(
                        e,
                        "File ${file.absolutePath} " +
                            "is not valid for a ComponentDetail class",
                    )
                    null
                }
            }
        emit(customizedComponents)
    }
        .flowOn(ioDispatcher)

    override fun getByComponentName(name: String): Flow<ComponentDetail?> = flow {
        val workingDir = getWorkingDirWithLang()
        val path = name.replace(".", File.separator)
            .plus(".$EXTENSION")
        if (!workingDir.exists()) {
            workingDir.mkdirs()
        }
        val file = workingDir.resolve(path)
        if (file.exists()) {
            try {
                val componentDetail = json.decodeFromString<ComponentDetail>(file.readText())
                emit(componentDetail)
            } catch (e: SerializationException) {
                Timber.e(e, "given JSON string is not a valid JSON input for the type")
                emit(null)
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "decoded input cannot be represented as a valid instance of type")
                emit(null)
            }
        } else {
            emit(null)
        }
    }
        .flowOn(ioDispatcher)

    override fun saveComponentData(component: ComponentDetail): Flow<Boolean> = flow {
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
                file.createNewFile()
            }
            val content = json.encodeToString(component)
            file.writeText(content)
            emit(true)
        } catch (e: IOException) {
            Timber.e(e, "Failed to save component detail: $name")
            emit(false)
        }
    }
        .flowOn(ioDispatcher)

    @RequiresApi(VERSION_CODES.O)
    override fun listenToComponentDetailChanges(): Flow<ComponentDetail?> = flow<ComponentDetail?> {
        val watchService: WatchService? =
            try {
                FileSystems.getDefault().newWatchService()
            } catch (e: IOException) {
                Timber.e(e, "Failed to create watch service")
                null
            } catch (e: UnsupportedOperationException) {
                Timber.e(e, "Watch service is not available on this platform")
                null
            }
        if (watchService == null) {
            emit(null)
            return@flow
        }
        val workingDir = getWorkingDirWithLang()
        if (!workingDir.exists()) {
            workingDir.mkdirs()
        }
        val pathToWatch = workingDir.toPath()
        pathToWatch.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
        Timber.d("Start watching file changes in path: $pathToWatch")
        while (true) {
            Timber.v("Waiting for file changes")
            val watchKey = watchService.take()

            for (event in watchKey.pollEvents()) {
                // do something with the events
                Timber.d("Event kind: ${event.kind()}. File affected: ${event.context()}.")
            }

            if (!watchKey.reset()) {
                watchKey.cancel()
                watchService.close()
                break
            }
        }
    }
        .flowOn(ioDispatcher)

    private suspend fun getWorkingDirWithLang(): File {
        val libDisplayLanguage = userDataRepository.userData.first().libDisplayLanguage
        return filesDir.resolve(BASE_FOLDER)
            .resolve(getLibDisplayLanguage(libDisplayLanguage))
    }
}
