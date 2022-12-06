package com.merxury.blocker.util

import android.annotation.TargetApi
import android.os.Build
import android.util.Log
import com.merxury.blocker.BlockerApplication
import com.merxury.blocker.core.RootCommand
import com.merxury.blocker.core.entity.ETrimMemoryLevel
import com.topjohnwu.superuser.io.SuFile

object ManagerUtils {
    @Throws(RuntimeException::class)
    fun launchApplication(packageName: String) {
        RootCommand.runBlockingCommand(
            "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
        )
    }

    @Throws(RuntimeException::class)
    fun launchActivity(packageName: String, activityName: String) {
        RootCommand.runBlockingCommand("am start -n $packageName/$activityName")
    }

    @Throws(RuntimeException::class)
    fun forceStop(packageName: String) {
        RootCommand.runBlockingCommand("am force-stop $packageName")
    }

    @Throws(RuntimeException::class)
    fun startService(packageName: String, serviceName: String) {
        RootCommand.runBlockingCommand("am startservice $packageName/$serviceName")
    }

    @Throws(RuntimeException::class)
    fun stopService(packageName: String, serviceName: String) {
        RootCommand.runBlockingCommand("am stopservice $packageName/$serviceName")
    }

    @Throws(RuntimeException::class)
    fun disableApplication(packageName: String) {
        RootCommand.runBlockingCommand("pm disable $packageName")
    }

    @Throws(RuntimeException::class)
    fun enableApplication(packageName: String) {
        RootCommand.runBlockingCommand("pm enable $packageName")
    }

    @Throws(RuntimeException::class)
    fun clearData(packageName: String) {
        RootCommand.runBlockingCommand("pm clear $packageName")
    }

    @Throws(RuntimeException::class)
    fun clearCache(packageName: String) {
        val context = BlockerApplication.context
        // TODO API adaption
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val dataFolder = context.dataDir
                .parentFile
                ?.resolve(packageName)
                ?.resolve("cache")
            if (dataFolder == null) {
                Log.e("ManagerUtils", "Can't find cache folder for $packageName")
                return
            }
            val cacheFolder = SuFile(dataFolder.absolutePath)
            if (cacheFolder.exists()) {
                cacheFolder.deleteRecursive()
            }
        }
    }

    @Throws(RuntimeException::class)
    fun uninstallApplication(packageName: String) {
        RootCommand.runBlockingCommand("pm uninstall $packageName")
    }

    @Throws(RuntimeException::class)
    @TargetApi(Build.VERSION_CODES.M)
    fun trimMemory(packageName: String, level: ETrimMemoryLevel) {
        RootCommand.runBlockingCommand("am send-trim-memory $packageName ${level.name}")
    }
}
