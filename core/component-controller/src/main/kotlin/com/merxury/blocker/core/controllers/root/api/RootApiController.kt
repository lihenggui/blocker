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

package com.merxury.blocker.core.controllers.root.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.merxury.blocker.core.controller.root.service.IRootService
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.MAIN
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.PermissionUtils
import com.topjohnwu.superuser.ipc.RootService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
internal class RootApiController @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(MAIN) private val mainDispatcher: CoroutineDispatcher,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : IController {
    private var rootConnection: ServiceConnection? = null
    private var rootService: IRootService? = null

    override suspend fun init() = withContext(mainDispatcher) {
        if (!PermissionUtils.isRootAvailable(ioDispatcher)) {
            throw RootUnavailableException()
        }
        Timber.d("Initialize RootApiController")
        val intent = Intent(context, RootServer::class.java)
        suspendCoroutine { cont ->
            RootService.bind(
                intent,
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        Timber.d("RootConnection: onServiceConnected")
                        rootConnection = this
                        rootService = IRootService.Stub.asInterface(service)
                        cont.resume(Unit)
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

    override suspend fun switchComponent(
        component: ComponentInfo,
        state: Int,
    ): Boolean {
        val rootService = rootService
        val packageName = component.packageName
        val componentName = component.name
        if (rootService == null) {
            Timber.e("Cannot switch component: root server is not initialized")
            throw RootUnavailableException()
        }
        Timber.d("Switch component: $packageName/$componentName, state: $state")
        rootService.setComponentEnabledSetting(
            packageName,
            componentName,
            state,
        )
        return true
    }

    override suspend fun enable(component: ComponentInfo): Boolean = switchComponent(
        component,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
    )

    override suspend fun disable(component: ComponentInfo): Boolean = switchComponent(
        component,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
    )

    override suspend fun batchEnable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit,
    ): Int {
        var successCount = 0
        componentList.forEach {
            if (enable(it)) {
                successCount++
            }
            action(it)
        }
        return successCount
    }

    override suspend fun batchDisable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit,
    ): Int {
        var successCount = 0
        componentList.forEach {
            if (disable(it)) {
                successCount++
            }
            action(it)
        }
        return successCount
    }

    override suspend fun checkComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean = ApplicationUtil.checkComponentIsEnabled(
        context.packageManager,
        ComponentName(packageName, componentName),
    )
}
