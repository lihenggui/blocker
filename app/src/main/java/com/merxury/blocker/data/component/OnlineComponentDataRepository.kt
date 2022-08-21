package com.merxury.blocker.data.component

import android.content.Context
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class OnlineComponentDataRepository @Inject constructor(private val service: OnlineComponentDataService) {
    private val logger = XLog.tag("OnlineComponentDataFetcher")
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.e(throwable)
    }

    // Read from local file first, if not found, read from remote file
    // If remote file is not found, return null
    // If remote file is found, save it to local file and return it
    suspend fun getComponentData(
        context: Context,
        name: String,
        loadFromCacheOnly: Boolean = true,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): OnlineComponentData? {
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
                return@withContext Gson().fromJson(content, OnlineComponentData::class.java)
            } else {
                if (!loadFromCacheOnly) {
                    try {
                        // Cache missed, fetch from remote
                        val content =
                            service.getOnlineComponentData(relativePath) ?: return@withContext null
                        // Save to cache folder
                        val formatter = GsonBuilder().setPrettyPrinting().create()
                        val json = formatter.toJson(content)
                        if (!destination.exists()) {
                            val folder = name.split(".")
                                .dropLast(1)
                                .joinToString("/")
                            context.cacheDir.resolve(ROOT_FOLDER + folder).mkdirs()
                        }
                        destination.writeText(json)
                        return@withContext content
                    } catch (e: Exception) {
                        logger.e("Failed to fetch online component data", e)
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
    ): OnlineComponentData? {
        return withContext(Dispatchers.IO + exceptionHandler) {
            val folder = context.filesDir.resolve(USER_GENERATED_FOLDER)
            val relativePath = name.replace(".", "/")
                .plus(".json")
            val destination = folder.resolve(relativePath)
            if (destination.exists()) {
                val content = destination.readText()
                return@withContext Gson().fromJson(content, OnlineComponentData::class.java)
            } else {
                return@withContext null
            }
        }
    }

    suspend fun saveUserGeneratedComponentDetail(
        context: Context,
        onlineComponentData: OnlineComponentData
    ): Boolean {
        return withContext(Dispatchers.IO + exceptionHandler) {
            // Make root folder first
            val rootFolder = context.filesDir.resolve(USER_GENERATED_FOLDER)
            if (!rootFolder.exists()) {
                rootFolder.mkdirs()
            }
            // Make new directory according to package name
            val packageNamePath = onlineComponentData.name?.split(".")
                ?.dropLast(1)
                ?.joinToString("/") ?: return@withContext false
            val packageFolder = rootFolder.resolve(packageNamePath)
            if (!packageFolder.exists()) {
                packageFolder.mkdirs()
            }
            // Decide file name
            val fileName = onlineComponentData.name?.split(".")
                ?.last()?.plus(EXTENSION) ?: return@withContext false
            val destination = packageFolder.resolve(fileName)
            val formatter = GsonBuilder().setPrettyPrinting().create()
            val json = formatter.toJson(onlineComponentData)
            try {
                destination.writeText(json)
            } catch (e: Exception) {
                logger.e("Failed to save user generated component data", e)
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