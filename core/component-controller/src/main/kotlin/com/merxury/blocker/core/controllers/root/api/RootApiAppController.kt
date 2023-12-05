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
import kotlin.coroutines.suspendCoroutine

@Singleton
class RootApiAppController @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(MAIN) private val mainDispatcher: CoroutineDispatcher,
) : IAppController {
    private var rootConnection: ServiceConnection? = null
    private var rootService: IRootService? = null

    override suspend fun init() = withContext(mainDispatcher) {
        Timber.d("Initialize RootApiAppController")
        val intent = Intent(context, RootServer::class.java)

        suspendCoroutine { cont ->
            RootService.bind(
                intent,
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        Timber.d("RootConnection: onServiceConnected")
                        rootConnection = this
                        rootService = IRootService.Stub.asInterface(service)
                        cont.resumeWith(Result.success(Unit))
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Timber.d("RootConnection: onServiceDisconnected")
                        rootService = null
                        rootConnection = null
                    }
                },
            )
        }
    }

    override suspend fun disable(packageName: String): Boolean {
        val rootService = rootService
        if (rootService == null) {
            Timber.w("Cannot disable app: root server is not initialized")
            return false
        }
        rootService.setApplicationEnabledSetting(
            packageName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        )
        return true
    }

    override suspend fun enable(packageName: String): Boolean {
        val rootService = rootService
        if (rootService == null) {
            Timber.w("Cannot enable app: root server is not initialized")
            return false
        }
        rootService.setApplicationEnabledSetting(
            packageName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )
        return true
    }

    override suspend fun forceStop(packageName: String): Boolean {
        val rootService = rootService
        if (rootService == null) {
            Timber.w("Cannot force stop app: root server is not initialized")
            return false
        }
        rootService.forceStop(packageName)
        return true
    }

    override suspend fun clearCache(packageName: String): Boolean {
        val rootService = rootService
        if (rootService == null) {
            Timber.w("Cannot clear cache: root server is not initialized")
            return false
        }
        rootService.clearCache(packageName)
        return true
    }

    override suspend fun clearData(packageName: String): Boolean {
        val rootService = rootService
        if (rootService == null) {
            Timber.w("Cannot clear data: root server is not initialized")
            return false
        }
        rootService.clearData(packageName)
        return true
    }

    override suspend fun uninstallApp(packageName: String, versionCode: Long): Boolean {
        val rootService = rootService
        if (rootService == null) {
            Timber.w("Cannot uninstall app: root server is not initialized")
            return false
        }
        rootService.uninstallApp(packageName, versionCode)
        return true
    }

    override fun isAppRunning(packageName: String): Boolean {
        val rootService = rootService ?: return false
        return rootService.isAppRunning(packageName)
    }

    override suspend fun refreshRunningAppList() {
        if (rootService == null) {
            init()
        }
        Timber.d("Refresh running app list")
        rootService?.refreshRunningAppList()
    }
}
