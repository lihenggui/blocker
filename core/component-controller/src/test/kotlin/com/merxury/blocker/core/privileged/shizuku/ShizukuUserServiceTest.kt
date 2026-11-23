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

package com.merxury.blocker.core.privileged.shizuku

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.EchoInPrivilegedProcess
import com.merxury.blocker.core.privileged.FailInPrivilegedProcess
import com.merxury.blocker.core.privileged.FakeSystemServices
import com.merxury.blocker.core.privileged.MeasureInPrivilegedProcess
import com.merxury.blocker.core.privileged.PrivilegedProcessUnavailableException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

private val CALL = EchoInPrivilegedProcess("hello")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShizukuUserServiceTest {

    private val system = FakeSystemServices()
    private val connector = FakeUserServiceConnector(system)
    private val userService = ShizukuUserService(connector, attachTimeout = 15.seconds)

    @Test
    fun givenNotBound_whenRun_thenBindsOnceAndRunsInService() = runTest {
        assertThat(userService.run(CALL)).isEqualTo(CallResult.Success("hello for ${system.callerPackage}"))
        assertThat(userService.run(CALL)).isEqualTo(CallResult.Success("hello for ${system.callerPackage}"))

        assertThat(connector.binds).isEqualTo(1)
        assertThat(connector.services.single().calls).isEqualTo(2)
    }

    @Test
    fun givenCallLargerThanOneTransaction_whenRun_thenRunsInService() = runTest {
        val call = MeasureInPrivilegedProcess("c".repeat(600_000))

        assertThat(userService.run(call)).isEqualTo(CallResult.Success(600_000))
    }

    @Test
    fun givenCallFailsInService_whenRun_thenFailureArrives() = runTest {
        assertThat(userService.run(FailInPrivilegedProcess("denied")))
            .isEqualTo(CallResult.Failure(CallFailure.NotPermitted("denied")))
    }

    @Test
    fun givenShizukuBefore13_whenRun_thenNotPermittedWithoutBind() = runTest {
        connector.version = 12

        val result = userService.run(CALL)

        assertThat((result as CallResult.Failure).reason).isInstanceOf(CallFailure.NotPermitted::class.java)
        assertThat(connector.binds).isEqualTo(0)
    }

    @Test
    fun givenServiceNeverAttaches_whenRun_thenUnavailableAfterTimeoutAndServiceStopped() = runTest {
        connector.attaches = false

        val run = async { runCatching { userService.run(CALL) } }
        advanceTimeBy(14.seconds)
        assertThat(run.isCompleted).isFalse()
        advanceTimeBy(2.seconds)

        assertThat(run.await().exceptionOrNull()).isInstanceOf(PrivilegedProcessUnavailableException::class.java)
        assertThat(connector.unbinds).containsExactly(true)
    }

    @Test
    fun givenAttachTimedOut_whenRunAgain_thenBindsAgain() = runTest {
        connector.attaches = false
        val first = async { runCatching { userService.run(CALL) } }
        advanceTimeBy(16.seconds)
        first.await()
        connector.attaches = true

        assertThat(userService.run(CALL)).isInstanceOf(CallResult.Success::class.java)
        assertThat(connector.binds).isEqualTo(2)
    }

    @Test
    fun givenServiceDiesDuringCall_whenRun_thenUnavailableAndNextRunRebinds() = runTest {
        userService.run(CALL)
        connector.services.single().dead = true

        val died = runCatching { userService.run(CALL) }.exceptionOrNull()
        val result = userService.run(CALL)

        assertThat(died).isInstanceOf(PrivilegedProcessUnavailableException::class.java)

        assertThat(result).isInstanceOf(CallResult.Success::class.java)
        assertThat(connector.binds).isEqualTo(2)
        assertThat(connector.services.last().calls).isEqualTo(1)
    }

    @Test
    fun givenServiceDisconnected_whenRun_thenRebinds() = runTest {
        userService.run(CALL)

        connector.connections.single().onServiceDisconnected(null)
        userService.run(CALL)

        assertThat(connector.binds).isEqualTo(2)
    }

    @Test
    fun givenShizukuStopped_whenReset_thenNextRunRebinds() = runTest {
        userService.run(CALL)

        userService.reset()
        userService.run(CALL)

        assertThat(connector.binds).isEqualTo(2)
    }

    @Test
    fun givenCallerIsAnotherApp_whenRun_thenRefusedBeforeTheCallRuns() = runTest {
        connector.callerUid = BLOCKER_UID + 1

        val thrown = runCatching { userService.run(CALL) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(SecurityException::class.java)
    }
}
