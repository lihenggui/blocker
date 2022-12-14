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

package com.merxury.blocker.work

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.merxury.blocker.R
import com.merxury.blocker.core.database.instantinfo.InstantComponentInfo
import com.merxury.blocker.core.database.instantinfo.InstantComponentInfoDao
import com.merxury.blocker.core.network.model.OnlineSourceType
import com.merxury.blocker.core.rule.util.NotificationUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.apache.commons.csv.CSVFormat

@HiltWorker
class CheckRuleUpdateWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    val dao: InstantComponentInfoDao,
    val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {
    private val logger = XLog.tag("CheckRuleUpdateWork")

    override suspend fun doWork(): Result {
        setForeground(updateNotification(R.string.fetching_online_repository))
        val onlineInfo = getOnlineSetInfo() ?: return Result.failure()
        val localInfo = getLocalSetInfo()
        if (localInfo == null || onlineInfo.date > localInfo.date) {
            saveOnlineSetInfo(onlineInfo)
            getOnlineSetData(onlineInfo)
        }
        if (isLocalSetExist(onlineInfo.filename)) {
            return if (updateDb(onlineInfo)) {
                Result.success()
            } else {
                Result.failure()
            }
        }
        return Result.failure()
    }

    private suspend fun updateDb(set: Set): Boolean {
        setForeground(updateNotification(R.string.updating_database))
        return withContext(Dispatchers.IO) {
            val file = applicationContext.cacheDir.resolve(set.filename)
            if (!file.exists()) {
                logger.e("Can't find online rules file in ${file.absolutePath}")
                return@withContext false
            }
            try {
                importCSV(file)
            } catch (e: IOException) {
                logger.e("Can't import rules to database", e)
                return@withContext false
            }
            true
        }
    }

    @Throws(IOException::class)
    private fun importCSV(file: File) {
        val reader = file.bufferedReader()
        CSVFormat.Builder.create(CSVFormat.DEFAULT)
            .apply { setIgnoreSurroundingSpaces(true) }
            .build()
            .parse(reader)
            .drop(1)
            .map {
                InstantComponentInfo(
                    packagePath = it[0],
                    componentName = it[1],
                    description = it[2],
                    recommendToBlock = it[3].toBoolean()
                )
            }
            .forEach {
                if (dao.find(it.packagePath, it.componentName) == null) {
                    dao.insert(it)
                }
            }
        reader.close()
    }

    private fun updateNotification(@StringRes content: Int): ForegroundInfo {
        val id = NotificationUtil.UPDATE_RULE_CHANNEL_ID
        val title = applicationContext.getString(R.string.checking_rules_updates)
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtil.createUpdateRulesNotificationChannel(applicationContext)
        }
        val notification = NotificationCompat.Builder(applicationContext, id)
            .setContentTitle(title)
            .setTicker(title)
            .setSubText(applicationContext.getText(content))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NotificationUtil.PROCESSING_NOTIFICATION_ID, notification)
    }

    private suspend fun getOnlineSetData(set: Set) {
        setForeground(updateNotification(R.string.download_online_rules))
        val onlineSource = getOnlineSourceType(applicationContext)
        val path = "components/zh-cn/${set.filename}"
        val url = onlineSource.baseUrl + path
        val request = Request.Builder()
            .url(url)
            .build()
        try {
            val source = okHttpClient.newCall(request).execute().body?.source() ?: run {
                logger.e("Can't download online set content")
                return
            }
            val downloadFile = File(applicationContext.cacheDir, set.filename)
            val sink = downloadFile.sink().buffer()
            sink.writeAll(source)
            sink.close()
        } catch (e: IOException) {
            logger.e("Can't download online set content", e)
        }
    }

    private fun getOnlineSetInfo(): Set? {
        val source = getOnlineSourceType(applicationContext)
        val path = "components/zh-cn/$SET_INFO_FILE_NAME"
        val url = source.baseUrl + path
        try {
            // Get from online
            val request = Request.Builder()
                .url(url)
                .build()
            val content = okHttpClient.newCall(request)
                .execute()
                .body
                ?.string()
            // Save to local cache
            return Gson().fromJson(content, Set::class.java)
        } catch (e: JsonSyntaxException) {
            logger.e("Can't parse online set info", e)
            return null
        } catch (e: IOException) {
            logger.e("Can't fetch online set data", e)
            return null
        }
    }

    private fun getLocalSetInfo(): Set? {
        val setInfoFile = applicationContext.cacheDir.resolve(SET_INFO_FILE_NAME)
        if (!setInfoFile.exists()) {
            return null
        }
        return try {
            val content = setInfoFile.readText()
            Gson().fromJson(content, Set::class.java)
        } catch (e: JsonSyntaxException) {
            logger.e("Can't parse local set info", e)
            null
        } catch (e: IOException) {
            logger.e("Can't fetch local set data", e)
            null
        }
    }

    private fun saveOnlineSetInfo(set: Set) {
        val setInfoFile = applicationContext.cacheDir.resolve(SET_INFO_FILE_NAME)
        try {
            if (setInfoFile.exists()) {
                setInfoFile.deleteRecursively()
            }
            setInfoFile.writeText(Gson().toJson(set))
        } catch (e: IOException) {
            logger.e("Can't save local data", e)
        }
    }

    private fun isLocalSetExist(filename: String): Boolean {
        return applicationContext.cacheDir.resolve(filename).exists()
    }

    // TODO use DataStore instead
    private fun getOnlineSourceType(context: Context): OnlineSourceType {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val value = pref.getString(
            context.getString(R.string.key_pref_online_source_type), "GITLAB"
        ).orEmpty()
        return try {
            OnlineSourceType.valueOf(value)
        } catch (e: Exception) {
            OnlineSourceType.GITHUB
        }
    }

    companion object {
        private const val SET_INFO_FILE_NAME = "setinfo.json"
    }
}

data class Set(
    val filename: String = "",
    val date: Long = 0L,
    val version: Long = 0L
)
