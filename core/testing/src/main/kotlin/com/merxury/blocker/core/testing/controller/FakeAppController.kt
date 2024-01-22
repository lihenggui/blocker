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

import com.merxury.blocker.core.controllers.IAppController

class FakeAppController(private val rootGranted: Boolean = false) : IAppController {

    private val runningApps = mutableSetOf<String>()
    override suspend fun disable(packageName: String): Boolean {
        return rootGranted
    }

    override suspend fun enable(packageName: String): Boolean {
        return rootGranted
    }

    override suspend fun forceStop(packageName: String): Boolean {
        return rootGranted
    }

    override suspend fun clearCache(packageName: String): Boolean {
        return rootGranted
    }

    override suspend fun clearData(packageName: String): Boolean {
        return rootGranted
    }

    override suspend fun uninstallApp(packageName: String, versionCode: Long): Boolean {
        return rootGranted
    }

    override fun isAppRunning(packageName: String): Boolean {
        return runningApps.contains(packageName)
    }

    fun setRunningApps(vararg packageName: String) {
        runningApps.addAll(packageName)
    }

    override suspend fun refreshRunningAppList() = Unit
}
