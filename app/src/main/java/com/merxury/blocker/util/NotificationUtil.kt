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

package com.merxury.blocker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.merxury.blocker.R

object NotificationUtil {
    const val PROCESSING_INDICATOR_CHANNEL_ID = "processing_progress_indicator"
    const val PROCESSING_NOTIFICATION_ID = 10001
    const val UPDATE_RULE_CHANNEL_ID = "update_rule"
    const val UPDATE_RULE_NOTIFICATION_ID = 10002

    @RequiresApi(Build.VERSION_CODES.O)
    fun createProgressingNotificationChannel(context: Context) {
        val channelId = PROCESSING_INDICATOR_CHANNEL_ID
        val channelName = context.getString(R.string.processing_progress_indicator)
        val channel =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                vibrationPattern = null
            }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createUpdateRulesNotificationChannel(context: Context) {
        val channelId = UPDATE_RULE_CHANNEL_ID
        val channelName = context.getString(R.string.update_rules_notification)
        val channel =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
                .apply {
                    setSound(null, null)
                    vibrationPattern = null
                }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }
}
