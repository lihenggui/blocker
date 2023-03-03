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
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.merxury.blocker.core.rule.R
import com.merxury.blocker.core.rule.util.NotificationUtil

abstract class RuleNotificationWorker(private val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    abstract fun getNotificationTitle(): Int

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return updateNotification("", 0, 0)
    }

    fun updateNotification(summary: String, current: Int, total: Int): ForegroundInfo {
        val id = NotificationUtil.PROCESSING_INDICATOR_CHANNEL_ID
        val title = context.getString(getNotificationTitle())
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
            .setSubText(summary)
            .setSmallIcon(com.merxury.blocker.core.common.R.drawable.ic_blocker_notification)
            .setProgress(total, current, false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()
        return ForegroundInfo(NotificationUtil.PROCESSING_NOTIFICATION_ID, notification)
    }
}
