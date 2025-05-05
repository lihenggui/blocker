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

import android.net.VpnService
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.vpn.deviceToNetworkUdpQueue
import com.merxury.blocker.core.vpn.model.ManagedDatagramChannel
import com.merxury.blocker.core.vpn.model.UdpTunnel
import com.merxury.blocker.core.vpn.udpNioSelector
import com.merxury.blocker.core.vpn.udpSocketMap
import com.merxury.blocker.core.vpn.udpTunnelQueue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.ConnectException
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import javax.inject.Inject

class UdpSendWorker @Inject constructor(
    @Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
) {
    private var vpnService: VpnService? = null

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun start(vpnService: VpnService) {
        this.vpnService = vpnService
        udpTunnelQueue.clear()
        scope.launch {
            runWorker()
        }
    }

    fun stop() {
        scope.cancel()
        vpnService = null
    }

    private suspend fun runWorker() = withContext(dispatcher) {
        while (scope.isActive) {
            val packet = deviceToNetworkUdpQueue.take()

            val destinationAddress = packet.ip4Header?.destinationAddress
            val udpHeader = packet.udpHeader

            val destinationPort = udpHeader?.destinationPort ?: 0
            val sourcePort = udpHeader?.sourcePort
            val ipAndPort = (
                destinationAddress?.hostAddress?.plus(":")
                    ?: "unknownHostAddress"
                ) + destinationPort + ":" + sourcePort

            val managedChannel = if (!udpSocketMap.containsKey(ipAndPort)) {
                val channel = DatagramChannel.open()
                var channelConnectSuccess = false
                channel.apply {
                    val socket = socket()
                    vpnService?.protect(socket)
                    try {
                        connect(InetSocketAddress(destinationAddress, destinationPort))
                        channelConnectSuccess = true
                    } catch (_: ConnectException) {
                    }
                    configureBlocking(false)
                }
                if (!channelConnectSuccess) {
                    continue
                }

                val tunnel = UdpTunnel(
                    ipAndPort,
                    InetSocketAddress(packet.ip4Header?.sourceAddress, udpHeader?.sourcePort ?: 0),
                    InetSocketAddress(
                        packet.ip4Header?.destinationAddress,
                        udpHeader?.destinationPort ?: 0,
                    ),
                    channel,
                )
                udpTunnelQueue.offer(tunnel)
                udpNioSelector.wakeup()

                val managedDatagramChannel = ManagedDatagramChannel(ipAndPort, channel)
                synchronized(udpSocketMap) {
                    udpSocketMap[ipAndPort] = managedDatagramChannel
                }
                managedDatagramChannel
            } else {
                synchronized(udpSocketMap) {
                    udpSocketMap[ipAndPort]
                        ?: throw IllegalStateException("udp:udpSocketMap[$ipAndPort] should not be null")
                }
            }
            managedChannel.lastTime = System.currentTimeMillis()
            val buffer = packet.backingBuffer
            runCatching {
                while (isActive && buffer?.hasRemaining() == true) {
                    managedChannel.channel.write(buffer)
                }
            }.exceptionOrNull()?.let {
                Timber.e("Error sending UDP packet", it)
                managedChannel.channel.close()
                synchronized(udpSocketMap) {
                    udpSocketMap.remove(ipAndPort)
                }
            }
        }
    }
}
