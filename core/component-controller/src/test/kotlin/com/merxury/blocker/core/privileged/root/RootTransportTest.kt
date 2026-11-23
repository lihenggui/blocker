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

package com.merxury.blocker.core.privileged.root

import android.os.RemoteException
import be.mygod.librootkotlinx.NoShellException
import be.mygod.librootkotlinx.RootServer
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.privileged.CallReply
import com.merxury.blocker.core.privileged.FakeSystemServices
import com.merxury.blocker.core.privileged.calls.ForceStop
import com.merxury.blocker.core.privileged.encodeOutcome
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess

private val CALL = ForceStop("com.example", UserId(10))

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RootTransportTest {

    private val scope = TestScope()
    private val runner = FakeRootRunner()
    private val transport = RootTransport(runner, StandardTestDispatcher(scope.testScheduler))

    @Test
    fun givenRootProcessRunsAsRoot_whenConnect_thenReadyRoot() = scope.runTest {
        assertThat(transport.connect()).isEqualTo(ChannelState.Ready(PrivilegedIdentity.ROOT))
    }

    @Test
    fun givenRootProcessNotUidZero_whenConnect_thenFailed() = scope.runTest {
        runner.uid = 2000

        assertThat(transport.connect()).isEqualTo(ChannelState.Unavailable(UnavailableReason.FAILED))
    }

    @Test
    fun givenSuRefuses_whenConnect_thenDenied() = scope.runTest {
        runner.failures += NoShellException("denied")

        assertThat(transport.connect()).isEqualTo(ChannelState.Unavailable(UnavailableReason.DENIED))
    }

    @Test
    fun givenRootProcessDiesOnce_whenExecute_thenSentAgainAndStillReady() = scope.runTest {
        transport.connect()
        runner.failures += RootServer.UnexpectedExitException()

        assertThat(transport.execute(CALL)).isEqualTo(CallResult.Success(Unit))
        assertThat(runner.system.activityManager.forceStopped).hasSize(1)
        assertThat(runner.sent).isEqualTo(3)
        assertThat(transport.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.ROOT))
    }

    @Test
    fun givenRootProcessDiesTwice_whenExecute_thenChannelUnavailableAndFailed() = scope.runTest {
        transport.connect()
        runner.failures += RootServer.UnexpectedExitException()
        runner.failures += RemoteException("still gone")

        assertThat(transport.execute(CALL)).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
        assertThat(runner.sent).isEqualTo(3)
        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.FAILED))
    }

    @Test
    fun givenRootRevoked_whenExecute_thenNoRetryAndDenied() = scope.runTest {
        transport.connect()
        runner.failures += NoShellException("denied")

        assertThat(transport.execute(CALL)).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
        assertThat(runner.sent).isEqualTo(2)
        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.DENIED))
    }

    @Test
    fun givenCallFailsInRootProcess_whenExecute_thenNoRetry() = scope.runTest {
        transport.connect()
        runner.system.activityManager.failure = SecurityException("no")

        assertThat(transport.execute(CALL)).isEqualTo(CallResult.Failure(CallFailure.NotPermitted("no")))
        assertThat(runner.sent).isEqualTo(2)
    }
}

/**
 * Plays the root process: runs each command against [system] as [uid] and encodes the outcome as
 * the root process does, unless a queued failure is thrown first, as librootkotlinx would.
 */
private class FakeRootRunner : RootCommandRunner {
    val system = FakeSystemServices()
    val failures = ArrayDeque<Exception>()
    var uid = 0
    var sent = 0

    override suspend fun run(command: RootCallCommand): CallReply {
        sent++
        failures.removeFirstOrNull()?.let { throw it }
        ShadowProcess.setUid(uid)
        return CallReply.of(encodeOutcome(command.call, system))
    }
}
