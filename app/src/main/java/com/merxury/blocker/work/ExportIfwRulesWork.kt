package com.merxury.blocker.work

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.util.NotificationUtil
import com.merxury.blocker.util.StorageUtil
import com.merxury.blocker.util.ToastUtil
import com.merxury.libkit.utils.FileUtils
import com.merxury.libkit.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ExportIfwRulesWork(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val logger = XLog.tag("ExportIfwRulesWork")

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        logger.i("Start to export IFW rules.")
        var current = 1
        try {
            val ifwFolder = StorageUtils.getIfwFolder()
            val files = FileUtils.listFiles(ifwFolder)
            val total = files.count()
            files.forEach {
                logger.i("Export $it")
                val filename = it.split(File.separator).last()
                updateNotification(filename, current, total)
                val content = FileUtils.read(it)
                StorageUtil.saveIfwToStorage(applicationContext, filename, content)
                current++
            }
        } catch (e: Exception) {
            logger.e("Failed to export IFW rules", e)
            ToastUtil.showToast(R.string.export_ifw_failed_message, Toast.LENGTH_LONG)
            return@withContext Result.failure()
        }
        logger.i("Export IFW rules finished, success count = $current.")
        return@withContext Result.success()
    }

    private fun updateNotification(name: String, current: Int, total: Int): ForegroundInfo {
        val id = NotificationUtil.PROCESSING_INDICATOR_CHANNEL_ID
        val title = applicationContext.getString(R.string.backing_up_ifw_please_wait)
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