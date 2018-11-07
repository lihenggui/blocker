package com.merxury.libkit.utils

import com.merxury.libkit.RootCommand

object ServiceUtil {
    private var packageName = ""
    private var serviceList = ""
    fun isServiceRunning(packageName: String, serviceName: String): Boolean {
        if (this.packageName != packageName) {
            getRunningServiceList(packageName)
        }
        val regex = SERVICE_REGEX.format(packageName, serviceName).toRegex()
        return serviceList.contains(regex)
    }

    private fun getRunningServiceList(packageName: String) {
        this.packageName = packageName
        serviceList = RootCommand.runBlockingCommand("dumpsys activity services $packageName")
    }

    private const val SERVICE_REGEX = """ServiceRecord\{(.*?) %s\/%s\}"""

}