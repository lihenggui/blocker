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
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.shizuku.IShizukuInitializer
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult
import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.testing.controller.FakeAppController
import com.merxury.blocker.core.testing.controller.FakeController
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.controller.FakeServiceController
import com.merxury.blocker.core.testing.controller.FakeShizukuInitializer
import com.merxury.blocker.core.testing.repository.TestUserDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppPermissionMonitorTest {
    @Test
    fun givenShizukuSystemUid_whenObservePermission_thenSystemUser() = runTest {
        val userDataRepository = TestUserDataRepository()
        val monitor = AppPermissionMonitor(
            userDataRepository = userDataRepository,
            shizukuInitializer = FakeShizukuInitializer(shizukuUid = SYSTEM_UID),
            rootApiController = FakeController(rootGranted = true),
            rootApiAppController = FakeAppController(rootGranted = true),
            rootApiServiceController = FakeServiceController(),
            channel = FakePrivilegedChannel(),
            appScope = backgroundScope,
        )

        userDataRepository.setControllerType(SHIZUKU)

        monitor.permissionStatus.test {
            assertEquals(PermissionStatus.SYSTEM_USER, awaitItem())
        }
    }

    @Test
    fun givenShizukuRegistrationPending_whenSwitchToRoot_thenRootStatusEmitted() = runTest {
        val userDataRepository = TestUserDataRepository()
        val shizukuInitializer = PendingShizukuInitializer()
        val monitor = createMonitor(userDataRepository, shizukuInitializer, backgroundScope)
        userDataRepository.setControllerType(SHIZUKU)

        monitor.permissionStatus.test {
            runCurrent()
            assertEquals(1, shizukuInitializer.registerCallCount)

            userDataRepository.setControllerType(PM)

            assertEquals(PermissionStatus.ROOT_USER, awaitItem())
        }
    }

    @Test
    fun givenShizukuDenied_whenSwitchToRootAndBack_thenFreshStatusEmitted() = runTest {
        val userDataRepository = TestUserDataRepository()
        val shizukuInitializer = FakeShizukuInitializer(shizukuUid = SYSTEM_UID, permissionGranted = false)
        val monitor = createMonitor(userDataRepository, shizukuInitializer, backgroundScope)
        userDataRepository.setControllerType(SHIZUKU)

        monitor.permissionStatus.test {
            assertEquals(PermissionStatus.NO_PERMISSION, awaitItem())

            userDataRepository.setControllerType(PM)
            assertEquals(PermissionStatus.ROOT_USER, awaitItem())

            shizukuInitializer.permissionGranted = true
            userDataRepository.setControllerType(SHIZUKU)
            assertEquals(PermissionStatus.SYSTEM_USER, awaitItem())
        }
    }

    @Test
    fun givenShizukuNotRunning_whenShizukuStarts_thenRegistersAgainAndEmitsGranted() = runTest {
        val userDataRepository = TestUserDataRepository()
        val shizukuInitializer = FakeShizukuInitializer(
            shizukuUid = SYSTEM_UID,
            permissionGranted = false,
            grantsOnRequest = true,
            shizukuRunning = false,
        )
        val monitor = createMonitor(userDataRepository, shizukuInitializer, backgroundScope)
        userDataRepository.setControllerType(SHIZUKU)

        monitor.permissionStatus.test {
            assertEquals(PermissionStatus.NO_PERMISSION, awaitItem())

            shizukuInitializer.startShizuku()

            assertEquals(PermissionStatus.SYSTEM_USER, awaitItem())
            assertEquals(2, shizukuInitializer.registerCallCount)
            assertEquals(0, shizukuInitializer.binderWaiterCount)
        }
    }

    @Test
    fun givenShizukuStartsButPermissionDenied_whenRegisteredAgain_thenStopsWaiting() = runTest {
        val userDataRepository = TestUserDataRepository()
        val shizukuInitializer = FakeShizukuInitializer(permissionGranted = false, shizukuRunning = false)
        val monitor = createMonitor(userDataRepository, shizukuInitializer, backgroundScope)
        userDataRepository.setControllerType(SHIZUKU)

        monitor.permissionStatus.test {
            assertEquals(PermissionStatus.NO_PERMISSION, awaitItem())

            shizukuInitializer.startShizuku()

            assertEquals(PermissionStatus.NO_PERMISSION, awaitItem())
            runCurrent()
            assertEquals(2, shizukuInitializer.registerCallCount)
            assertEquals(0, shizukuInitializer.binderWaiterCount)
            expectNoEvents()
        }
    }

    @Test
    fun givenWaitingForShizukuToStart_whenSwitchToRoot_thenWaitCancelled() = runTest {
        val userDataRepository = TestUserDataRepository()
        val shizukuInitializer = FakeShizukuInitializer(shizukuRunning = false)
        val monitor = createMonitor(userDataRepository, shizukuInitializer, backgroundScope)
        userDataRepository.setControllerType(SHIZUKU)

        monitor.permissionStatus.test {
            assertEquals(PermissionStatus.NO_PERMISSION, awaitItem())
            runCurrent()
            assertEquals(1, shizukuInitializer.binderWaiterCount)

            userDataRepository.setControllerType(PM)

            assertEquals(PermissionStatus.ROOT_USER, awaitItem())
            assertEquals(0, shizukuInitializer.binderWaiterCount)
            shizukuInitializer.startShizuku()
            runCurrent()
            assertEquals(1, shizukuInitializer.registerCallCount)
            expectNoEvents()
        }
    }

    @Test
    fun givenRootGrantedAndChannelNotConnected_whenObserved_thenChannelConnectedOnce() = runTest {
        val userDataRepository = TestUserDataRepository()
        val channel = FakePrivilegedChannel(DENIED)
        val monitor = createMonitor(userDataRepository, FakeShizukuInitializer(), backgroundScope, channel = channel)
        userDataRepository.setControllerType(PM)

        monitor.permissionStatus.test {
            assertEquals(PermissionStatus.ROOT_USER, awaitItem())
            runCurrent()
            assertEquals(1, channel.connectCount)
            assertEquals(ChannelState.Ready(PrivilegedIdentity.ROOT), channel.state.value)
        }
        // Observing again, as another screen does, connects nothing more.
        monitor.permissionStatus.test {
            assertEquals(PermissionStatus.ROOT_USER, awaitItem())
        }
        assertEquals(1, channel.connectCount)
    }

    @Test
    fun givenRootRefused_whenObserved_thenChannelNotAskedAgain() = runTest {
        val userDataRepository = TestUserDataRepository()
        val channel = FakePrivilegedChannel(DENIED)
        val monitor = createMonitor(userDataRepository, FakeShizukuInitializer(), backgroundScope, RefusingController(), channel)
        userDataRepository.setControllerType(PM)

        monitor.permissionStatus.test {
            assertEquals(PermissionStatus.NO_PERMISSION, awaitItem())
            runCurrent()
        }
        assertEquals(0, channel.connectCount)
    }

    @Test
    fun givenShizukuGrantedOrDenied_whenObserved_thenChannelConnectedOnlyAfterTheGrant() = runTest {
        val userDataRepository = TestUserDataRepository()
        val shizukuInitializer = FakeShizukuInitializer(shizukuUid = SYSTEM_UID, permissionGranted = false)
        val channel = FakePrivilegedChannel(DENIED).apply { connectResult = ChannelState.Ready(PrivilegedIdentity.SYSTEM) }
        val monitor = createMonitor(userDataRepository, shizukuInitializer, backgroundScope, channel = channel)
        userDataRepository.setControllerType(SHIZUKU)

        monitor.permissionStatus.test {
            assertEquals(PermissionStatus.NO_PERMISSION, awaitItem())
            runCurrent()
            assertEquals(0, channel.connectCount)

            userDataRepository.setControllerType(PM)
            assertEquals(PermissionStatus.ROOT_USER, awaitItem())
            runCurrent()
            assertEquals(1, channel.connectCount)
        }
    }

    @Test
    fun givenChannelReadyAlready_whenObserved_thenNotConnectedAgain() = runTest {
        val userDataRepository = TestUserDataRepository()
        val channel = FakePrivilegedChannel(ChannelState.Ready(PrivilegedIdentity.ROOT))
        val monitor = createMonitor(userDataRepository, FakeShizukuInitializer(), backgroundScope, channel = channel)
        userDataRepository.setControllerType(PM)

        monitor.permissionStatus.test {
            assertEquals(PermissionStatus.ROOT_USER, awaitItem())
            runCurrent()
        }
        assertEquals(0, channel.connectCount)
    }

    @Test
    fun givenNoScreenObserves_whenAppStartsInTheBackground_thenNothingConnects() = runTest {
        val userDataRepository = TestUserDataRepository()
        val channel = FakePrivilegedChannel(DENIED)
        createMonitor(userDataRepository, FakeShizukuInitializer(), backgroundScope, channel = channel)
        userDataRepository.setControllerType(PM)

        runCurrent()

        assertEquals(0, channel.connectCount)
    }

    private fun createMonitor(
        userDataRepository: TestUserDataRepository,
        shizukuInitializer: IShizukuInitializer,
        appScope: CoroutineScope,
        rootApiController: IController = FakeController(rootGranted = true),
        channel: FakePrivilegedChannel = FakePrivilegedChannel(),
    ) = AppPermissionMonitor(
        userDataRepository = userDataRepository,
        shizukuInitializer = shizukuInitializer,
        rootApiController = rootApiController,
        rootApiAppController = FakeAppController(rootGranted = true),
        rootApiServiceController = FakeServiceController(),
        channel = channel,
        appScope = appScope,
    )

    /** A root controller su refuses, as when the user denies the prompt. */
    private class RefusingController : IController by FakeController() {
        override suspend fun init() = throw RootUnavailableException()
    }

    /** Never finishes registering, like a Shizuku permission dialog nobody answers. */
    private class PendingShizukuInitializer : IShizukuInitializer {
        var registerCallCount = 0
            private set

        override suspend fun registerShizuku(): RegisterShizukuResult {
            registerCallCount++
            awaitCancellation()
        }

        override suspend fun registerShizukuIfGranted(): RegisterShizukuResult = error("Not used by the monitor")

        override suspend fun awaitBinder() = awaitCancellation()

        override fun hasPermission(): Boolean = false

        override fun getUid(): Int = -1
    }

    private companion object {
        const val SYSTEM_UID = 1000
        val DENIED = ChannelState.Unavailable(UnavailableReason.DENIED)
    }
}
