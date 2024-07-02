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

import com.merxury.blocker.core.vpn.protocol.Packet.IP4Header.TransportProtocol.TCP
import com.merxury.blocker.core.vpn.protocol.Packet.IP4Header.TransportProtocol.UDP
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Representation of an IP Packet
 */
internal class Packet {
    companion object {
        const val IP4_HEADER_SIZE = 20
        const val TCP_HEADER_SIZE = 20
        const val UDP_HEADER_SIZE = 8

        val globalPackId = AtomicLong()
    }

    var ip4Header: IP4Header? = null
    var tcpHeader: TCPHeader? = null
    var udpHeader: UDPHeader? = null
    var backingBuffer: ByteBuffer? = null

    var isTCP = false

    var isUDP = false

    init {
        globalPackId.incrementAndGet()
    }

    constructor()

    @Throws(UnknownHostException::class)
    constructor(buffer: ByteBuffer) : this() {
        ip4Header = IP4Header(buffer)
        when (ip4Header?.protocol) {
            TCP -> {
                tcpHeader = TCPHeader(buffer)
                isTCP = true
            }

            UDP -> {
                udpHeader = UDPHeader(buffer)
                isUDP = true
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
        if (isTCP) {
            append(", tcpHeader=").append(tcpHeader)
        } else if (isUDP) {
            append(", udpHeader=").append(udpHeader)
        }
        append(", payloadSize=").append(
            backingBuffer?.limit()?.minus(backingBuffer?.position() ?: 0),
        )
        append('}')
    }

    fun updateTCPBuffer(
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

            updateIP4Checksum()
        }
    }

    fun updateUDPBuffer(buffer: ByteBuffer, payloadSize: Int) {
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

            updateIP4Checksum()
        }
    }

    private fun updateIP4Checksum() {
        val buffer = backingBuffer?.duplicate() ?: return
        buffer.position(0)

        // Clear previous checksum
        buffer.putShort(10, 0.toShort())

        var ipLength = ip4Header?.headerLength ?: return
        var sum = 0
        while (ipLength > 0) {
            sum += BitUtils.getUnsignedShort(buffer.short)
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
            sum = BitUtils.getUnsignedShort(buffer.short) + BitUtils.getUnsignedShort(buffer.short)
        }

        ip4Header?.destinationAddress?.address?.let { destinationAddress ->
            val buffer = ByteBuffer.wrap(destinationAddress)
            sum += BitUtils.getUnsignedShort(buffer.short) + BitUtils.getUnsignedShort(buffer.short)
        }

        sum += TCP.number + tcpLength

        val buffer = backingBuffer?.duplicate() ?: return
        // Clear previous checksum
        buffer.putShort(IP4_HEADER_SIZE + 16, 0.toShort())

        // Calculate TCP segment checksum
        buffer.position(IP4_HEADER_SIZE)
        while (tcpLength > 1) {
            sum += BitUtils.getUnsignedShort(buffer.short)
            tcpLength -= 2
        }
        if (tcpLength > 0) {
            sum += BitUtils.getUnsignedByte(buffer.get()).toInt() shl 8
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
        if (isUDP) {
            udpHeader?.fillHeader(buffer)
        } else if (isTCP) {
            tcpHeader?.fillHeader(buffer)
        }
    }

    class IP4Header {
        var version: Byte = 0
        var ihl: Byte = 0
        var headerLength: Int = 0
        var typeOfService: Short = 0
        var totalLength: Int = 0

        var identificationAndFlagsAndFragmentOffset: Int = 0

        var ttl: Short = 0
        var protocolNum: Short = 0
        var protocol: TransportProtocol? = null
        var headerChecksum: Int = 0

        var sourceAddress: InetAddress? = null
        var destinationAddress: InetAddress? = null

        var optionsAndPadding: Int = 0

        enum class TransportProtocol(val number: Int) {
            TCP(6),
            UDP(17),
            OTHER(0xFF),
            ;

            companion object {
                fun numberToEnum(protocolNumber: Int): TransportProtocol = when (protocolNumber) {
                    6 -> TCP
                    17 -> UDP
                    else -> OTHER
                }
            }
        }

        constructor()

        @Throws(UnknownHostException::class)
        constructor(buffer: ByteBuffer) {
            val versionAndIHL = buffer.get()
            version = (versionAndIHL.toInt() shr 4).toByte()
            ihl = (versionAndIHL.toInt() and 0x0F).toByte()
            headerLength = ihl.toInt() shl 2

            typeOfService = BitUtils.getUnsignedByte(buffer.get())
            totalLength = BitUtils.getUnsignedShort(buffer.short)

            identificationAndFlagsAndFragmentOffset = buffer.int

            ttl = BitUtils.getUnsignedByte(buffer.get())
            protocolNum = BitUtils.getUnsignedByte(buffer.get())
            protocol =
                com.merxury.blocker.core.vpn.protocol.Packet.IP4Header.TransportProtocol.numberToEnum(
                    protocolNum.toInt(),
                )
            headerChecksum = BitUtils.getUnsignedShort(buffer.short)

            val addressBytes = ByteArray(4)
            buffer.get(addressBytes, 0, 4)
            sourceAddress = InetAddress.getByAddress(addressBytes)

            buffer.get(addressBytes, 0, 4)
            destinationAddress = InetAddress.getByAddress(addressBytes)
        }

        fun fillHeader(buffer: ByteBuffer) {
            buffer.put((version.toInt() shl 4 or ihl.toInt()).toByte())
            buffer.put(typeOfService.toByte())
            buffer.putShort(totalLength.toShort())

            buffer.putInt(identificationAndFlagsAndFragmentOffset)

            buffer.put(ttl.toByte())
            buffer.put(protocol?.number?.toByte() ?: 0)
            buffer.putShort(headerChecksum.toShort())

            sourceAddress?.address?.let { buffer.put(it) }
            destinationAddress?.address?.let { buffer.put(it) }
        }

        override fun toString(): String = buildString {
            append("IP4Header{")
            append("version=").append(version)
            append(", IHL=").append(ihl)
            append(", typeOfService=").append(typeOfService)
            append(", totalLength=").append(totalLength)
            append(", identificationAndFlagsAndFragmentOffset=").append(
                identificationAndFlagsAndFragmentOffset,
            )
            append(", TTL=").append(ttl)
            append(", protocol=").append(protocolNum).append(":").append(protocol)
            append(", headerChecksum=").append(headerChecksum)
            append(", sourceAddress=").append(sourceAddress?.hostAddress)
            append(", destinationAddress=").append(destinationAddress?.hostAddress)
            append('}')
        }
    }

    class TCPHeader {
        companion object {
            const val FIN = 0x01
            const val SYN = 0x02
            const val RST = 0x04
            const val PSH = 0x08
            const val ACK = 0x10
            const val URG = 0x20
        }

        var sourcePort: Int = 0
        var destinationPort: Int = 0

        var sequenceNumber: Long = 0
        var acknowledgementNumber: Long = 0

        var dataOffsetAndReserved: Byte = 0
        var headerLength: Int = 0
        var flags: Byte = 0
        var window: Int = 0

        var checksum: Int = 0
        var urgentPointer: Int = 0

        var optionsAndPadding: ByteArray? = null

        constructor(buffer: ByteBuffer) {
            sourcePort = BitUtils.getUnsignedShort(buffer.short)
            destinationPort = BitUtils.getUnsignedShort(buffer.short)

            sequenceNumber = BitUtils.getUnsignedInt(buffer.int)
            acknowledgementNumber = BitUtils.getUnsignedInt(buffer.int)

            dataOffsetAndReserved = buffer.get()
            headerLength = (dataOffsetAndReserved.toInt() and 0xF0) shr 2
            flags = buffer.get()
            window = BitUtils.getUnsignedShort(buffer.short)

            checksum = BitUtils.getUnsignedShort(buffer.short)
            urgentPointer = BitUtils.getUnsignedShort(buffer.short)

            val optionsLength = headerLength - TCP_HEADER_SIZE
            if (optionsLength > 0) {
                optionsAndPadding = ByteArray(optionsLength)
                optionsAndPadding?.let {
                    buffer.get(it, 0, optionsLength)
                }
            }
        }

        constructor()

        val isFIN: Boolean
            get() = (flags.toInt() and FIN) == FIN

        val isSYN: Boolean
            get() = (flags.toInt() and SYN) == SYN

        val isRST: Boolean
            get() = (flags.toInt() and RST) == RST

        val isPSH: Boolean
            get() = (flags.toInt() and PSH) == PSH

        val isACK: Boolean
            get() = (flags.toInt() and ACK) == ACK

        val isURG: Boolean
            get() = (flags.toInt() and URG) == URG

        fun fillHeader(buffer: ByteBuffer) {
            buffer.putShort(sourcePort.toShort())
            buffer.putShort(destinationPort.toShort())

            buffer.putInt(sequenceNumber.toInt())
            buffer.putInt(acknowledgementNumber.toInt())

            buffer.put(dataOffsetAndReserved)
            buffer.put(flags)
            buffer.putShort(window.toShort())

            buffer.putShort(checksum.toShort())
            buffer.putShort(urgentPointer.toShort())

            optionsAndPadding?.let {
                buffer.put(it)
            }
        }

        fun printSimple(): String = buildString {
            if (isFIN) append("FIN ")
            if (isSYN) append("SYN ")
            if (isRST) append("RST ")
            if (isPSH) append("PSH ")
            if (isACK) append("ACK ")
            if (isURG) append("URG ")
            append("seq $sequenceNumber ")
            append("ack $acknowledgementNumber ")
        }

        override fun toString(): String = buildString {
            append("TCPHeader{")
            append("sourcePort=").append(sourcePort)
            append(", destinationPort=").append(destinationPort)
            append(", sequenceNumber=").append(sequenceNumber)
            append(", acknowledgementNumber=").append(acknowledgementNumber)
            append(", headerLength=").append(headerLength)
            append(", window=").append(window)
            append(", checksum=").append(checksum)
            append(", flags=")
            if (isFIN) append(" FIN")
            if (isSYN) append(" SYN")
            if (isRST) append(" RST")
            if (isPSH) append(" PSH")
            if (isACK) append(" ACK")
            if (isURG) append(" URG")
            append('}')
        }
    }

    class UDPHeader {
        var sourcePort: Int = 0
        var destinationPort: Int = 0

        var length: Int = 0
        var checksum: Int = 0

        constructor()

        constructor(buffer: ByteBuffer) {
            sourcePort = BitUtils.getUnsignedShort(buffer.short)
            destinationPort = BitUtils.getUnsignedShort(buffer.short)

            length = BitUtils.getUnsignedShort(buffer.short)
            checksum = BitUtils.getUnsignedShort(buffer.short)
        }

        fun fillHeader(buffer: ByteBuffer) {
            buffer.putShort(sourcePort.toShort())
            buffer.putShort(destinationPort.toShort())

            buffer.putShort(length.toShort())
            buffer.putShort(checksum.toShort())
        }

        override fun toString(): String = buildString {
            append("UDPHeader{")
            append("sourcePort=").append(sourcePort)
            append(", destinationPort=").append(destinationPort)
            append(", length=").append(length)
            append(", checksum=").append(checksum)
            append('}')
        }
    }

    private object BitUtils {
        fun getUnsignedByte(value: Byte): Short = (value.toInt() and 0xFF).toShort()

        fun getUnsignedShort(value: Short): Int = value.toInt() and 0xFFFF

        fun getUnsignedInt(value: Int): Long = value.toLong() and 0xFFFFFFFFL
    }
}
