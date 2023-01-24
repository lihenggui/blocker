/*
 * Copyright 2023 Blocker
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
import timber.log.Timber
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
                .setTimeout(10),
        )
        Timber.plant(Timber.DebugTree())
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // Bypass hidden api restriction, https://github.com/tiann/FreeReflection
        Reflection.unseal(this)
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
