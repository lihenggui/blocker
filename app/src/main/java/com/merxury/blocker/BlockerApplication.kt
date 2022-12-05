package com.merxury.blocker

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.flattener.ClassicFlattener
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.naming.DateFileNameGenerator
import com.google.android.material.color.DynamicColors
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import me.weishu.reflection.Reflection
import javax.inject.Inject

@HiltAndroidApp
class BlockerApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    override fun onCreate() {
        super.onCreate()
        initLogger()
        context = this
        DynamicColors.applyToActivitiesIfAvailable(this)
        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        Reflection.unseal(this) // bypass hidden api restriction, https://github.com/tiann/FreeReflection
    }

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()


    private fun initLogger() {
        val logFolder = filesDir.resolve(LOG_PATH)
        if (!logFolder.exists()) {
            logFolder.mkdirs()
        }
        val config = LogConfiguration.Builder()
            .logLevel(LogLevel.ALL)
            .tag("Blocker")
            .enableThreadInfo()
            .build()
        val filePrinter = FilePrinter.Builder(logFolder.absolutePath)
            .backupStrategy(NeverBackupStrategy())
            .fileNameGenerator(DateFileNameGenerator())
            .flattener(ClassicFlattener())
            .build()
        val androidPrinter = AndroidPrinter()
        XLog.init(config, androidPrinter, filePrinter)
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
        private const val LOG_PATH = "log"
    }
}