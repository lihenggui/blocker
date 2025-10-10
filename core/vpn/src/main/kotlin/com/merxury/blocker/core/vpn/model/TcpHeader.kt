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

package com.merxury.blocker.core.vpn.model

import com.merxury.blocker.core.vpn.extension.toUnsignedInt
import com.merxury.blocker.core.vpn.extension.toUnsignedShort
import com.merxury.blocker.core.vpn.protocol.Packet.Companion.TCP_HEADER_SIZE
import java.nio.ByteBuffer

data class TcpHeader(
    var sourcePort: Int = 0,
    var destinationPort: Int = 0,
    var sequenceNumber: Long = 0,
    var acknowledgementNumber: Long = 0,
    var dataOffsetAndReserved: Byte = 0,
    var headerLength: Int = 0,
    var flags: Byte = 0,
    var window: Int = 0,
    var checksum: Int = 0,
    var urgentPointer: Int = 0,
    var optionsAndPadding: ByteArray? = null,
) {
    companion object {
        const val FIN = 0x01
        const val SYN = 0x02
        const val RST = 0x04
        const val PSH = 0x08
        const val ACK = 0x10
        const val URG = 0x20
    }

    constructor(buffer: ByteBuffer) : this() {
        sourcePort = buffer.short.toUnsignedShort()
        destinationPort = buffer.short.toUnsignedShort()

        sequenceNumber = buffer.int.toUnsignedInt()
        acknowledgementNumber = buffer.int.toUnsignedInt()

        dataOffsetAndReserved = buffer.get()
        headerLength = (dataOffsetAndReserved.toInt() and 0xF0) shr 2
        flags = buffer.get()
        window = buffer.short.toUnsignedShort()

        checksum = buffer.short.toUnsignedShort()
        urgentPointer = buffer.short.toUnsignedShort()

        val optionsLength = headerLength - TCP_HEADER_SIZE
        if (optionsLength > 0) {
            optionsAndPadding = ByteArray(optionsLength)
            optionsAndPadding?.let {
                buffer.get(it, 0, optionsLength)
            }
        }
    }

    val isFin: Boolean
        get() = (flags.toInt() and FIN) == FIN

    val isSyn: Boolean
        get() = (flags.toInt() and SYN) == SYN

    val isRst: Boolean
        get() = (flags.toInt() and RST) == RST

    val isPsh: Boolean
        get() = (flags.toInt() and PSH) == PSH

    val isAck: Boolean
        get() = (flags.toInt() and ACK) == ACK

    val isUrg: Boolean
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
        if (isFin) append("FIN ")
        if (isSyn) append("SYN ")
        if (isRst) append("RST ")
        if (isPsh) append("PSH ")
        if (isAck) append("ACK ")
        if (isUrg) append("URG ")
        append("seq $sequenceNumber ")
        append("ack $acknowledgementNumber ")
    }

    override fun toString(): String = buildString {
        append("TcpHeader{")
        append("sourcePort=").append(sourcePort)
        append(", destinationPort=").append(destinationPort)
        append(", sequenceNumber=").append(sequenceNumber)
        append(", acknowledgementNumber=").append(acknowledgementNumber)
        append(", headerLength=").append(headerLength)
        append(", window=").append(window)
        append(", checksum=").append(checksum)
        append(", flags=")
        if (isFin) append(" FIN")
        if (isSyn) append(" SYN")
        if (isRst) append(" RST")
        if (isPsh) append(" PSH")
        if (isAck) append(" ACK")
        if (isUrg) append(" URG")
        append('}')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TcpHeader) return false

        if (sourcePort != other.sourcePort) return false
        if (destinationPort != other.destinationPort) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (acknowledgementNumber != other.acknowledgementNumber) return false
        if (dataOffsetAndReserved != other.dataOffsetAndReserved) return false
        if (headerLength != other.headerLength) return false
        if (flags != other.flags) return false
        if (window != other.window) return false
        if (checksum != other.checksum) return false
        if (urgentPointer != other.urgentPointer) return false
        if (optionsAndPadding != null) {
            if (other.optionsAndPadding == null) return false
            if (!optionsAndPadding.contentEquals(other.optionsAndPadding)) return false
        } else if (other.optionsAndPadding != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = sourcePort
        result = 31 * result + destinationPort
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + acknowledgementNumber.hashCode()
        result = 31 * result + dataOffsetAndReserved
        result = 31 * result + headerLength
        result = 31 * result + flags
        result = 31 * result + window
        result = 31 * result + checksum
        result = 31 * result + urgentPointer
        result = 31 * result + (optionsAndPadding?.contentHashCode() ?: 0)
        return result
    }
}
