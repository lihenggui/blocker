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

import com.merxury.blocker.core.data.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private const val EXTENSION = "json"
private const val BASE_FOLDER = "blocker-general-rules"
class LocalComponentDetailDataSource @Inject constructor(
    private val json: Json,
    @FilesDir private val filesDir: File,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ComponentDetailDataSource {

    private val workingDir = filesDir.resolve(BASE_FOLDER)
    override fun getComponentDetail(name: String): Flow<ComponentDetail?> = flow {
        val path = name.replace(".", "/")
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
    }.flowOn(ioDispatcher)
    override suspend fun saveComponentData(component: ComponentDetail): Boolean {
        Timber.e("Not support saving component detail in LocalComponentDetailDataSource")
        return false
    }
}
