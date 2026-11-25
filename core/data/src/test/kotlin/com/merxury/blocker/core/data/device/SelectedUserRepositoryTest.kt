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
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.repository.device.FakeDeviceUserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectedUserRepositoryTest {
    private val deviceUsers = FakeDeviceUserRepository()
    private val channel = FakePrivilegedChannel(ChannelState.Ready(PrivilegedIdentity.SHELL))

    private fun TestScope.repository() = InMemorySelectedUserRepository(
        ownUser = OWNER,
        deviceUsers = deviceUsers,
        channel = channel,
        scope = backgroundScope,
    )

    @Test
    fun givenNothingCached_whenCreated_thenOnlyOwnUserListedAndSelected() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()

        assertThat(repository.users.value.map { it.id }).containsExactly(OWNER)
        assertThat(repository.users.value.single().kind).isEqualTo(UserKind.OWNER)
        assertThat(repository.selectedUser.value).isEqualTo(OWNER)
    }

    @Test
    fun givenChannelReady_whenUsersCached_thenAllListed() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()

        deviceUsers.sendUsers(listOf(deviceUser(WORK), deviceUser(OWNER)))

        assertThat(repository.users.value.map { it.id }).containsExactly(OWNER, WORK).inOrder()
    }

    @Test
    fun givenProfileCached_whenChannelNotReady_thenOnlyOwnUserListed() = runTest(UnconfinedTestDispatcher()) {
        channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_RUNNING))
        val repository = repository()

        deviceUsers.sendUsers(listOf(deviceUser(OWNER), deviceUser(WORK)))

        assertThat(repository.users.value.map { it.id }).containsExactly(OWNER)
    }

    @Test
    fun givenChannelNotCheckedYet_whenProfileCached_thenListedUntilTheCheckFails() = runTest(UnconfinedTestDispatcher()) {
        channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
        val repository = repository()
        deviceUsers.sendUsers(listOf(deviceUser(OWNER), deviceUser(WORK)))

        assertThat(repository.users.value.map { it.id }).containsExactly(OWNER, WORK).inOrder()

        channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))

        assertThat(repository.users.value.map { it.id }).containsExactly(OWNER)
    }

    @Test
    fun givenReachableUser_whenSelected_thenSelected() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        deviceUsers.sendUsers(listOf(deviceUser(OWNER), deviceUser(WORK)))

        repository.select(WORK)

        assertThat(repository.selectedUser.value).isEqualTo(WORK)
    }

    @Test
    fun givenUnknownUser_whenSelected_thenIgnored() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()

        repository.select(UserId(42))

        assertThat(repository.selectedUser.value).isEqualTo(OWNER)
    }

    @Test
    fun givenWorkSelected_whenChannelDropsAndReturns_thenWorkIsRestored() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        deviceUsers.sendUsers(listOf(deviceUser(OWNER), deviceUser(WORK)))
        repository.selectedUser.test {
            assertThat(awaitItem()).isEqualTo(OWNER)
            repository.select(WORK)
            assertThat(awaitItem()).isEqualTo(WORK)

            channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_RUNNING))
            assertThat(awaitItem()).isEqualTo(OWNER)

            channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))
            assertThat(awaitItem()).isEqualTo(WORK)
        }
    }

    @Test
    fun givenWorkTemporarilyUnavailable_whenOwnerIsChosen_thenWorkDoesNotReclaimSelection() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        deviceUsers.sendUsers(listOf(deviceUser(OWNER), deviceUser(WORK)))
        repository.select(WORK)
        channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_RUNNING))

        repository.select(OWNER)
        channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))

        assertThat(repository.selectedUser.value).isEqualTo(OWNER)
    }

    @Test
    fun givenWorkSelected_whenProfileRemovedAndIdReused_thenOwnUserStaysSelected() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        deviceUsers.sendUsers(listOf(deviceUser(OWNER), deviceUser(WORK)))
        repository.select(WORK)

        deviceUsers.sendUsers(listOf(deviceUser(OWNER)))

        assertThat(repository.selectedUser.value).isEqualTo(OWNER)

        deviceUsers.sendUsers(listOf(deviceUser(OWNER), deviceUser(WORK).copy(name = "New work profile")))

        assertThat(repository.users.value.map { it.id }).containsExactly(OWNER, WORK)
        assertThat(repository.selectedUser.value).isEqualTo(OWNER)
    }
}
