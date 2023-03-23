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

import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

private const val EXTENSION = "json"
private const val BASE_FOLDER = "componentdetail"

class LocalComponentDetailDataSource @Inject constructor(
    filesDir: File,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ComponentDetailDataSource {

    private val workingDir = filesDir.resolve(BASE_FOLDER)
        .resolve("user_generated")

    override fun getComponentDetail(name: String): Flow<ComponentDetail?> = flow {
        val path = name.replace(".", "/")
            .plus(".$EXTENSION")
        if (!workingDir.exists()) {
            workingDir.mkdirs()
        }
        val file = workingDir.resolve(path)
        if (file.exists()) {
            try {
                val componentDetail = Json.decodeFromString<ComponentDetail>(file.readText())
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
    }.flowOn(ioDispatcher)

    // It actually uses Dispatcher.IO
    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun saveComponentData(component: ComponentDetail): Boolean =
        withContext(ioDispatcher) {
            val name = component.name
            val path = name.replace(".", "/")
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
                val content = Json.encodeToString(component)
                file.writeText(content)
                true
            } catch (e: IOException) {
                Timber.e(e, "Failed to save component detail: $name")
                false
            }
        }
}
