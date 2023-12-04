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
import android.content.pm.PackageManager
import android.os.IBinder
import com.merxury.blocker.core.controller.root.service.IRootService
import com.merxury.blocker.core.controllers.IAppController
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.MAIN
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.topjohnwu.superuser.ipc.RootService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootApiAppController @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(MAIN) private val mainDispatcher: CoroutineDispatcher,
) : IAppController {
    private var rootConnection: RootConnection? = null
    private var rootServer: IRootService? = null

    inner class RootConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.d("RootApiAppController: onServiceConnected")
            rootConnection = this
            rootServer = IRootService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.d("RootApiAppController: onServiceDisconnected")
            rootServer = null
            rootConnection = null
        }
    }

    private suspend fun ensureInitialization() = withContext(mainDispatcher) {
        if (rootConnection == null) {
            val connection = RootConnection()
            val intent = Intent(context, RootServer::class.java)
            RootService.bind(intent, connection)
            this@RootApiAppController.rootConnection = connection
        }
    }

    override suspend fun disable(packageName: String): Boolean {
        ensureInitialization()
        rootServer?.setApplicationEnabledSetting(
            packageName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        )
        return true
    }

    override suspend fun enable(packageName: String): Boolean {
        ensureInitialization()
        rootServer?.setApplicationEnabledSetting(
            packageName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )
        return true
    }

    override suspend fun forceStop(packageName: String): Boolean {
        ensureInitialization()
        rootServer?.forceStop(packageName)
        return true
    }

    override suspend fun clearCache(packageName: String): Boolean {
        ensureInitialization()
        rootServer?.clearCache(packageName)
        return true
    }

    override suspend fun clearData(packageName: String): Boolean {
        ensureInitialization()
        rootServer?.clearData(packageName)
        return true
    }

    override suspend fun uninstallApp(packageName: String, versionCode: Long): Boolean {
        ensureInitialization()
        rootServer?.uninstallApp(packageName, versionCode)
        return true
    }

    override fun isAppRunning(packageName: String): Boolean {
        if (rootServer == null) {
            Timber.w("Root server is not initialized")
            return false
        }
        return rootServer?.isAppRunning(packageName) ?: false
    }

    override suspend fun refreshRunningAppList() {
        ensureInitialization()
        Timber.d("Refresh running app list")
        rootServer?.refreshRunningAppList()
    }
}
