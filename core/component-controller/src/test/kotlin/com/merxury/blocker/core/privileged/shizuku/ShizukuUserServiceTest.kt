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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val CALL = EchoInPrivilegedProcess("hello")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ShizukuUserServiceTest {

    private val system = FakeSystemServices()
    private val connector = FakeUserServiceConnector(system)
    private val scope = TestScope()
    private val userService = ShizukuUserService(connector, attachTimeout = 15.seconds, scope = scope.backgroundScope)

    @Test
    fun givenNotBound_whenRun_thenBindsOnceAndRunsInService() = scope.runTest {
        assertThat(userService.run(CALL)).isEqualTo(CallResult.Success("hello for ${system.callerPackage}"))
        assertThat(userService.run(CALL)).isEqualTo(CallResult.Success("hello for ${system.callerPackage}"))

        assertThat(connector.binds).isEqualTo(1)
        assertThat(connector.services.single().calls).isEqualTo(2)
    }

    @Test
    fun givenCallLargerThanOneTransaction_whenRun_thenRunsInService() = scope.runTest {
        val call = MeasureInPrivilegedProcess("c".repeat(600_000))

        assertThat(userService.run(call)).isEqualTo(CallResult.Success(600_000))
    }

    @Test
    fun givenCallFailsInService_whenRun_thenFailureArrives() = scope.runTest {
        assertThat(userService.run(FailInPrivilegedProcess("denied")))
            .isEqualTo(CallResult.Failure(CallFailure.NotPermitted("denied")))
    }

    @Test
    fun givenShizukuBefore13_whenRun_thenNotPermittedWithoutBind() = scope.runTest {
        connector.version = 12

        val result = userService.run(CALL)

        assertThat((result as CallResult.Failure).reason).isInstanceOf(CallFailure.NotPermitted::class.java)
        assertThat(connector.binds).isEqualTo(0)
    }

    @Test
    fun givenServiceNeverAttaches_whenRun_thenUnavailableAfterTimeoutAndServiceStopped() = scope.runTest {
        connector.attaches = false

        val run = async { runCatching { userService.run(CALL) } }
        advanceTimeBy(14.seconds)
        assertThat(run.isCompleted).isFalse()
        advanceTimeBy(2.seconds)

        assertThat(run.await().exceptionOrNull()).isInstanceOf(PrivilegedProcessUnavailableException::class.java)
        assertThat(connector.unbinds).containsExactly(true)
    }

    @Test
    fun givenAttachTimedOut_whenRunAgain_thenBindsAgain() = scope.runTest {
        connector.attaches = false
        val first = async { runCatching { userService.run(CALL) } }
        advanceTimeBy(16.seconds)
        first.await()
        connector.attaches = true

        assertThat(userService.run(CALL)).isInstanceOf(CallResult.Success::class.java)
        assertThat(connector.binds).isEqualTo(2)
    }

    @Test
    fun givenServiceDiesDuringCall_whenRun_thenUnavailableAndNextRunRebinds() = scope.runTest {
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
    fun givenServiceDisconnected_whenRun_thenRebinds() = scope.runTest {
        userService.run(CALL)

        connector.connections.single().onServiceDisconnected(null)
        userService.run(CALL)

        assertThat(connector.binds).isEqualTo(2)
    }

    @Test
    fun givenShizukuStopped_whenReset_thenNextRunRebinds() = scope.runTest {
        userService.run(CALL)

        userService.reset()
        userService.run(CALL)

        assertThat(connector.binds).isEqualTo(2)
    }

    @Test
    fun givenAttachIsPending_whenShizukuResets_thenNextCallBindsAgain() = scope.runTest {
        connector.attaches = false
        val first = async { runCatching { userService.run(CALL) } }
        runCurrent()
        assertThat(connector.binds).isEqualTo(1)

        userService.reset()
        connector.attaches = true

        assertThat(userService.run(CALL)).isInstanceOf(CallResult.Success::class.java)
        assertThat(first.await().exceptionOrNull()).isInstanceOf(PrivilegedProcessUnavailableException::class.java)
        assertThat(connector.unbinds).containsExactly(true)
        assertThat(connector.binds).isEqualTo(2)
    }

    @Test
    fun givenCallerIsAnotherApp_whenRun_thenRefusedBeforeTheCallRuns() = scope.runTest {
        connector.callerUid = BLOCKER_UID + 1

        val thrown = runCatching { userService.run(CALL) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(SecurityException::class.java)
    }

    @Test
    fun givenNoVisibleScreen_whenIdleForTheStopTime_thenServiceStoppedAndTheNextCallStartsIt() = scope.runTest {
        val idle = ShizukuUserService(connector, 15.seconds, backgroundScope, IdleStop(60.seconds) { false })
        idle.run(CALL)

        advanceTimeBy(59.seconds)
        assertThat(connector.unbinds).isEmpty()
        advanceTimeBy(2.seconds)
        assertThat(connector.unbinds).containsExactly(true)

        assertThat(idle.run(CALL)).isInstanceOf(CallResult.Success::class.java)
        assertThat(connector.binds).isEqualTo(2)
    }

    @Test
    fun givenLastWaiterTimesOut_whenServiceAttachesLater_thenIdleStopStartsAtAttachment() = scope.runTest {
        val idle = ShizukuUserService(connector, 15.seconds, backgroundScope, IdleStop(5.seconds) { false })
        connector.attaches = false

        val call = async { withTimeoutOrNull(5.seconds) { idle.run(CALL) } }
        advanceTimeBy(12.seconds)
        assertThat(call.await()).isNull()
        assertThat(connector.unbinds).isEmpty()
        connector.deliverBinder()

        advanceTimeBy(4.seconds)
        assertThat(connector.unbinds).isEmpty()
        advanceTimeBy(2.seconds)
        assertThat(connector.unbinds).containsExactly(true)
    }

    @Test
    fun givenCallsKeepComing_whenEachIsWithinTheStopTime_thenServiceKept() = scope.runTest {
        val idle = ShizukuUserService(connector, 15.seconds, backgroundScope, IdleStop(60.seconds) { false })
        repeat(4) {
            idle.run(CALL)
            advanceTimeBy(40.seconds)
        }

        assertThat(connector.unbinds).isEmpty()
        assertThat(connector.binds).isEqualTo(1)
    }

    @Test
    fun givenVisibleScreen_whenIdle_thenServiceKeptUntilTheScreenGoes() = scope.runTest {
        var visible = true
        val idle = ShizukuUserService(connector, 15.seconds, backgroundScope, IdleStop(60.seconds) { visible })
        idle.run(CALL)

        advanceTimeBy(10.minutes)
        assertThat(connector.unbinds).isEmpty()
        visible = false
        advanceTimeBy(61.seconds)

        assertThat(connector.unbinds).containsExactly(true)
    }
}
