package com.merxury.libkit.utils

import com.merxury.libkit.RootCommand
import com.merxury.libkit.entity.ETrimMemoryLevel

object AmPmUtils {
    fun launchApplication(packageName: String) {
        RootCommand.runBlockingCommand("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }

    fun launchActivity(packageName: String, activityName: String) {
        RootCommand.runBlockingCommand("am start -n $packageName/$activityName")
    }

    fun forceStop(packageName: String) {
        RootCommand.runBlockingCommand("am force-stop $packageName")
    }

    fun startService(packageName: String, serviceName: String) {
        RootCommand.runBlockingCommand("am startservice $packageName/$serviceName")
    }

    fun stopService(packageName: String, serviceName: String) {
        RootCommand.runBlockingCommand("am stopservice $packageName/$serviceName")
    }

    fun disableApplication(packageName: String) {
        RootCommand.runBlockingCommand("pm disable $packageName")
    }

    fun enableApplication(packageName: String) {
        RootCommand.runBlockingCommand("pm enable $packageName")
    }

    fun clearData(packageName: String) {
        RootCommand.runBlockingCommand("pm clear $packageName")
    }

    fun trimMemory(packageName: String, level: ETrimMemoryLevel) {
        RootCommand.runBlockingCommand("am send-trim-memory $packageName ${level.name}")
    }
}