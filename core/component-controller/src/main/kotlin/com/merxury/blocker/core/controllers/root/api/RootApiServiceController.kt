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
import android.os.IBinder
import com.merxury.blocker.core.controller.root.service.IRootService
import com.merxury.blocker.core.controllers.IServiceController
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.MAIN
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.topjohnwu.superuser.ipc.RootService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class RootApiServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(MAIN) private val mainDispatcher: CoroutineDispatcher,
) : IServiceController {
    private var rootConnection: RootConnection? = null
    private var rootServer: IRootService? = null

    inner class RootConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.d("RootConnection: onServiceConnected")
            rootConnection = this
            rootServer = IRootService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.d("RootConnection: onServiceDisconnected")
            rootServer = null
            rootConnection = null
        }
    }

    private suspend fun ensureInitialization() = withContext(mainDispatcher) {
        if (rootConnection == null) {
            val connection = RootConnection()
            val intent = Intent(context, RootServer::class.java)
            RootService.bind(intent, connection)
            this@RootApiServiceController.rootConnection = connection
        }
    }
    override suspend fun load(): Boolean {
        rootServer?.refreshRunningServiceList()
        return true
    }

    override fun isServiceRunning(packageName: String, serviceName: String): Boolean {
        return rootServer?.isServiceRunning(packageName, serviceName) ?: false
    }

    override suspend fun stopService(packageName: String, serviceName: String): Boolean {
        return rootServer?.stopService(packageName, serviceName) ?: false
    }

    override suspend fun startService(packageName: String, serviceName: String): Boolean {
        return rootServer?.startService(packageName, serviceName) ?: false
    }
}
