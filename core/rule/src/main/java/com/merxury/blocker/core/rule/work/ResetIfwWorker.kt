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
import com.merxury.blocker.core.rule.entity.RuleWorkResult.MISSING_ROOT_PERMISSION
import com.merxury.blocker.core.rule.entity.RuleWorkResult.UNEXPECTED_EXCEPTION
import com.merxury.blocker.core.rule.util.NotificationUtil
import com.merxury.blocker.core.utils.FileUtils
import com.merxury.ifw.util.IfwStorageUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

@HiltWorker
class ResetIfwWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        Timber.i("Clear IFW rules")
        var count = 0
        val total: Int
        try {
            val ifwFolder = IfwStorageUtils.getIfwFolder()
            val files = FileUtils.listFiles(ifwFolder)
            total = files.count()
            files.forEach {
                updateNotification(it, count, total)
                Timber.i("Delete $it")
                FileUtils.delete(
                    path = ifwFolder + it,
                    recursively = false,
                    dispatcher = ioDispatcher,
                )
                count++
            }
        } catch (e: RuntimeException) {
            Timber.e("Failed to clear IFW rules", e)
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to MISSING_ROOT_PERMISSION),
            )
        } catch (e: IOException) {
            Timber.e("Failed to clear IFW rules, IO exception occured", e)
            return@withContext Result.failure(
                workDataOf(PARAM_WORK_RESULT to UNEXPECTED_EXCEPTION),
            )
        }
        Timber.i("Cleared $count IFW rules.")
        return@withContext Result.success(
            workDataOf(PARAM_CLEAR_COUNT to count),
        )
    }

    private fun updateNotification(name: String, current: Int, total: Int): ForegroundInfo {
        val id = NotificationUtil.PROCESSING_INDICATOR_CHANNEL_ID
        val title = applicationContext.getString(R.string.import_ifw_please_wait)
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

    companion object {
        const val PARAM_CLEAR_COUNT = "param_clear_count"
        const val PARAM_WORK_RESULT = "param_work_result"

        fun clearIfwWork() = OneTimeWorkRequestBuilder<ResetIfwWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
