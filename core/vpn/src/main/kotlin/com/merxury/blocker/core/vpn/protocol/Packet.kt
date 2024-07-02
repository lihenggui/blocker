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

import com.merxury.blocker.core.vpn.extension.toUnsignedByte
import com.merxury.blocker.core.vpn.extension.toUnsignedShort
import com.merxury.blocker.core.vpn.model.Ip4Header
import com.merxury.blocker.core.vpn.model.TcpHeader
import com.merxury.blocker.core.vpn.model.TransportProtocol.TCP
import com.merxury.blocker.core.vpn.model.TransportProtocol.UDP
import com.merxury.blocker.core.vpn.model.UdpHeader
import java.net.UnknownHostException
import java.nio.ByteBuffer

/**
 * Representation of an IP Packet
 */
internal data class Packet(
    var ip4Header: Ip4Header? = null,
    var tcpHeader: TcpHeader? = null,
    var udpHeader: UdpHeader? = null,
    var backingBuffer: ByteBuffer? = null,
    var isTcp: Boolean = false,
    var isUdp: Boolean = false,
) {
    companion object {
        const val IP4_HEADER_SIZE = 20
        const val TCP_HEADER_SIZE = 20
        const val UDP_HEADER_SIZE = 8
    }

    @Throws(UnknownHostException::class)
    constructor(buffer: ByteBuffer) : this() {
        ip4Header = Ip4Header(buffer)
        when (ip4Header?.protocol) {
            TCP -> {
                tcpHeader = TcpHeader(buffer)
                isTcp = true
            }
            UDP -> {
                udpHeader = UdpHeader(buffer)
                isUdp = true
            }
            else -> {}
        }
        backingBuffer = buffer
    }

    fun release() {
        ip4Header = null
        tcpHeader = null
        udpHeader = null
        backingBuffer = null
    }

    override fun toString(): String = buildString {
        append("Packet{")
        append("ip4Header=").append(ip4Header)
        if (isTcp) {
            append(", tcpHeader=").append(tcpHeader)
        } else if (isUdp) {
            append(", udpHeader=").append(udpHeader)
        }
        append(", payloadSize=").append(
            backingBuffer?.limit()?.minus(backingBuffer?.position() ?: 0),
        )
        append('}')
    }

    fun updateTcpBuffer(
        buffer: ByteBuffer,
        flags: Byte,
        sequenceNum: Long,
        ackNum: Long,
        payloadSize: Int,
    ) {
        buffer.position(0)
        fillHeader(buffer)
        backingBuffer = buffer

        tcpHeader?.apply {
            this.flags = flags
            backingBuffer?.put(IP4_HEADER_SIZE + 13, flags)

            this.sequenceNumber = sequenceNum
            backingBuffer?.putInt(IP4_HEADER_SIZE + 4, sequenceNum.toInt())

            this.acknowledgementNumber = ackNum
            backingBuffer?.putInt(IP4_HEADER_SIZE + 8, ackNum.toInt())

            // Reset header size, since we don't need options
            val dataOffset = (TCP_HEADER_SIZE shl 2).toByte()
            this.dataOffsetAndReserved = dataOffset
            backingBuffer?.put(IP4_HEADER_SIZE + 12, dataOffset)

            updateTCPChecksum(payloadSize)

            val ip4TotalLength = IP4_HEADER_SIZE + TCP_HEADER_SIZE + payloadSize
            backingBuffer?.putShort(2, ip4TotalLength.toShort())
            ip4Header?.totalLength = ip4TotalLength

            updateIp4Checksum()
        }
    }

    fun updateUdpBuffer(buffer: ByteBuffer, payloadSize: Int) {
        buffer.position(0)
        fillHeader(buffer)
        backingBuffer = buffer

        udpHeader?.apply {
            val udpTotalLength = UDP_HEADER_SIZE + payloadSize
            backingBuffer?.putShort(IP4_HEADER_SIZE + 4, udpTotalLength.toShort())
            this.length = udpTotalLength

            // Disable UDP checksum validation
            backingBuffer?.putShort(IP4_HEADER_SIZE + 6, 0.toShort())
            this.checksum = 0

            val ip4TotalLength = IP4_HEADER_SIZE + udpTotalLength
            backingBuffer?.putShort(2, ip4TotalLength.toShort())
            ip4Header?.totalLength = ip4TotalLength

            updateIp4Checksum()
        }
    }

    private fun updateIp4Checksum() {
        val buffer = backingBuffer?.duplicate() ?: return
        buffer.position(0)

        // Clear previous checksum
        buffer.putShort(10, 0.toShort())

        var ipLength = ip4Header?.headerLength ?: return
        var sum = 0
        while (ipLength > 0) {
            sum += buffer.short.toUnsignedShort()
            ipLength -= 2
        }
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        sum = sum.inv()
        ip4Header?.headerChecksum = sum
        backingBuffer?.putShort(10, sum.toShort())
    }

    private fun updateTCPChecksum(payloadSize: Int) {
        var sum = 0
        var tcpLength = TCP_HEADER_SIZE + payloadSize

        // Calculate pseudo-header checksum
        ip4Header?.sourceAddress?.address?.let { sourceAddress ->
            val buffer = ByteBuffer.wrap(sourceAddress)
            sum = buffer.short.toUnsignedShort() + buffer.short.toUnsignedShort()
        }

        ip4Header?.destinationAddress?.address?.let { destinationAddress ->
            val buffer = ByteBuffer.wrap(destinationAddress)
            sum += buffer.short.toUnsignedShort() + buffer.short.toUnsignedShort()
        }

        sum += TCP.number + tcpLength

        val buffer = backingBuffer?.duplicate() ?: return
        // Clear previous checksum
        buffer.putShort(IP4_HEADER_SIZE + 16, 0.toShort())

        // Calculate TCP segment checksum
        buffer.position(IP4_HEADER_SIZE)
        while (tcpLength > 1) {
            sum += buffer.short.toUnsignedShort()
            tcpLength -= 2
        }
        if (tcpLength > 0) {
            sum += buffer.get().toUnsignedByte().toInt() shl 8
        }

        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        sum = sum.inv()
        tcpHeader?.checksum = sum
        backingBuffer?.putShort(IP4_HEADER_SIZE + 16, sum.toShort())
    }

    private fun fillHeader(buffer: ByteBuffer) {
        ip4Header?.fillHeader(buffer)
        if (isUdp) {
            udpHeader?.fillHeader(buffer)
        } else if (isTcp) {
            tcpHeader?.fillHeader(buffer)
        }
    }
}
