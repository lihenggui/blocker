package com.merxury.blocker.core.utils

import com.elvishew.xlog.XLog
import com.merxury.blocker.core.RootCommand
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ServiceHelper(private val packageName: String) {
    private val logger = XLog.tag("ServiceHelper").build()
    private var serviceInfo: String = ""
    private val serviceList: MutableList<String> = mutableListOf()

    suspend fun isServiceRunning(serviceName: String): Boolean {
        return withContext(Dispatchers.Default) {
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
                        return@withContext true
                    }
                }
            }
            return@withContext false
        }
    }

    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            serviceList.clear()
            serviceInfo = try {
                if (PermissionUtils.isRootAvailable()) {
                    RootCommand.runBlockingCommand("dumpsys activity services -p $packageName")
                } else {
                    ""
                }
            } catch (e: Exception) {
                logger.e("Cannot get running service list:", e)
                ""
            }
            parseServiceInfo()
        }
    }

    private suspend fun parseServiceInfo(dispatcher: CoroutineDispatcher = Dispatchers.Default) {
        withContext(dispatcher) {
            if (serviceInfo.contains("(nothing)")) {
                return@withContext
            }
            val list = serviceInfo.split("\n[\n]+".toRegex()).toMutableList()
            if (list.lastOrNull()?.contains("Connection bindings to services") == true) {
                list.removeAt(list.size - 1)
            }
            serviceList.addAll(list)
        }
    }

    companion object {
        private const val SERVICE_REGEX = """ServiceRecord\{(.*?) %s\/%s\}"""
    }
}
