package com.merxury.blocker.work

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.rule.Rule
import com.merxury.blocker.util.NotificationUtil
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.libkit.utils.ApplicationUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportBlockerRulesWork(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val logger = XLog.tag("ExportBlockerRulesWork")

    override suspend fun doWork(): Result {
        setForeground(updateNotification("", 0, 0))
        val shouldBackupSystemApp = PreferenceUtil.shouldBackupSystemApps(applicationContext)
        return withContext(Dispatchers.IO) {
            try {
                val list = if (shouldBackupSystemApp) {
                    ApplicationUtil.getApplicationList(applicationContext)
                } else {
                    ApplicationUtil.getThirdPartyApplicationList(applicationContext)
                }
                val total = list.count()
                var current = 1
                list.forEach {
                    setForeground(updateNotification(it.packageName, current, total))
                    Rule.export(applicationContext, it.packageName)
                    current++
                }
            } catch (e: Exception) {
                logger.e("Failed to export blocker rules", e)
                return@withContext Result.failure()
            }
            return@withContext Result.success()
        }

    }

    private fun updateNotification(name: String, current: Int, total: Int): ForegroundInfo {
        val id = NotificationUtil.PROCESSING_INDICATOR_CHANNEL_ID
        val title = applicationContext.getString(R.string.backing_up_apps_please_wait)
        val cancel = applicationContext.getString(R.string.cancel)
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(getId())
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtil.createProgressingNotificationChannel(applicationContext)
        }
        val notification = NotificationCompat.Builder(applicationContext, id)
            .setContentTitle(title)
            .setTicker(title)
            .setSubText(name)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(total, current, false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()
        return ForegroundInfo(NotificationUtil.PROCESSING_NOTIFICATION_ID, notification)
    }
}