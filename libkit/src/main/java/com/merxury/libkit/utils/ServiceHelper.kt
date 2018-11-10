package com.merxury.libkit.utils

import com.merxury.libkit.RootCommand

class ServiceHelper(private val packageName: String) {
    private lateinit var serviceList: String

    fun isServiceRunning(serviceName: String): Boolean {
        val shortName = if (serviceName.startsWith(packageName)) {
            serviceName.removePrefix(packageName)
        } else {
            serviceName
        }
        val fullRegex = SERVICE_REGEX.format(packageName, serviceName).toRegex()
        val shortRegex = SERVICE_REGEX.format(packageName, shortName).toRegex()
        return serviceList.contains(fullRegex) || serviceList.contains(shortRegex)
    }

    fun refresh() {
        serviceList = RootCommand.runBlockingCommand("dumpsys activity services $packageName")
    }

    companion object {
        private const val SERVICE_REGEX = """\s+?\*\sServiceRecord\{(.*?) %s\/%s\}"""
    }
}