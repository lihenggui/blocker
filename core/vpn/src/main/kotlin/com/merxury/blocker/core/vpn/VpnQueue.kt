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

package com.merxury.blocker.core.vpn

import com.merxury.blocker.core.vpn.model.ManagedDatagramChannel
import com.merxury.blocker.core.vpn.model.UdpTunnel
import com.merxury.blocker.core.vpn.protocol.Packet
import java.nio.ByteBuffer
import java.nio.channels.Selector
import java.util.concurrent.ArrayBlockingQueue

/**
 * Queue for UDP packets sent from device to network
 */
internal val deviceToNetworkUDPQueue = ArrayBlockingQueue<Packet>(1024)

/**
 * Queue for TCP packets sent from device to network
 */
internal val deviceToNetworkTCPQueue = ArrayBlockingQueue<Packet>(1024)

/**
 * Queue for packets sent from network to device
 */
internal val networkToDeviceQueue = ArrayBlockingQueue<ByteBuffer>(1024)

/**
 * TCP forwarding network selector
 */
internal val tcpNioSelector: Selector = Selector.open()

/**
 * Queue for UDP forwarding channels
 */
internal val udpTunnelQueue = ArrayBlockingQueue<UdpTunnel>(1024)

/**
 * UDP forwarding network selector
 */
internal val udpNioSelector: Selector = Selector.open()

/**
 * Existing UDP socket map
 * key is target host address:target port:request port
 */
internal val udpSocketMap = HashMap<String, ManagedDatagramChannel>()

const val UDP_SOCKET_IDLE_TIMEOUT = 60
