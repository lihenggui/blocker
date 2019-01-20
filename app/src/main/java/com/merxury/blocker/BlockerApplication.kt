package com.merxury.blocker

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.naming.ChangelessFileNameGenerator
import com.merxury.blocker.util.NotificationUtil
import moe.shizuku.api.ShizukuClient

class BlockerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initLogger()
        context = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = NotificationUtil.PROCESSING_INDICATOR_CHANNEL_ID
            val channelName = context.getString(R.string.processing_progress_indicator)
            createNotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        }
        ShizukuClient.initialize(this)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String, importance: Int) {
        val channel = NotificationChannel(channelId, channelName, importance)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun initLogger() {
        val config = LogConfiguration.Builder()
            .logLevel(LogLevel.ALL)
            .t()
            .build()
        val androidPrinter = AndroidPrinter()
        val filePrinter = FilePrinter.Builder(filesDir.absolutePath)
            .backupStrategy(NeverBackupStrategy())
            .fileNameGenerator(ChangelessFileNameGenerator(LOG_FILENAME))
            .build()
        XLog.init(config, androidPrinter, filePrinter)
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
        const val LOG_FILENAME = "blocker_log.log"
    }
}