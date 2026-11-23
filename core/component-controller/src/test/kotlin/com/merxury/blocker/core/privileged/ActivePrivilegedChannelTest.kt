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
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun givenNothingConnected_whenCreated_thenNotChecked() = scope.runTest {
        assertThat(channel().state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
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
        assertThat(channel.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))

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
