/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.core.data.util

import app.cash.turbine.test
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.controller.FakeShizukuInitializer
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppPermissionMonitorTest {
    private val preferences = TestUserDataRepository()
    private val shizuku = FakeShizukuInitializer(shizukuRunning = false)
    private val channel = FakePrivilegedChannel(DENIED)

    @Test
    fun givenNoScreenObserves_whenAppStartsInTheBackground_thenMonitorNeverPrompts() = runTest {
        preferences.setControllerType(PM)
        monitor()
        runCurrent()

        assertEquals(0, channel.connectCount)
        assertEquals(0, channel.quietConnectCount)
        assertEquals(0, shizuku.registerCallCount)
    }

    @Test
    fun givenRootNotChecked_whenScreenObserves_thenRequestsOnceForTheFirstObservation() = runTest {
        preferences.setControllerType(PM)
        val monitor = monitor()
        monitor.permissionStatus.test {
            assertEquals(PermissionStatus.ROOT_USER, awaitItem())
        }
        monitor.permissionStatus.test { assertEquals(PermissionStatus.ROOT_USER, awaitItem()) }
        assertEquals(1, channel.connectCount)
    }

    @Test
    fun givenRootRefused_whenScreenObserves_thenDoesNotPromptAgain() = runTest {
        preferences.setControllerType(PM)
        channel.connectResult = DENIED
        monitor().permissionStatus.test {
            assertEquals(PermissionStatus.NO_PERMISSION, awaitItem())
            runCurrent()
            expectNoEvents()
        }
        assertEquals(1, channel.connectCount)
    }

    @Test
    fun givenShizukuRunsAsSystem_whenScreenObserves_thenReportsSystemAccess() = runTest {
        preferences.setControllerType(SHIZUKU)
        channel.connectResult = SYSTEM
        monitor().permissionStatus.test { assertEquals(PermissionStatus.SYSTEM_USER, awaitItem()) }
        assertEquals(1, channel.connectCount)
        assertEquals(0, shizuku.registerCallCount)
    }

    @Test
    fun givenShizukuNotRunning_whenItsBinderArrives_thenRequestsAccessAgain() = runTest {
        preferences.setControllerType(SHIZUKU)
        channel.connectResult = ChannelState.Unavailable(UnavailableReason.NOT_RUNNING)
        monitor().permissionStatus.test {
            assertEquals(PermissionStatus.NO_PERMISSION, awaitItem())
            channel.connectResult = SYSTEM
            shizuku.startShizuku()

            assertEquals(PermissionStatus.SYSTEM_USER, awaitItem())
            assertEquals(2, channel.connectCount)
            assertEquals(0, shizuku.binderWaiterCount)
        }
    }

    @Test
    fun givenShizukuStartsButPermissionIsDenied_whenRetrying_thenDoesNotAskAgain() = runTest {
        preferences.setControllerType(SHIZUKU)
        channel.connectResult = ChannelState.Unavailable(UnavailableReason.NOT_RUNNING)
        monitor().permissionStatus.test {
            assertEquals(PermissionStatus.NO_PERMISSION, awaitItem())

            channel.connectResult = DENIED
            shizuku.startShizuku()

            assertEquals(PermissionStatus.NO_PERMISSION, awaitItem())
            runCurrent()
            assertEquals(2, channel.connectCount)
            assertEquals(0, shizuku.binderWaiterCount)
            expectNoEvents()
        }
    }

    @Test
    fun givenShizukuWasDenied_whenSwitchingBackAfterAGrant_thenReportsTheNewPermission() = runTest {
        preferences.setControllerType(SHIZUKU)
        channel.connectResult = DENIED
        monitor().permissionStatus.test {
            assertEquals(PermissionStatus.NO_PERMISSION, awaitItem())

            channel.connectResult = ROOT
            preferences.setControllerType(PM)
            assertEquals(PermissionStatus.ROOT_USER, awaitItem())

            channel.connectResult = SYSTEM
            preferences.setControllerType(SHIZUKU)
            assertEquals(PermissionStatus.SYSTEM_USER, awaitItem())
        }
        assertEquals(3, channel.connectCount)
    }

    @Test
    fun givenShizukuNotRunning_whenRootIsSelected_thenCancelsBinderWait() = runTest {
        preferences.setControllerType(SHIZUKU)
        channel.connectResult = ChannelState.Unavailable(UnavailableReason.NOT_RUNNING)
        monitor().permissionStatus.test {
            assertEquals(PermissionStatus.NO_PERMISSION, awaitItem())
            runCurrent()
            assertEquals(1, shizuku.binderWaiterCount)

            channel.connectResult = ROOT
            preferences.setControllerType(PM)

            assertEquals(PermissionStatus.ROOT_USER, awaitItem())
            assertEquals(0, shizuku.binderWaiterCount)
        }
    }

    @Test
    fun givenPendingShizukuPrompt_whenRootIsSelected_thenCancelsTheOldRequest() = runTest {
        preferences.setControllerType(SHIZUKU)
        var attempts = 0
        val pendingChannel = object : PrivilegedChannel by channel {
            override suspend fun connect(): ChannelState {
                if (attempts++ == 0) awaitCancellation()
                return ROOT
            }
        }
        monitor(pendingChannel).permissionStatus.test {
            runCurrent()
            assertEquals(1, attempts)

            preferences.setControllerType(PM)

            assertEquals(PermissionStatus.ROOT_USER, awaitItem())
            assertEquals(2, attempts)
        }
    }

    private fun TestScope.monitor(privilegedChannel: PrivilegedChannel = channel) = AppPermissionMonitor(preferences, shizuku, privilegedChannel, backgroundScope)

    private companion object {
        val DENIED = ChannelState.Unavailable(UnavailableReason.DENIED)
        val ROOT = ChannelState.Ready(PrivilegedIdentity.ROOT)
        val SYSTEM = ChannelState.Ready(PrivilegedIdentity.SYSTEM)
    }
}
