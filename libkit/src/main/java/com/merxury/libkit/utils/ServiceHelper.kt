package com.merxury.libkit.utils

import com.elvishew.xlog.XLog
import com.merxury.libkit.RootCommand

class ServiceHelper(private val packageName: String) {
    private val logger = XLog.tag("ServiceHelper").build()
    private var serviceInfo: String = ""
    private val serviceList: MutableList<String> = mutableListOf()

    fun isServiceRunning(serviceName: String): Boolean {
        val shortName = if (serviceName.startsWith(packageName)) {
            serviceName.removePrefix(packageName)
        } else {
            serviceName
        }
        val fullRegex = SERVICE_REGEX.format(packageName, serviceName).toRegex()
        val shortRegex = SERVICE_REGEX.format(packageName, shortName).toRegex()
        serviceList.forEach {
            if (it.contains(fullRegex) || it.contains(shortRegex)) {
                if (it.contains("app=ProcessRecord{")) {
                    return true
                }
            }
        }
        return false
    }

    fun refresh() {
        serviceList.clear()
        serviceInfo = try {
            RootCommand.runBlockingCommand("dumpsys activity services -p $packageName")
        } catch (e: Exception) {
            logger.e("Cannot get running service list:", e)
            ""
        }
        parseServiceInfo()
    }

    private fun parseServiceInfo() {
        if (serviceInfo.contains("(nothing)")) {
            return
        }
        val list = serviceInfo.split("\n[\n]+".toRegex()).toMutableList()
        if (list.lastOrNull()?.contains("Connection bindings to services") == true) {
            list.removeAt(list.size - 1)
        }
        serviceList.addAll(list)
    }

    companion object {
        private const val SERVICE_REGEX = """ServiceRecord\{(.*?) %s\/%s\}"""
    }
}