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

package com.merxury.blocker.core.privileged

import android.content.pm.PackageManager.NameNotFoundException
import android.os.DeadObjectException
import android.os.RemoteException
import be.mygod.librootkotlinx.NoShellException
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.privileged.calls.ForceStop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val CALL = ForceStop("com.example", UserId(10))
private val READY_ROOT = ChannelState.Ready(PrivilegedIdentity.ROOT)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AbstractPrivilegedTransportTest {

    private val transport = FakeTransport()

    @Test
    fun whenCreated_thenNotChecked() {
        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
    }

    @Test
    fun givenGranted_whenConnect_thenReadyWithIdentity() = runTest {
        transport.acquireResult = ChannelState.Ready(PrivilegedIdentity.SHELL)

        assertThat(transport.connect()).isEqualTo(ChannelState.Ready(PrivilegedIdentity.SHELL))
        assertThat(transport.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.SHELL))
    }

    @Test
    fun givenDenied_whenConnect_thenUnavailableDenied() = runTest {
        transport.acquireResult = ChannelState.Unavailable(UnavailableReason.DENIED)

        assertThat(transport.connect()).isEqualTo(ChannelState.Unavailable(UnavailableReason.DENIED))
        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.DENIED))
    }

    @Test
    fun givenNotConnected_whenExecute_thenConnectsOnceAndRuns() = runTest {
        assertThat(transport.execute(CALL)).isEqualTo(CallResult.Success(Unit))
        assertThat(transport.execute(CALL)).isEqualTo(CallResult.Success(Unit))

        assertThat(transport.acquireCount).isEqualTo(1)
        assertThat(transport.system.activityManager.forceStopped).hasSize(2)
    }

    @Test
    fun givenConnectDenied_whenExecute_thenChannelUnavailableWithoutRunning() = runTest {
        transport.acquireResult = ChannelState.Unavailable(UnavailableReason.DENIED)

        assertThat(transport.execute(CALL)).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
        assertThat(transport.acquireCount).isEqualTo(1)
        assertThat(transport.sent).isEmpty()
    }

    @Test
    fun givenNotConnected_whenExecuteIfReady_thenNeverConnects() = runTest {
        assertThat(transport.executeIfReady(CALL)).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))

        assertThat(transport.acquireCount).isEqualTo(0)
        assertThat(transport.sent).isEmpty()
    }

    @Test
    fun givenCallThrows_whenExecute_thenEachKindMapped() = runTest {
        transport.connect()
        val cases = mapOf(
            SecurityException("Shell cannot change component state") to
                CallFailure.NotPermitted("Shell cannot change component state"),
            NameNotFoundException("com.example") to CallFailure.TargetNotFound("com.example"),
            IllegalArgumentException("Unknown component") to CallFailure.TargetNotFound("Unknown component"),
            IllegalStateException("odd") to CallFailure.SystemError("java.lang.IllegalStateException: odd"),
            RuntimeException("Transaction failed") to CallFailure.SystemError("java.lang.RuntimeException: Transaction failed"),
        )

        cases.forEach { (thrown, expected) ->
            transport.throwOnSend = thrown
            assertThat(transport.execute(CALL)).isEqualTo(CallResult.Failure(expected))
        }
        assertThat(transport.state.value).isEqualTo(READY_ROOT)
    }

    @Test
    fun givenChannelDiesMidCall_whenExecute_thenChannelUnavailableAndStateUpdated() = runTest {
        transport.connect()
        transport.throwOnSend = ChannelLostException(UnavailableReason.NOT_RUNNING, "Shizuku stopped")

        assertThat(transport.execute(CALL)).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.NOT_RUNNING))
    }

    @Test
    fun givenDeadBinderWrapped_whenExecute_thenChannelUnavailableAndFailed() = runTest {
        transport.connect()
        // Shizuku rethrows a dead binder wrapped in a RuntimeException.
        transport.throwOnSend = RuntimeException(DeadObjectException())

        assertThat(transport.execute(CALL)).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.FAILED))
    }

    @Test
    fun givenPrivilegedProcessGone_whenExecute_thenChannelUnavailableAndStillReady() = runTest {
        transport.connect()
        transport.throwOnSend = PrivilegedProcessUnavailableException("died", DeadObjectException())

        assertThat(transport.execute(CALL)).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
        assertThat(transport.state.value).isEqualTo(READY_ROOT)
    }

    @Test
    fun givenRootRevoked_whenExecute_thenChannelUnavailableAndDenied() = runTest {
        transport.connect()
        transport.throwOnSend = RemoteException("closed").apply { initCause(NoShellException("denied")) }

        assertThat(transport.execute(CALL)).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.DENIED))
    }

    @Test
    fun givenChannelDied_whenExecuteAgain_thenReconnectsOnce() = runTest {
        transport.connect()
        transport.throwOnSend = ChannelLostException(UnavailableReason.FAILED, "gone")
        transport.execute(CALL)
        transport.throwOnSend = null

        assertThat(transport.execute(CALL)).isEqualTo(CallResult.Success(Unit))
        assertThat(transport.acquireCount).isEqualTo(2)
        assertThat(transport.state.value).isEqualTo(READY_ROOT)
    }

    @Test
    fun givenCallCancelled_whenExecute_thenCancellationPropagates() = runTest {
        transport.connect()
        transport.throwOnSend = CancellationException("caller left")

        val thrown = runCatching { transport.execute(CALL) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(CancellationException::class.java)
        assertThat(transport.state.value).isEqualTo(READY_ROOT)
    }
}
