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
import com.merxury.blocker.core.vpn.extension.toUnsignedByte
import com.merxury.blocker.core.vpn.extension.toUnsignedShort
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer

internal data class Ip4Header(
    var version: Byte = 0,
    var ihl: Byte = 0,
    var headerLength: Int = 0,
    var typeOfService: Short = 0,
    var totalLength: Int = 0,
    var identificationAndFlagsAndFragmentOffset: Int = 0,
    var ttl: Short = 0,
    var protocolNum: Short = 0,
    var protocol: TransportProtocol? = null,
    var headerChecksum: Int = 0,
    var sourceAddress: InetAddress? = null,
    var destinationAddress: InetAddress? = null,
    var optionsAndPadding: Int = 0,
) {
    @Throws(UnknownHostException::class)
    constructor(buffer: ByteBuffer) : this() {
        val versionAndIHL = buffer.get()
        version = (versionAndIHL.toInt() shr 4).toByte()
        ihl = (versionAndIHL.toInt() and 0x0F).toByte()
        headerLength = ihl.toInt() shl 2

        typeOfService = buffer.get().toUnsignedByte()
        totalLength = buffer.short.toUnsignedShort()

        identificationAndFlagsAndFragmentOffset = buffer.int

        ttl = buffer.get().toUnsignedByte()
        protocolNum = buffer.get().toUnsignedByte()
        protocol = TransportProtocol.numberToEnum(protocolNum.toInt())
        headerChecksum = buffer.short.toUnsignedShort()

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
}
