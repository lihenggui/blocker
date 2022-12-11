/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.core.rule.work

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
import com.merxury.blocker.core.utils.FileUtils
import com.merxury.blocker.util.NotificationUtil
import com.merxury.blocker.util.StorageUtil
import com.merxury.blocker.util.ToastUtil
import com.merxury.ifw.util.StorageUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportIfwRulesWork(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val logger = XLog.tag("ExportIfwRulesWork")

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!StorageUtil.isSavedFolderReadable(applicationContext)) {
            ToastUtil.showToast(R.string.export_ifw_failed_message, Toast.LENGTH_LONG)
            return@withContext Result.failure()
        }
        logger.i("Start to export IFW rules.")
        var current = 0
        try {
            val ifwFolder = StorageUtils.getIfwFolder()
            val files = FileUtils.listFiles(ifwFolder)
            val total = files.count()
            files.forEach {
                logger.i("Export $it")
                val filename = it.split(File.separator).last()
                setForeground(updateNotification(filename, current, total))
                val content = FileUtils.read(ifwFolder + it)
                StorageUtil.saveIfwToStorage(applicationContext, filename, content)
                current++
            }
        } catch (e: Exception) {
            logger.e("Failed to export IFW rules", e)
            ToastUtil.showToast(R.string.export_ifw_failed_message, Toast.LENGTH_LONG)
            return@withContext Result.failure()
        }
        logger.i("Export IFW rules finished, success count = $current.")
        val message = applicationContext.getString(R.string.export_ifw_successful_message, current)
        ToastUtil.showToast(message, Toast.LENGTH_LONG)
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
