package com.merxury.blocker.data.component

import android.content.Context
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class OnlineComponentDataFetcher @Inject constructor(private val service: OnlineComponentDataService) {
    private val logger = XLog.tag("OnlineComponentDataFetcher")

    // Read from local file first, if not found, read from remote file
    // If remote file is not found, return null
    // If remote file is found, save it to local file and return it
    suspend fun getComponentData(
        context: Context,
        name: String,
        forceRefresh: Boolean = false,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): OnlineComponentData? {
        if (name.isEmpty()) {
            return null
        }
        return withContext(dispatcher) {
            val relativePath = name.replace(".", "/")
                .plus(".json")
            val destination = context.cacheDir.resolve(relativePath)
            logger.d("local path = $destination")
            if (destination.exists()) {
                // Hit cache, return cached value
                val content = destination.readText()
                return@withContext Gson().fromJson(content, OnlineComponentData::class.java)
            } else {
                if (forceRefresh) {
                    try {
                        // Miss cache, fetch from remote
                        val content =
                            service.getOnlineComponentData(relativePath) ?: return@withContext null
                        // Save to cache folder
                        val formatter = GsonBuilder().setPrettyPrinting().create()
                        val json = formatter.toJson(content)
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
}