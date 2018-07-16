package com.merxury.blocker.util

import android.content.Context
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import com.merxury.blocker.R


object NotificationUtil {
    const val PROCESSING_INDICATOR_CHANNEL_ID = "processing_progress_indicator"
    const val PROCESSING_NOTIFICATION_ID = 1
    private lateinit var builder: NotificationCompat.Builder
    fun createProcessingNotification(context: Context, total: Int) {
        builder = NotificationCompat.Builder(context, PROCESSING_INDICATOR_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.processing))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setProgress(total, 0, false)
                .setAutoCancel(true)
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(PROCESSING_NOTIFICATION_ID, builder.build())
    }

    fun finishProcessingNotification(context: Context, applicationLabel: String) {
        builder.setContentText(context.getString(R.string.import_finished, applicationLabel))
                .setProgress(0, 0, false)
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(PROCESSING_NOTIFICATION_ID, builder.build())
    }

    fun updateProcessingNotification(context: Context, current: Int, total: Int) {
        builder.setProgress(total, current, false)
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(PROCESSING_NOTIFICATION_ID, builder.build())
    }
}