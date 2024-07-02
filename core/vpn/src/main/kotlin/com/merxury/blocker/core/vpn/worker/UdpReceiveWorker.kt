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
import com.merxury.blocker.core.vpn.model.Ip4Header
import com.merxury.blocker.core.vpn.model.TransportProtocol
import com.merxury.blocker.core.vpn.model.UdpHeader
import com.merxury.blocker.core.vpn.model.UdpTunnel
import com.merxury.blocker.core.vpn.networkToDeviceQueue
import com.merxury.blocker.core.vpn.protocol.Packet
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
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

private const val UDP_HEADER_FULL_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE

class UdpReceiveWorker @Inject constructor(
    @Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
) {

    private var ipId = AtomicInteger()

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun start() {
        scope.launch {
            runWorker()
        }
    }

    fun stop() {
        scope.cancel()
    }

    private fun sendUdpPacket(tunnel: UdpTunnel, source: InetSocketAddress, data: ByteArray) {
        val packet = buildUdpPacket(tunnel.remote, tunnel.local, ipId.addAndGet(1))

        val byteBuffer = ByteBuffer.allocate(UDP_HEADER_FULL_SIZE + data.size)
        byteBuffer.apply {
            position(UDP_HEADER_FULL_SIZE)
            put(data)
        }
        packet.updateUdpBuffer(byteBuffer, data.size)
        byteBuffer.position(UDP_HEADER_FULL_SIZE + data.size)
        networkToDeviceQueue.offer(byteBuffer)
    }

    private fun buildUdpPacket(source: InetSocketAddress, dest: InetSocketAddress, ipId: Int): Packet {
        val ip4Header = Ip4Header(
            version = 4,
            ihl = 5,
            destinationAddress = dest.address,
            headerChecksum = 0,
            headerLength = 20,
            identificationAndFlagsAndFragmentOffset = ipId shl 16 or (0x40 shl 8) or 0,
            optionsAndPadding = 0,
            protocol = TransportProtocol.UDP,
            protocolNum = 17,
            sourceAddress = source.address,
            totalLength = 60,
            typeOfService = 0,
            ttl = 64,
        )

        val udpHeader = UdpHeader(
            sourcePort = source.port,
            destinationPort = dest.port,
            length = 0,
        )
        return Packet(
            isTcp = false,
            isUdp = true,
            ip4Header = ip4Header,
            udpHeader = udpHeader,
        )
    }

    private suspend fun runWorker() = withContext(dispatcher) {
        val receiveBuffer = ByteBuffer.allocate(16384)
        while (scope.isActive) {
            val readyChannels = udpNioSelector.select()
            while (scope.isActive) {
                val tunnel = udpTunnelQueue.poll() ?: break
                runCatching {
                    val key = tunnel.channel.register(udpNioSelector, SelectionKey.OP_READ, tunnel)
                    key.interestOps(SelectionKey.OP_READ)
                }.exceptionOrNull()?.printStackTrace()
            }
            if (readyChannels == 0) {
                udpNioSelector.selectedKeys().clear()
                continue
            }
            val keys = udpNioSelector.selectedKeys()
            val iterator = keys.iterator()
            while (isActive && iterator.hasNext()) {
                val key = iterator.next()
                iterator.remove()
                if (key.isValid && key.isReadable) {
                    val tunnel = key.attachment() as UdpTunnel
                    runCatching {
                        val inputChannel = key.channel() as DatagramChannel
                        receiveBuffer.clear()
                        inputChannel.read(receiveBuffer)
                        receiveBuffer.flip()
                        val data = ByteArray(receiveBuffer.remaining())
                        receiveBuffer.get(data)
                        sendUdpPacket(
                            tunnel,
                            inputChannel.socket().localSocketAddress as InetSocketAddress,
                            data,
                        )
                    }.exceptionOrNull()?.let {
                        Timber.e(it)
                        synchronized(udpSocketMap) {
                            udpSocketMap.remove(tunnel.id)
                        }
                    }
                }
            }
        }
    }
}
