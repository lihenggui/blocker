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

package com.merxury.blocker.core.controllers.root

import com.merxury.blocker.core.controllers.IServiceController
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.utils.PermissionUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val SERVICE_REGEX = """ServiceRecord\{(.*?) %s\/%s\}"""

class RootServiceController(
    @Dispatcher(DEFAULT) private val defaultDispatcher: CoroutineDispatcher,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : IServiceController {

    private val runningServices = mutableListOf<String>()
    override suspend fun load(): Boolean = withContext(defaultDispatcher) {
        if (!PermissionUtils.isRootAvailable()) {
            return@withContext false
        }
        runningServices.clear()
        try {
            val serviceInfo = "dumpsys activity services"
                .exec(ioDispatcher)
                .out
                .joinToString("\n")
            if (serviceInfo.contains("(nothing)")) {
                Timber.d("No running services")
                return@withContext true
            }
            val list = serviceInfo.split("\n[\n]+".toRegex()).toMutableList()
            if (list.lastOrNull()?.contains("Connection bindings to services") == true) {
                list.removeAt(list.size - 1)
            }
            Timber.d("Found ${list.size} running services")
            runningServices.addAll(list)
            return@withContext true
        } catch (e: Exception) {
            Timber.e(e, "Cannot get running service list:")
            return@withContext false
        }
    }

    override fun isServiceRunning(packageName: String, serviceName: String): Boolean {
        val shortName = if (serviceName.startsWith(packageName)) {
            serviceName.removePrefix(packageName)
        } else {
            serviceName
        }
        val fullRegex = SERVICE_REGEX.format(packageName, serviceName).toRegex()
        val shortRegex = SERVICE_REGEX.format(packageName, shortName).toRegex()
        runningServices.forEach {
            if (it.contains(fullRegex) || it.contains(shortRegex)) {
                if (it.contains("app=ProcessRecord{")) {
                    return true
                }
            }
        }
        return false
    }

    override suspend fun stopService(packageName: String, serviceName: String): Boolean {
        Timber.d("Stopping service $packageName/$serviceName")
        val result = "am stopservice $packageName/$serviceName"
            .exec(ioDispatcher)
        val output = result.out.joinToString("\n")
        return if (output.contains("Service stopped")) {
            Timber.d("Service $packageName/$serviceName stopped")
            true
        } else {
            Timber.e("Cannot stop service $packageName/$serviceName, output: $output")
            false
        }
    }

    override suspend fun startService(packageName: String, serviceName: String): Boolean {
        Timber.d("Starting service $packageName/$serviceName")
        val result = "am startservice $packageName/$serviceName"
            .exec(ioDispatcher)
        return if (result.isSuccess) {
            Timber.d("Service $packageName/$serviceName started")
            true
        } else {
            Timber.e("Cannot start service $packageName/$serviceName")
            false
        }
    }
}
