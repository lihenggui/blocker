package com.merxury.blocker

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.merxury.blocker.core.shizuku.ShizukuClientWrapper

class BlockerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        context = this
        ShizukuClientWrapper.initialize(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "processing_progress_indicator"
            val channelName = context.getString(R.string.processing_progress_indicator)
            createNotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String, importance: Int) {
        val channel = NotificationChannel(channelId, channelName, importance)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }
}