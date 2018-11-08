package com.merxury.libkit.utils

import com.merxury.libkit.RootCommand

class ServiceHelper(private val packageName: String) {
    private lateinit var serviceList: String

    init {
        refresh()
    }

    fun isServiceRunning(serviceName: String): Boolean {
        val regex = SERVICE_REGEX.format(packageName, serviceName).toRegex()
        return serviceList.contains(regex)
    }

    fun refresh() {
        serviceList = RootCommand.runBlockingCommand("dumpsys activity services $packageName")
    }

    companion object {
        private const val SERVICE_REGEX = """ServiceRecord\{(.*?) %s\/%s\}"""
    }
}