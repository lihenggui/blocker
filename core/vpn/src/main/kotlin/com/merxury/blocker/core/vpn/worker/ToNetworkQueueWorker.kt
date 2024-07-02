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
import com.merxury.blocker.core.vpn.deviceToNetworkTCPQueue
import com.merxury.blocker.core.vpn.deviceToNetworkUDPQueue
import com.merxury.blocker.core.vpn.protocol.Packet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject

class ToNetworkQueueWorker @Inject constructor(
    @Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
) {

    private lateinit var vpnInput: FileChannel
    var totalInputCount = 0L

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun start(vpnFileDescriptor: FileDescriptor) {
        vpnInput = FileInputStream(vpnFileDescriptor).channel
        scope.launch {
            runWorker()
        }
    }

    fun stop() {
        scope.cancel()
    }

    private suspend fun runWorker() = withContext(dispatcher) {
        val readBuffer = ByteBuffer.allocate(16384)
        while (scope.isActive) {
            var readCount: Int
            try {
                readCount = vpnInput.read(readBuffer)
            } catch (e: IOException) {
                e.printStackTrace()
                continue
            }
            if (readCount > 0) {
                readBuffer.flip()
                val byteArray = ByteArray(readCount)
                readBuffer.get(byteArray)

                val byteBuffer = ByteBuffer.wrap(byteArray)
                totalInputCount += readCount

                val packet = Packet(byteBuffer)
                if (packet.isUdp) {
                    deviceToNetworkUDPQueue.offer(packet)
                } else if (packet.isTcp) {
                    deviceToNetworkTCPQueue.offer(packet)
                } else {
                    Timber.d("Unknown packet protocol type ${packet.ip4Header?.protocolNum}")
                }
            } else if (readCount < 0) {
                break
            }
            readBuffer.clear()
        }

        Timber.i("ToNetworkQueueWorker finished running")
    }
}
