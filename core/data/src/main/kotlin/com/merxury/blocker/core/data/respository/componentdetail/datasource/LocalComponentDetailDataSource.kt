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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private const val EXTENSION = "json"
private const val COMPONENT_FOLDER = "components"

internal class LocalComponentDetailDataSource @Inject constructor(
    private val userDataRepository: UserDataRepository,
    private val json: Json,
    @FilesDir private val filesDir: File,
    @RuleBaseFolder private val ruleBaseFolder: String,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ComponentDetailDataSource {
    private val workingDir: File by lazy {
        filesDir.resolve(ruleBaseFolder)
            .resolve(COMPONENT_FOLDER)
    }

    override fun getByPackageName(packageName: String): Flow<List<ComponentDetail>> = flow {
        val path = packageName.replace(".", File.separator)
            .plus(File.separator)
        val folder = workingDir
            .resolve(userDataRepository.getLibDisplayLanguage())
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
        if (!workingDir.exists()) {
            Timber.w("The component folder does not exist")
            emit(null)
            return@flow
        }
        val path = name.replace(".", File.separator)
            .plus(".$EXTENSION")
        val file = workingDir
            .resolve(userDataRepository.getLibDisplayLanguage())
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

    override fun saveComponentData(component: ComponentDetail): Flow<Boolean> {
        Timber.e("Not support saving component detail in LocalComponentDetailDataSource")
        return flowOf(false)
    }
}
