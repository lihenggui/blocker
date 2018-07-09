package com.merxury.blocker.utils

import com.merxury.libkit.RootCommand

object ServiceUtil {
    fun isServiceRunning(packageName: String, serviceName: String): Boolean {
        val output = RootCommand.runBlockingCommand("dumpsys activity services $packageName/$serviceName")
        return output.contains("pid=")
    }
}