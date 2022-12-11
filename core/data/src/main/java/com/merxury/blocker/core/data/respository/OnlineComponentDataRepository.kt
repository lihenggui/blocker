/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.core.data.respository

import android.content.Context
import com.merxury.blocker.core.network.model.NetworkComponentDetail
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

// TODO rewrite
class OnlineComponentDataRepository @Inject constructor() {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable)
    }

    // Read from local file first, if not found, read from remote file
    // If remote file is not found, return null
    // If remote file is found, save it to local file and return it
    suspend fun getComponentData(
        context: Context,
        name: String,
        loadFromCacheOnly: Boolean = true,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): NetworkComponentDetail? {
        if (name.isEmpty()) {
            return null
        }
        return withContext(dispatcher + exceptionHandler) {
            // Load user generated rule first
            if (loadFromCacheOnly) {
                val userRule = getUserGeneratedComponentDetail(context, name)
                if (userRule != null) {
                    return@withContext userRule
                }
            }
            // If there is no user generated rule, load from local cache
            val relativePath = name.replace(".", "/")
                .plus(EXTENSION)
            val destination = context.cacheDir.resolve(ROOT_FOLDER + relativePath)

            if (destination.exists() && loadFromCacheOnly) {
                // Hit cache, return cached value
                val content = destination.readText()
                return@withContext Json { ignoreUnknownKeys = true }.decodeFromString(content)
            } else {
                if (!loadFromCacheOnly) {
                    try {
//                        // Cache missed, fetch from remote
//                        val content =
//                            service.getOnlineComponentData(relativePath) ?: return@withContext null
//                        // Save to cache folder
//                        val formatter = GsonBuilder().setPrettyPrinting().create()
//                        val json = formatter.toJson(content)
//                        if (!destination.exists()) {
//                            val folder = name.split(".")
//                                .dropLast(1)
//                                .joinToString("/")
//                            context.cacheDir.resolve(ROOT_FOLDER + folder).mkdirs()
//                        }
//                        destination.writeText(json)
//                        return@withContext content
                        return@withContext null
                    } catch (e: Exception) {
                        Timber.e("Failed to fetch online component data", e)
                        return@withContext null
                    }
                } else {
                    // Miss cache, but not force refresh, return null
                    return@withContext null
                }
            }
        }
    }

    suspend fun getUserGeneratedComponentDetail(
        context: Context,
        name: String
    ): NetworkComponentDetail? {
        return withContext(Dispatchers.IO + exceptionHandler) {
            val folder = context.filesDir.resolve(USER_GENERATED_FOLDER)
            val relativePath = name.replace(".", "/")
                .plus(".json")
            val destination = folder.resolve(relativePath)
            if (destination.exists()) {
                val content = destination.readText()
                return@withContext Json.decodeFromString(content)
            } else {
                return@withContext null
            }
        }
    }

    suspend fun saveUserGeneratedComponentDetail(
        context: Context,
        networkComponentDetail: NetworkComponentDetail
    ): Boolean {
        return withContext(Dispatchers.IO + exceptionHandler) {
            // Make root folder first
            val rootFolder = context.filesDir.resolve(USER_GENERATED_FOLDER)
            if (!rootFolder.exists()) {
                rootFolder.mkdirs()
            }
            // Make new directory according to package name
            val packageNamePath = networkComponentDetail.name.split(".")
                .dropLast(1)
                ?.joinToString("/") ?: return@withContext false
            val packageFolder = rootFolder.resolve(packageNamePath)
            if (!packageFolder.exists()) {
                packageFolder.mkdirs()
            }
            // Decide file name
            val fileName = networkComponentDetail.name.split(".")
                .last()?.plus(EXTENSION) ?: return@withContext false
            val destination = packageFolder.resolve(fileName)
            val content = Json.encodeToString(networkComponentDetail)
            try {
                destination.writeText(content)
            } catch (e: Exception) {
                Timber.e("Failed to save user generated component data", e)
                return@withContext false
            }
            return@withContext true
        }
    }

    companion object {
        const val EXTENSION = ".json"
        const val USER_GENERATED_FOLDER = "user_generated_components/"
        const val ROOT_FOLDER = "components/"
    }
}
