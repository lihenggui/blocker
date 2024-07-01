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

import java.net.InetSocketAddress

internal object IpUtil {
    fun buildUdpPacket(source: InetSocketAddress, dest: InetSocketAddress, ipId: Int): Packet {
        val packet = Packet().apply {
            isTCP = false
            isUDP = true
        }

        val ip4Header = Packet.IP4Header().apply {
            version = 4
            ihl = 5
            destinationAddress = dest.address
            headerChecksum = 0
            headerLength = 20
            identificationAndFlagsAndFragmentOffset = ipId shl 16 or (0x40 shl 8) or 0
            optionsAndPadding = 0
            protocol = Packet.IP4Header.TransportProtocol.UDP
            protocolNum = 17
            sourceAddress = source.address
            totalLength = 60
            typeOfService = 0
            ttl = 64
        }

        val udpHeader = Packet.UDPHeader().apply {
            sourcePort = source.port
            destinationPort = dest.port
            length = 0
        }

        packet.ip4Header = ip4Header
        packet.udpHeader = udpHeader
        return packet
    }

    fun buildTcpPacket(
        source: InetSocketAddress,
        dest: InetSocketAddress,
        flag: Byte,
        ack: Long,
        seq: Long,
        ipId: Int,
    ): Packet {
        val packet = Packet().apply {
            isTCP = true
            isUDP = false
        }

        val ip4Header = Packet.IP4Header().apply {
            version = 4
            ihl = 5
            destinationAddress = dest.address
            headerChecksum = 0
            headerLength = 20
            identificationAndFlagsAndFragmentOffset = ipId shl 16 or (0x40 shl 8) or 0
            optionsAndPadding = 0
            protocol = Packet.IP4Header.TransportProtocol.TCP
            protocolNum = 6
            sourceAddress = source.address
            totalLength = 60
            typeOfService = 0
            ttl = 64
        }

        val tcpHeader = Packet.TCPHeader().apply {
            acknowledgementNumber = ack
            checksum = 0
            dataOffsetAndReserved = -96
            destinationPort = dest.port
            flags = flag
            headerLength = 40
            optionsAndPadding = null
            sequenceNumber = seq
            sourcePort = source.port
            urgentPointer = 0
            window = 65535
        }

        packet.ip4Header = ip4Header
        packet.tcpHeader = tcpHeader
        return packet
    }
}
