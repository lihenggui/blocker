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

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.datastore.UserPreferences
import com.merxury.blocker.core.datastore.test.InMemoryDataStore
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.privileged.calls.ForceStop
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val CALL = ForceStop("com.example", UserId(0))

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActivePrivilegedChannelTest {

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val preferences = BlockerPreferencesDataSource(InMemoryDataStore(UserPreferences.getDefaultInstance()))
    private val root = FakeTransport()
    private val shizuku = FakeTransport()

    private fun TestScope.channel() = ActivePrivilegedChannel(
        preferences.userData.map { it.controllerType },
        root,
        shizuku,
        backgroundScope,
    )

    @Test
    fun givenQuietConnectRunning_whenCreated_thenNotCheckedUntilItEnds() = scope.runTest {
        val gate = CompletableDeferred<Unit>()
        root.quietAcquireGate = gate
        val channel = channel()
        assertThat(channel.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))

        gate.complete(Unit)

        assertThat(channel.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.DENIED))
    }

    @Test
    fun whenCreated_thenSelectedTransportConnectedQuietlyOnce() = scope.runTest {
        root.quietAcquireResult = ChannelState.Ready(PrivilegedIdentity.ROOT)

        val channel = channel()

        assertThat(channel.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.ROOT))
        assertThat(root.quietAcquireCount).isEqualTo(1)
        assertThat(root.acquireCount).isEqualTo(0)
        assertThat(shizuku.quietAcquireCount).isEqualTo(0)
    }

    @Test
    fun givenTransportChanges_whenChosen_thenEachChoiceConnectedQuietly() = scope.runTest {
        channel()

        preferences.setControllerType(ControllerType.SHIZUKU)
        runCurrent()
        assertThat(shizuku.quietAcquireCount).isEqualTo(1)

        preferences.setControllerType(ControllerType.IFW)
        runCurrent()
        assertThat(root.quietAcquireCount).isEqualTo(2)
        assertThat(root.acquireCount + shizuku.acquireCount).isEqualTo(0)
    }

    @Test
    fun givenRootBasedTypeChanges_whenChosen_thenNoNewQuietConnect() = scope.runTest {
        channel()

        preferences.setControllerType(ControllerType.IFW)
        preferences.setControllerType(ControllerType.IFW_PLUS_PM)
        runCurrent()

        assertThat(root.quietAcquireCount).isEqualTo(1)
    }

    @Test
    fun givenFirstQuietConnectRunning_whenExecuteIfReady_thenWaitsForItAndRuns() = scope.runTest {
        val gate = CompletableDeferred<Unit>()
        root.quietAcquireGate = gate
        root.quietAcquireResult = ChannelState.Ready(PrivilegedIdentity.ROOT)
        val channel = channel()

        val read = async { channel.executeIfReady(CALL) }
        runCurrent()
        assertThat(read.isActive).isTrue()
        gate.complete(Unit)

        assertThat(read.await()).isEqualTo(CallResult.Success(Unit))
        assertThat(root.quietAcquireCount).isEqualTo(1)
        assertThat(root.acquireCount).isEqualTo(0)
    }

    @Test
    fun givenNothingGranted_whenExecuteIfReady_thenChannelUnavailableWithoutConnecting() = scope.runTest {
        val channel = channel()

        assertThat(channel.executeIfReady(CALL)).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
        assertThat(root.acquireCount).isEqualTo(0)
        assertThat(root.sent).isEmpty()
    }

    @Test
    fun givenQuietConnectRunning_whenConnect_thenConnectsAfterIt() = scope.runTest {
        val gate = CompletableDeferred<Unit>()
        root.quietAcquireGate = gate
        val channel = channel()

        val connect = async { channel.connect() }
        runCurrent()
        assertThat(root.acquireCount).isEqualTo(0)
        gate.complete(Unit)

        assertThat(connect.await()).isEqualTo(ChannelState.Ready(PrivilegedIdentity.ROOT))
        assertThat(channel.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.ROOT))
    }

    @Test
    fun givenEachRootBasedType_whenConnect_thenRootTransportUsed() = scope.runTest {
        val channel = channel()
        listOf(ControllerType.PM, ControllerType.IFW, ControllerType.IFW_PLUS_PM).forEach { type ->
            preferences.setControllerType(type)
            channel.connect()
        }

        assertThat(root.acquireCount).isEqualTo(3)
        assertThat(shizuku.acquireCount).isEqualTo(0)
        assertThat(channel.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.ROOT))
    }

    @Test
    fun givenShizukuChosenButNotRunning_whenExecute_thenNoFallbackToRoot() = scope.runTest {
        preferences.setControllerType(ControllerType.SHIZUKU)
        root.connect()
        shizuku.acquireResult = ChannelState.Unavailable(UnavailableReason.NOT_RUNNING)
        val channel = channel()

        assertThat(channel.execute(CALL)).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
        assertThat(channel.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.NOT_RUNNING))
        assertThat(root.sent).isEmpty()
    }

    @Test
    fun givenSelectionChanges_whenObserveState_thenStateOfNewTransport() = scope.runTest {
        preferences.setControllerType(ControllerType.PM)
        root.connect()
        shizuku.acquireResult = ChannelState.Ready(PrivilegedIdentity.SHELL)
        val channel = channel()
        assertThat(channel.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.ROOT))

        preferences.setControllerType(ControllerType.SHIZUKU)
        runCurrent()
        assertThat(channel.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.DENIED))

        channel.connect()
        assertThat(channel.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.SHELL))
    }

    @Test
    fun givenReadyTransport_whenExecuteIfReady_thenRunsWithoutConnecting() = scope.runTest {
        preferences.setControllerType(ControllerType.SHIZUKU)
        val channel = channel()

        assertThat(channel.executeIfReady(CALL)).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
        assertThat(shizuku.acquireCount).isEqualTo(0)

        shizuku.connect()
        assertThat(channel.executeIfReady(CALL)).isEqualTo(CallResult.Success(Unit))
        assertThat(shizuku.sent).containsExactly(CALL)
    }
}
