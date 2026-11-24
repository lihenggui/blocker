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

package com.merxury.blocker.core.data.device

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class DeviceUserRepositoryTest : DeviceRepositoryTestBase() {

    @Test
    fun givenObservedUsers_whenRefresh_thenSystemUsersEmitted() = runTest {
        users.users.test {
            assertThat(awaitItem()).isEmpty()

            val result = users.refresh()

            assertThat(result).isEqualTo(ReadResult.Success(listOf(deviceUser(OWNER), deviceUser(WORK))))
            assertThat(awaitItem()).containsExactly(deviceUser(OWNER), deviceUser(WORK)).inOrder()
        }
    }

    @Test
    fun givenUserRemovedFromTheSystem_whenRefresh_thenItsAppsAndRecordsAreRemoved() = runTest {
        system.install(installedApp(PKG, WORK))
        apps.refresh(WORK)
        pmBlocks.record(listOf(ComponentKey(declaration("Main").id, WORK)))
        system.users.removeAll { it.id == WORK }

        users.refresh()

        assertThat(users.users.first()).containsExactly(deviceUser(OWNER))
        assertThat(apps.apps(WORK).first()).isEmpty()
        assertThat(pmBlocks.of(WORK)).isEmpty()
    }

    @Test
    fun givenCachedWorkProfile_whenOnlyOwnUserIsVisible_thenKeepWorkAppsAndRecords() = runTest {
        val workApp = installedApp(PKG, WORK)
        system.install(workApp)
        apps.refresh(WORK)
        val record = ComponentKey(declaration("Main").id, WORK)
        pmBlocks.record(listOf(record))
        channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_RUNNING))
        system.users.removeAll { it.id == WORK }

        users.refresh()

        assertThat(apps.apps(WORK).first()).containsExactly(workApp)
        assertThat(pmBlocks.of(WORK)).containsExactly(record)
    }

    @Test
    fun givenCachedUsers_whenReadFails_thenCacheUntouchedAndFailureReturned() = runTest {
        users.refresh()
        system.usersFailure = ReadFailure.ChannelUnavailable

        val result = users.refresh()

        assertThat(result).isEqualTo(ReadResult.Failure(ReadFailure.ChannelUnavailable))
        assertThat(users.users.first()).hasSize(2)
    }
}
