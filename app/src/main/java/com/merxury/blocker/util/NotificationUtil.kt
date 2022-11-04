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
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                vibrationPattern = null
            }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }
}