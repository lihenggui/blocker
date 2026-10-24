/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.core.controllers.ifw.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.core.ifw.IfwFileSystem
import com.merxury.core.ifw.IfwStorageUtils
import com.merxury.core.ifw.IfwUnavailableException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume

private const val EXTENSION = ".xml"
private const val SERVICE_VERSION = 1
private const val BIND_TIMEOUT_MS = 10_000L

internal class ShizukuIfwFileSystem @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
) : IfwFileSystem {

    private val bindMutex = Mutex()

    @Volatile
    private var service: IIfwFileService? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, IfwFileService::class.java.name),
    ).daemon(false).processNameSuffix("ifw").version(SERVICE_VERSION)

    private suspend fun service(): IIfwFileService {
        service?.let { return it }
        return bindMutex.withLock {
            service?.let { return it }
            var pendingConnection: ServiceConnection? = null
            val bound = withTimeoutOrNull(BIND_TIMEOUT_MS) {
                suspendCancellableCoroutine<IIfwFileService?> { cont ->
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            if (binder != null && binder.pingBinder()) {
                                cont.resume(IIfwFileService.Stub.asInterface(binder))
                            } else {
                                cont.resume(null)
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            service = null
                            runCatching { Shizuku.unbindUserService(userServiceArgs, this, true) }
                        }
                    }
                    pendingConnection = connection
                    cont.invokeOnCancellation {
                        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
                    }
                    Shizuku.bindUserService(userServiceArgs, connection)
                }
            }
            if (bound == null) {
                pendingConnection?.let { conn ->
                    runCatching { Shizuku.unbindUserService(userServiceArgs, conn, true) }
                }
                throw IfwUnavailableException("Failed to bind Shizuku IFW user service")
            }
            service = bound
            bound
        }
    }

    private fun pathOf(packageName: String) = IfwStorageUtils.ifwFolder + packageName + EXTENSION

    override suspend fun readRules(packageName: String): String? = withContext(dispatcher) {
        runCatching { service().readFile(pathOf(packageName)) }
            .onFailure { Timber.e(it, "readRules via Shizuku failed") }
            .getOrNull()
    }

    override suspend fun writeRules(packageName: String, content: String) = withContext(dispatcher) {
        val ok = service().writeFile(pathOf(packageName), content)
        if (!ok) throw IfwUnavailableException("Shizuku write/verify failed for $packageName")
        Timber.i("Saved IFW rules via Shizuku for $packageName")
    }

    override suspend fun deleteRules(packageName: String): Boolean = withContext(dispatcher) {
        runCatching { service().deleteFile(pathOf(packageName)) }.getOrDefault(false)
    }

    override suspend fun fileExists(packageName: String): Boolean = withContext(dispatcher) {
        runCatching { service().fileExists(pathOf(packageName)) }.getOrDefault(false)
    }

    override suspend fun listRuleFiles(): List<String> = withContext(dispatcher) {
        runCatching { service().listFiles(IfwStorageUtils.ifwFolder) }
            .getOrDefault(emptyList())
            .filter { it.endsWith(EXTENSION) }
            .map { it.removeSuffix(EXTENSION) }
    }
}
