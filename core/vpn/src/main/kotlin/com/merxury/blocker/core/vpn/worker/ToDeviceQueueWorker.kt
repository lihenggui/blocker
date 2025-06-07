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
import com.merxury.blocker.core.vpn.networkToDeviceQueue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.FileChannel
import javax.inject.Inject

class ToDeviceQueueWorker @Inject constructor(
    @Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
) {

    private lateinit var vpnOutput: FileChannel
    private var totalOutputCount = 0L

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun start(vpnFileDescriptor: FileDescriptor) {
        vpnOutput = FileOutputStream(vpnFileDescriptor).channel
        scope.launch {
            runWorker()
        }
    }

    fun stop() {
        scope.cancel()
    }

    private suspend fun runWorker() = withContext(dispatcher) {
        try {
            while (scope.isActive) {
                val byteBuffer = networkToDeviceQueue.take()
                byteBuffer.flip()
                while (byteBuffer.hasRemaining()) {
                    val count = vpnOutput.write(byteBuffer)
                    if (count > 0) {
                        totalOutputCount += count
                    }
                }
            }
        } catch (e: InterruptedException) {
            Timber.e(e.message)
        } catch (e: ClosedByInterruptException) {
            Timber.e(e.message)
        }
    }
}
