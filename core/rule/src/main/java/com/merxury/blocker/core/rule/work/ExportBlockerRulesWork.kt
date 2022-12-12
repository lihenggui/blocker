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

package com.merxury.blocker.core.rule.work

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.merxury.blocker.core.PreferenceUtil
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.Rule
import com.merxury.blocker.core.rule.util.NotificationUtil
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.ApplicationUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class ExportBlockerRulesWork(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    override suspend fun doWork(): Result {
        // Check storage permission first
        if (!StorageUtil.isSavedFolderReadable(applicationContext)) {
//            ToastUtil.showToast(R.string.failed_to_back_up, Toast.LENGTH_LONG)
            return Result.failure()
        }
        // Notify users that work is being started
        Timber.i("Start to backup app rules")
        setForeground(updateNotification("", 0, 0))
        // Backup logic
        val shouldBackupSystemApp = PreferenceUtil.shouldBackupSystemApps(applicationContext)
        val savedPath =
            PreferenceUtil.getSavedRulePath(applicationContext) ?: return Result.failure()
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
                    Rule.export(applicationContext, it.packageName, savedPath)
                    current++
                }
            } catch (e: Exception) {
                // Notify users that something bad happens
//                ToastUtil.showToast(R.string.failed_to_back_up, Toast.LENGTH_LONG)
                Timber.e("Failed to export blocker rules", e)
                return@withContext Result.failure()
            }
            // Success, show a toast then cancel notifications
//            ToastUtil.showToast(R.string.backup_finished, Toast.LENGTH_LONG)
            Timber.i("Backup app rules finished.")
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
            .setSmallIcon(com.merxury.blocker.core.common.R.drawable.ic_blocker_notification)
            .setProgress(total, current, false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()
        return ForegroundInfo(NotificationUtil.PROCESSING_NOTIFICATION_ID, notification)
    }
}
