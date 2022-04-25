package com.merxury.libkit.utils

import android.annotation.TargetApi
import android.os.Build
import com.merxury.libkit.RootCommand
import com.merxury.libkit.entity.ETrimMemoryLevel

object ManagerUtils {
    @Throws(RuntimeException::class)
    fun launchApplication(packageName: String) {
        RootCommand.runBlockingCommand("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
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
    @TargetApi(Build.VERSION_CODES.M)
    fun trimMemory(packageName: String, level: ETrimMemoryLevel) {
        RootCommand.runBlockingCommand("am send-trim-memory $packageName ${level.name}")
    }
}