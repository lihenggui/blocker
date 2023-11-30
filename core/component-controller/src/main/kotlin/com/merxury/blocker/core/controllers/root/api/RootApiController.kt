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

package com.merxury.blocker.core.controllers.root.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.os.IBinder
import com.merxury.blocker.core.controller.root.service.IRootService
import com.merxury.blocker.core.controllers.IController
import com.topjohnwu.superuser.ipc.RootService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class RootApiController @Inject constructor(
    @ApplicationContext private val context: Context,
): IController {
    private var rootConnection: RootConnection? = null
    private var rootServer: IRootService? = null
    inner class RootConnection : ServiceConnection {
        private var isDaemon = false
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.d("onServiceConnected")
            rootConnection = this
            rootServer = IRootService.Stub.asInterface(service)
            Timber.d("uid: ${rootServer?.uid}")
            Timber.d("pid: ${rootServer?.pid}")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.d("onServiceDisconnected")
            rootServer = null
        }
    }

    private suspend fun ensureInitialization() = withContext(Dispatchers.Main) {
        if (rootConnection == null) {
            val connection = RootConnection()
            val intent = Intent(context, RootServer::class.java)
            RootService.bind(intent, connection)
            this@RootApiController.rootConnection = connection
        }
    }

    override suspend fun switchComponent(
        packageName: String,
        componentName: String,
        state: Int,
    ): Boolean {
        Timber.d("Switch component: $packageName/$componentName, state: $state")
        ensureInitialization()
        return false
    }

    override suspend fun enable(packageName: String, componentName: String): Boolean {
        return switchComponent(packageName, componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    override suspend fun disable(packageName: String, componentName: String): Boolean {
        return switchComponent(packageName, componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    override suspend fun batchEnable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit,
    ): Int {
        TODO("Not yet implemented")
    }

    override suspend fun batchDisable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit,
    ): Int {
        TODO("Not yet implemented")
    }

    override suspend fun checkComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean {
        TODO("Not yet implemented")
    }
}
