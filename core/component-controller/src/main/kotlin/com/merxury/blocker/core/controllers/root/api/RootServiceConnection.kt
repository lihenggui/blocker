/*
 * Copyright 2025 Blocker
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
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.MAIN
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.utils.RootAvailabilityChecker
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
internal class RootServiceConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootChecker: RootAvailabilityChecker,
    @Dispatcher(MAIN) private val mainDispatcher: CoroutineDispatcher,
) {
    private var connection: ServiceConnection? = null
    private var service: IRootService? = null

    val rootService: IRootService?
        get() = service

    suspend fun ensureConnected() = withContext(mainDispatcher) {
        if (service != null) return@withContext
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
        Timber.d("Binding to RootServer")
        val intent = Intent(context, RootServer::class.java)
        suspendCoroutine { cont ->
            RootService.bind(
                intent,
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        Timber.d("RootConnection: onServiceConnected")
                        connection = this
                        service = IRootService.Stub.asInterface(binder)
                        cont.resume(Unit)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Timber.d("RootConnection: onServiceDisconnected")
                        service = null
                        connection = null
                    }
                },
            )
        }
    }
}
