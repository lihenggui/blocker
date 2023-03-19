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

package com.merxury.blocker.core.data.respository.onlinecomponent

import android.content.Context
import com.merxury.blocker.core.data.model.asEntity
import com.merxury.blocker.core.database.cmpdetail.ComponentDetailDao
import com.merxury.blocker.core.database.cmpdetail.ComponentDetailEntity
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.merxury.blocker.core.network.model.NetworkComponentDetail
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.result.asResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

const val FILE_EXTENSION = ".json"
const val USER_GENERATED_FOLDER = "user_generated_components/"

class OnlineComponentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val network: BlockerNetworkDataSource,
    private val componentDetailDao: ComponentDetailDao,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ComponentDataRepository {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable)
    }

    override suspend fun getNetworkComponentData(
        name: String,
    ): Flow<Result<NetworkComponentDetail>> {
        val relativePath = name.replace(".", "/")
            .plus(FILE_EXTENSION)
        return flow {
            emit(network.getComponentData(relativePath))
        }
            .asResult()
    }

    override suspend fun getLocalComponentData(
        name: String,
    ): ComponentDetailEntity? {
        return componentDetailDao.getComponentDetail(name)
            .firstOrNull()
            ?.firstOrNull()
    }

    override suspend fun getUserGeneratedComponentDetail(
        name: String,
    ): NetworkComponentDetail? {
        return withContext(ioDispatcher + exceptionHandler) {
            val folder = context.filesDir.resolve(USER_GENERATED_FOLDER)
            val relativePath = name.replace(".", "/")
                .plus(FILE_EXTENSION)
            val destination = folder.resolve(relativePath)
            if (destination.exists()) {
                val content = destination.readText()
                return@withContext Json.decodeFromString(content)
            } else {
                return@withContext null
            }
        }
    }

    override suspend fun saveComponentAsCache(
        component: NetworkComponentDetail,
    ) {
        Timber.d("Save network component info to db: ${component.name}")
        componentDetailDao.insertComponentDetail(component.asEntity())
    }

    override suspend fun saveUserGeneratedComponentDetail(
        componentDetail: NetworkComponentDetail,
    ): Boolean {
        return withContext(ioDispatcher + exceptionHandler) {
            val simpleName = componentDetail.name.split(".").last()
            // Make root folder first
            val rootFolder = context.filesDir.resolve(USER_GENERATED_FOLDER)
            if (!rootFolder.exists()) {
                rootFolder.mkdirs()
            }
            // Make new directory according to package name
            val packageNamePath = componentDetail.name
                .replace(".", "/")
            val packageFolder = rootFolder.resolve(packageNamePath)
            if (!packageFolder.exists()) {
                packageFolder.mkdirs()
            }
            // Decide file name
            val fileName = simpleName.plus(FILE_EXTENSION)
            val destination = packageFolder.resolve(fileName)
            val content = Json.encodeToString(componentDetail)
            try {
                destination.writeText(content)
            } catch (e: IOException) {
                Timber.e(e, "Failed to save user generated component data")
                return@withContext false
            }
            return@withContext true
        }
    }
}
