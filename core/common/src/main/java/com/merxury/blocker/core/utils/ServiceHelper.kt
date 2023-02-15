/*
 * Copyright 2023 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.core.utils

import com.merxury.blocker.core.extension.exec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class ServiceHelper(private val packageName: String) {
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
                    "dumpsys activity services -p $packageName".exec().out
                        .joinToString("\n")
                } else {
                    ""
                }
            } catch (e: Exception) {
                Timber.e(e, "Cannot get running service list:")
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
