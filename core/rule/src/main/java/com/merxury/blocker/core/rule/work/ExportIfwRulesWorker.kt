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
import com.merxury.blocker.core.network.BlockerDispatchers.IO
import com.merxury.blocker.core.network.Dispatcher
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.util.NotificationUtil
import com.merxury.blocker.core.rule.util.StorageUtil
import com.merxury.blocker.core.utils.FileUtils
import com.merxury.ifw.util.IfwStorageUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

@HiltWorker
class ExportIfwRulesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val folderPath = inputData.getString(PARAM_FOLDER_PATH)
        if (folderPath.isNullOrEmpty()) {
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.FOLDER_NOT_DEFINED),
            )
        }
        if (!StorageUtil.isFolderReadable(context, folderPath)) {
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_STORAGE_PERMISSION),
            )
        }
        Timber.i("Start to export IFW rules.")
        var current = 0
        try {
            val ifwFolder = IfwStorageUtils.getIfwFolder()
            val files = FileUtils.listFiles(ifwFolder)
            val total = files.count()
            files.forEach {
                Timber.i("Export $it")
                val filename = it.split(File.separator).last()
                setForeground(updateNotification(filename, current, total))
                val content = FileUtils.read(ifwFolder + it)
                StorageUtil.saveIfwToStorage(context, folderPath, filename, content, ioDispatcher)
                current++
            }
        } catch (e: RuntimeException) {
            Timber.e("Failed to export IFW rules", e)
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.MISSING_ROOT_PERMISSION),
            )
        } catch (e: IOException) {
            Timber.e("Can't read IFW rules", e)
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to RuleWorkResult.UNEXPECTED_EXCEPTION),
            )
        }
        Timber.i("Export IFW rules finished, success count = $current.")
        return@withContext Result.success(
            workDataOf(PARAM_EXPORT_COUNT to current),
        )
    }

    private fun updateNotification(name: String, current: Int, total: Int): ForegroundInfo {
        val id = NotificationUtil.PROCESSING_INDICATOR_CHANNEL_ID
        val title = context.getString(R.string.backing_up_ifw_please_wait)
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
        const val PARAM_EXPORT_COUNT = "param_export_count"
        const val PARAM_WORK_RESULT = "param_work_result"
        private const val PARAM_FOLDER_PATH = "param_folder_path"
        fun exportWork(folderPath: String?) = OneTimeWorkRequestBuilder<ExportIfwRulesWorker>()
            .setInputData(
                workDataOf(
                    PARAM_FOLDER_PATH to folderPath,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
