package com.merxury.blocker.work

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.merxury.blocker.R
import com.merxury.blocker.data.instantinfo.InstantComponentInfo
import com.merxury.blocker.data.instantinfo.InstantComponentInfoDao
import com.merxury.blocker.util.NotificationUtil
import com.merxury.blocker.util.PreferenceUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Okio
import java.io.File
import java.io.IOException
import java.util.Scanner
import javax.inject.Inject

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
                val scanner = Scanner(file)
                while (scanner.hasNextLine()) {
                    val line = scanner.nextLine()
                    importData(line)
                }
            } catch (e: IOException) {
                logger.e("Can't import rules to database")
                return@withContext false
            }
            true
        }
    }

    private fun importData(line: String) {
        // Parse CSV files directly
        val (appId, packagePath, componentName, description, recommendToBlock) = line.split(
            ",",
            ignoreCase = false,
            limit = 5
        )
        val result = dao.find(appId, packagePath, componentName)
        if (result == null) {
            val recommended = recommendToBlock.toIntOrNull() == 1
            val component =
                InstantComponentInfo(appId, packagePath, componentName, description, recommended)
            dao.insert(component)
        }
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
        val onlineSource = PreferenceUtil.getOnlineSourceType(applicationContext)
        val path = "online/components/zh-cn/${set.filename}"
        val url = onlineSource.baseUrl + path
        val request = Request.Builder()
            .url(url)
            .build()
        try {
            val source = okHttpClient.newCall(request).execute().body()?.source() ?: run {
                logger.e("Can't download online set content")
                return
            }
            val downloadFile = File(applicationContext.cacheDir, set.filename)
            val sink = Okio.buffer(Okio.sink(downloadFile))
            sink.writeAll(source)
            sink.close()
        } catch (e: IOException) {
            logger.e("Can't download online set content", e)
        }
    }

    private fun getOnlineSetInfo(): Set? {
        val source = PreferenceUtil.getOnlineSourceType(applicationContext)
        val path = "online/components/zh-cn/$SET_INFO_FILE_NAME"
        val url = source.baseUrl + path
        try {
            // Get from online
            val request = Request.Builder()
                .url(url)
                .build()
            val content = okHttpClient.newCall(request)
                .execute()
                .body()
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

    companion object {
        private const val SET_INFO_FILE_NAME = "setinfo.json"
    }
}

data class Set(
    val filename: String = "",
    val date: Long = 0L,
    val version: Long = 0L
)