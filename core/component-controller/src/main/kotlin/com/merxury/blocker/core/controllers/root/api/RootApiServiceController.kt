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
import javax.inject.Singleton
import kotlin.coroutines.suspendCoroutine

@Singleton
class RootApiServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(MAIN) private val mainDispatcher: CoroutineDispatcher,
) : IServiceController {
    private var rootConnection: ServiceConnection? = null
    private var rootService: IRootService? = null

    override suspend fun init() = withContext(mainDispatcher) {
        Timber.d("Start initialize RootApiServiceController")
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

    override suspend fun load(): Boolean {
        rootService?.refreshRunningServiceList()
        return true
    }

    override fun isServiceRunning(packageName: String, serviceName: String): Boolean {
        val rootService = rootService
        if (rootService == null) {
            Timber.w("Cannot get running service, rootService is null")
            return false
        }
        return rootService.isServiceRunning(packageName, serviceName)
    }

    override suspend fun stopService(packageName: String, serviceName: String): Boolean {
        val rootService = rootService
        if (rootService == null) {
            Timber.w("Cannot stop service, rootService is null")
            return false
        }
        return rootService.stopService(packageName, serviceName)
    }

    override suspend fun startService(packageName: String, serviceName: String): Boolean {
        val rootService = rootService
        if (rootService == null) {
            Timber.w("Cannot start service, rootService is null")
            return false
        }
        return rootService.startService(packageName, serviceName)
    }
}
