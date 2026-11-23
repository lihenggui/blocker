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
import com.merxury.blocker.core.privileged.CallBytes
import com.merxury.blocker.core.privileged.FakeSystemServices
import com.merxury.blocker.core.privileged.MeasureInPrivilegedProcess
import com.merxury.blocker.core.privileged.awaitPipeWriters
import com.merxury.blocker.core.privileged.calls.ForceStop
import com.merxury.blocker.core.privileged.encodeOutcome
import com.merxury.blocker.core.privileged.unmarshallCall
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
    private val grants = FakeRootGrantMemory()
    private val transport = RootTransport(runner, grants, StandardTestDispatcher(scope.testScheduler))

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
    fun givenRootProcessRunsAsSystemUid_whenConnect_thenFailed() = scope.runTest {
        runner.uid = 1000

        assertThat(transport.connect()).isEqualTo(ChannelState.Unavailable(UnavailableReason.FAILED))
    }

    @Test
    fun givenSuRefuses_whenConnect_thenDenied() = scope.runTest {
        runner.failures += NoShellException("denied")

        assertThat(transport.connect()).isEqualTo(ChannelState.Unavailable(UnavailableReason.DENIED))
    }

    @Test
    fun givenRootGranted_whenConnect_thenRemembered() = scope.runTest {
        transport.connect()

        assertThat(grants.remembered).isTrue()
    }

    @Test
    fun givenRememberedAndSuRefuses_whenConnect_thenForgotten() = scope.runTest {
        grants.remembered = true
        runner.failures += NoShellException("denied")

        transport.connect()

        assertThat(grants.remembered).isFalse()
    }

    @Test
    fun givenRememberedAndRootProcessNotUidZero_whenConnect_thenStillRemembered() = scope.runTest {
        grants.remembered = true
        runner.uid = 2000

        transport.connect()

        assertThat(grants.remembered).isTrue()
    }

    @Test
    fun givenRemembered_whenConnectIfGranted_thenReadyRoot() = scope.runTest {
        grants.remembered = true

        assertThat(transport.connectIfGranted()).isEqualTo(ChannelState.Ready(PrivilegedIdentity.ROOT))
        assertThat(runner.sent).isEqualTo(1)
        assertThat(grants.remembered).isTrue()
    }

    @Test
    fun givenNotRemembered_whenConnectIfGranted_thenDeniedWithoutStartingSu() = scope.runTest {
        assertThat(transport.connectIfGranted()).isEqualTo(ChannelState.Unavailable(UnavailableReason.DENIED))
        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.DENIED))
        assertThat(runner.sent).isEqualTo(0)
    }

    @Test
    fun givenRememberedButSuNowRefuses_whenConnectIfGranted_thenDeniedAndForgotten() = scope.runTest {
        grants.remembered = true
        runner.failures += NoShellException("denied")

        assertThat(transport.connectIfGranted()).isEqualTo(ChannelState.Unavailable(UnavailableReason.DENIED))
        assertThat(grants.remembered).isFalse()

        assertThat(transport.connectIfGranted()).isEqualTo(ChannelState.Unavailable(UnavailableReason.DENIED))
        assertThat(runner.sent).isEqualTo(1)
    }

    @Test
    fun givenNotRemembered_whenExecuteIfReady_thenChannelUnavailableWithoutStartingSu() = scope.runTest {
        assertThat(transport.executeIfReady(CALL)).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
        assertThat(runner.sent).isEqualTo(0)
    }

    @Test
    fun givenRemembered_whenExecuteIfReadyInNewProcess_thenConnectsQuietlyAndRuns() = scope.runTest {
        grants.remembered = true

        assertThat(transport.executeIfReady(CALL)).isEqualTo(CallResult.Success(Unit))
        assertThat(runner.system.activityManager.forceStopped).hasSize(1)
        assertThat(runner.sent).isEqualTo(2)
    }

    @Test
    fun givenRootRevokedWhileReady_whenExecute_thenForgotten() = scope.runTest {
        transport.connect()
        runner.failures += NoShellException("denied")

        transport.execute(CALL)

        assertThat(grants.remembered).isFalse()
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
    fun givenCallLargerThanOneRootCall_whenExecute_thenSentThroughPipeAndRun() = scope.runTest {
        transport.connect()
        val call = MeasureInPrivilegedProcess("x".repeat(300_000))

        val result = transport.execute(call)

        assertThat(result).isEqualTo(CallResult.Success(300_000))
        assertThat(runner.requestSizes.last()).isGreaterThan(64 * 1024)
        assertThat(transport.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.ROOT))
    }

    @Test
    fun givenLargeCallAndRootProcessDiesOnce_whenExecute_thenSentWholeAgain() = scope.runTest {
        transport.connect()
        runner.failures += RootServer.UnexpectedExitException()
        val call = MeasureInPrivilegedProcess("y".repeat(300_000))

        assertThat(transport.execute(call)).isInstanceOf(CallResult.Success::class.java)
        assertThat(runner.sent).isEqualTo(3)
    }

    @Test
    fun givenCallFailsInRootProcess_whenExecute_thenNoRetry() = scope.runTest {
        transport.connect()
        runner.system.activityManager.failure = SecurityException("no")

        assertThat(transport.execute(CALL)).isEqualTo(CallResult.Failure(CallFailure.NotPermitted("no")))
        assertThat(runner.sent).isEqualTo(2)
    }
}

private class FakeRootGrantMemory : RootGrantMemory {
    var remembered = false

    override suspend fun isRemembered(): Boolean = remembered

    override suspend fun remember(granted: Boolean) {
        remembered = granted
    }
}

/**
 * Plays the root process: runs each command's call against [system] as [uid] and encodes the
 * outcome as the root process does, unless a queued failure is thrown first, before the request
 * is read, as librootkotlinx would.
 */
private class FakeRootRunner : RootCommandRunner {
    val system = FakeSystemServices()
    val failures = ArrayDeque<Exception>()
    var uid = 0
    var sent = 0
    val requestSizes = mutableListOf<Int>()

    override suspend fun run(command: RootCallCommand): CallBytes {
        sent++
        failures.removeFirstOrNull()?.let { throw it }
        ShadowProcess.setUid(uid)
        check(awaitPipeWriters())
        val bytes = command.request.read(CallBytes.MAX_REQUEST_BYTES)
        requestSizes += bytes.size
        return CallBytes.reply(encodeOutcome(unmarshallCall(bytes), system))
    }
}
