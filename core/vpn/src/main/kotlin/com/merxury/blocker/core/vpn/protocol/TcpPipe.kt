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

package com.merxury.blocker.core.vpn.protocol

import android.net.VpnService
import com.merxury.blocker.core.vpn.tcpNioSelector
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel

internal class TcpPipe(val tunnelKey: String, packet: Packet) {
    var mySequenceNum: Long = 0
    var theirSequenceNum: Long = 0
    var myAcknowledgementNum: Long = 0
    var theirAcknowledgementNum: Long = 0
    val tunnelId = tunnelIds++

    val sourceAddress: InetSocketAddress =
        InetSocketAddress(packet.ip4Header?.sourceAddress, packet.tcpHeader?.sourcePort ?: 0)
    val destinationAddress: InetSocketAddress = InetSocketAddress(
        packet.ip4Header?.destinationAddress,
        packet.tcpHeader?.destinationPort ?: 0,
    )
    val remoteSocketChannel: SocketChannel =
        SocketChannel.open().also { it.configureBlocking(false) }
    val remoteSocketChannelKey: SelectionKey =
        remoteSocketChannel.register(tcpNioSelector, SelectionKey.OP_CONNECT)
            .also { it.attach(this) }

    var tcbStatus: TcbStatus = TcbStatus.SYN_SENT
    var remoteOutBuffer: ByteBuffer? = null

    var upActive = true
    var downActive = true
    var packId = 1
    var timestamp = System.currentTimeMillis()
    var synCount = 0

    fun tryConnect(vpnService: VpnService): Result<Boolean> {
        val result = runCatching {
            vpnService.protect(remoteSocketChannel.socket())
            remoteSocketChannel.connect(destinationAddress)
        }
        return result
    }

    companion object {
        var tunnelIds = 0
    }
}
