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

package com.merxury.blocker.core.vpn.worker

import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.vpn.UDP_SOCKET_IDLE_TIMEOUT
import com.merxury.blocker.core.vpn.udpSocketMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

private const val INTERVAL_TIME = 5L

class UdpSocketCleanWorker @Inject constructor(
    @Dispatcher(IO)private val dispatcher: CoroutineDispatcher,
) {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun start() {
        scope.launch {
            runWorker()
        }
    }

    fun stop() {
        scope.cancel()
    }

    private suspend fun runWorker() = withContext(dispatcher) {
        while (scope.isActive) {
            synchronized(udpSocketMap) {
                val iterator = udpSocketMap.iterator()
                var removeCount = 0
                while (isActive && iterator.hasNext()) {
                    val managedDatagramChannel = iterator.next()
                    if (System.currentTimeMillis() - managedDatagramChannel.value.lastTime > UDP_SOCKET_IDLE_TIMEOUT * 1000) {
                        runCatching {
                            managedDatagramChannel.value.channel.close()
                        }.exceptionOrNull()?.printStackTrace()
                        iterator.remove()
                        removeCount++
                    }
                }
                if (removeCount > 0) {
                    Timber.d("Removed $removeCount expired inactive UDP sockets, currently active ${udpSocketMap.size}")
                }
            }
            delay(INTERVAL_TIME * 1000)
        }
    }
}
