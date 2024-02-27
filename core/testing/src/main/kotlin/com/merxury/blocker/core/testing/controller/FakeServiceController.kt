/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.testing.controller

import com.merxury.blocker.core.controllers.IServiceController

class FakeServiceController : IServiceController {
    private val runningServices = mutableListOf<String>()
    override suspend fun load(): Boolean {
        return true
    }

    override fun isServiceRunning(packageName: String, serviceName: String): Boolean {
        return runningServices.contains(serviceName)
    }

    override suspend fun stopService(packageName: String, serviceName: String): Boolean {
        return true
    }

    override suspend fun startService(packageName: String, serviceName: String): Boolean {
        return true
    }

    fun sendRunningServices(vararg name: String) {
        runningServices.addAll(name)
    }
}
