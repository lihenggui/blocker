/*
 * Copyright 2023 Blocker
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
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.Rule
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.util.NotificationUtil
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.ApplicationUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class ExportBlockerRulesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    override suspend fun doWork(): Result {
        // Check storage permission first
        val backupPath = inputData.getString(PARAM_FOLDER_PATH)
        if (backupPath.isNullOrEmpty()) {
            return Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.FOLDER_NOT_DEFINED),
            )
        }
        if (!StorageUtil.isFolderReadable(context, backupPath)) {
            return Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_STORAGE_PERMISSION),
            )
        }
        // Notify users that work is being started
        Timber.i("Start to backup app rules")
        setForeground(updateNotification("", 0, 0))
        // Backup logic
        val shouldBackupSystemApp = inputData.getBoolean(PARAM_BACKUP_SYSTEM_APPS, false)
        return withContext(ioDispatcher) {
            var current = 1
            try {
                val list = if (shouldBackupSystemApp) {
                    ApplicationUtil.getApplicationList(context)
                } else {
                    ApplicationUtil.getThirdPartyApplicationList(context)
                }
                val total = list.count()
                list.forEach {
                    setForeground(updateNotification(it.packageName, current, total))
                    Rule.export(context, it.packageName, Uri.parse(backupPath))
                    current++
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to export blocker rules")
                return@withContext Result.failure(
                    workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_ROOT_PERMISSION),
                )
            }
            // Success, show a toast then cancel notifications
            Timber.i("Backup app rules finished.")
            return@withContext Result.success(
                workDataOf(PARAM_BACKUP_COUNT to current),
            )
        }
    }

    private fun updateNotification(name: String, current: Int, total: Int): ForegroundInfo {
        val id = NotificationUtil.PROCESSING_INDICATOR_CHANNEL_ID
        val title = context.getString(R.string.backing_up_apps_please_wait)
        val cancel = context.getString(R.string.cancel)
        // This PendingIntent can be used to cancel the worker
        val intent = WorkManager.getInstance(context)
            .createCancelPendingIntent(getId())
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtil.createProgressingNotificationChannel(context)
        }
        val notification = NotificationCompat.Builder(context, id)
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

    companion object {
        const val PARAM_BACKUP_COUNT = "param_backup_count"
        const val PARAM_WORK_RESULT = "param_work_result"
        private const val PARAM_FOLDER_PATH = "param_folder_path"
        private const val PARAM_BACKUP_SYSTEM_APPS = "param_restore_system_apps"

        fun exportWork(folderPath: String?, backupSystemApps: Boolean) =
            OneTimeWorkRequestBuilder<ExportBlockerRulesWorker>()
                .setInputData(
                    workDataOf(
                        PARAM_FOLDER_PATH to folderPath,
                        PARAM_BACKUP_SYSTEM_APPS to backupSystemApps,
                    ),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
    }
}
