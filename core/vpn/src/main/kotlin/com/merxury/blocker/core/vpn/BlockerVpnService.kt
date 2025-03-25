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

import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.vpn.worker.TcpWorker
import com.merxury.blocker.core.vpn.worker.ToDeviceQueueWorker
import com.merxury.blocker.core.vpn.worker.ToNetworkQueueWorker
import com.merxury.blocker.core.vpn.worker.UdpReceiveWorker
import com.merxury.blocker.core.vpn.worker.UdpSendWorker
import com.merxury.blocker.core.vpn.worker.UdpSocketCleanWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@AndroidEntryPoint
class BlockerVpnService : VpnService() {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    @Dispatcher(IO)
    lateinit var ioDispatcher: CoroutineDispatcher

    private var vpnInterface: ParcelFileDescriptor? = null
    private lateinit var toNetworkQueueWorker: ToNetworkQueueWorker
    private lateinit var toDeviceQueueWorker: ToDeviceQueueWorker
    private lateinit var udpSendWorker: UdpSendWorker
    private lateinit var udpReceiveWorker: UdpReceiveWorker
    private lateinit var udpSocketCleanWorker: UdpSocketCleanWorker
    private lateinit var tcpWorker: TcpWorker

    override fun onCreate() {
        super.onCreate()
        toNetworkQueueWorker = ToNetworkQueueWorker(ioDispatcher)
        toDeviceQueueWorker = ToDeviceQueueWorker(ioDispatcher)
        udpSendWorker = UdpSendWorker(ioDispatcher)
        udpReceiveWorker = UdpReceiveWorker(ioDispatcher)
        udpSocketCleanWorker = UdpSocketCleanWorker(ioDispatcher)
        tcpWorker = TcpWorker(ioDispatcher)

        udpSendWorker.start(this)
        udpReceiveWorker.start()
        udpSocketCleanWorker.start()
        tcpWorker.start(this)
        startVpn()
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        udpSendWorker.stop()
        udpReceiveWorker.stop()
        udpSocketCleanWorker.stop()
        tcpWorker.stop()
        vpnInterface?.close()
        vpnInterface = null
    }

    private fun startVpn() {
        val builder = Builder()
        builder.addAddress("10.0.0.2", 24)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer("8.8.8.8")
        builder.addDnsServer("8.8.4.4")
        vpnInterface = builder.establish()

        vpnInterface?.let {
            runVpn(it)
        }
    }

    private fun runVpn(vpnInterface: ParcelFileDescriptor) {
        val fileDescriptor = vpnInterface.fileDescriptor
        toNetworkQueueWorker.start(fileDescriptor)
        toDeviceQueueWorker.start(fileDescriptor)
    }

    private fun disconnect() {
        toNetworkQueueWorker.stop()
        toDeviceQueueWorker.stop()
        vpnInterface?.close()
        vpnInterface = null

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            @Suppress("DEPRECATION")
            stopForeground(true)
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }
}
