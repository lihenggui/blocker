package com.merxury.blocker.work

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.merxury.blocker.R
import com.merxury.blocker.rule.Rule
import com.merxury.blocker.rule.entity.BlockerRule
import com.merxury.blocker.util.NotificationUtil
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.ToastUtil
import com.merxury.libkit.utils.ApplicationUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

class ImportBlockerRuleWork(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    private val logger = XLog.tag("ImportBlockerRuleWork")

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        logger.i("Start to import app rules")
        var successCount = 0
        try {
            val context = applicationContext
            val folderUri = PreferenceUtil.getSavedRulePath(context)
            if (folderUri == null) {
                logger.e("Cannot read rule path")
                ToastUtil.showToast(R.string.dir_is_invalid, Toast.LENGTH_LONG)
                return@withContext Result.failure()
            }
            val documentDir = DocumentFile.fromTreeUri(context, folderUri)
            if (documentDir == null) {
                logger.e("Cannot create DocumentFile")
                ToastUtil.showToast(R.string.dir_is_invalid, Toast.LENGTH_LONG)
                return@withContext Result.failure()
            }
            val files = documentDir.listFiles()
                .filter { it.name?.endsWith(Rule.EXTENSION) == true }
            val total = files.count()
            var current = 1
            files.forEach {
                logger.i("Import ${it.uri}")
                val inputStream =
                    InputStreamReader(context.contentResolver.openInputStream(it.uri))
                val rule = Gson().fromJson(inputStream, BlockerRule::class.java)
                if (!ApplicationUtil.isAppInstalled(context.packageManager, rule.packageName)) {
                    logger.w("App ${rule.packageName} is not installed, skipping")
                    current++
                    return@forEach
                }
                setForeground(updateNotification(rule.packageName ?: "", current, total))
                val result = Rule.import(context, rule)
                if (result) {
                    successCount++
                }
                current++
            }
            logger.i("Import rules finished.")
        } catch (e: Exception) {
            logger.e("Failed to import blocker rules", e)
            ToastUtil.showToast(R.string.import_failed_message, Toast.LENGTH_LONG)
            return@withContext Result.failure()
        }
        if (successCount == 0) {
            logger.i("No rules were imported.")
            ToastUtil.showToast(R.string.no_rules_imported, Toast.LENGTH_LONG)
            return@withContext Result.failure()
        } else {
            logger.i("Imported $successCount rules.")
            ToastUtil.showToast(R.string.import_successfully, Toast.LENGTH_LONG)
            return@withContext Result.success()
        }
    }

    private fun updateNotification(name: String, current: Int, total: Int): ForegroundInfo {
        val id = NotificationUtil.PROCESSING_INDICATOR_CHANNEL_ID
        val title = applicationContext.getString(R.string.import_app_rules_please_wait)
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