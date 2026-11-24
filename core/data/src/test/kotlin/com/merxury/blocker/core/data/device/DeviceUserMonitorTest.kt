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

import android.content.Intent
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.repository.device.FakeDeviceUserRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The cached users follow work profiles added or removed while Blocker runs. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeviceUserMonitorTest {
    private val context = RuntimeEnvironment.getApplication()
    private val deviceUsers = FakeDeviceUserRepository().apply { sendUsers(listOf(deviceUser(OWNER))) }
    private val channel = FakePrivilegedChannel(ChannelState.Ready(PrivilegedIdentity.ROOT))

    private fun TestScope.monitor() = DeviceUserMonitor(context, deviceUsers, channel, backgroundScope).apply { start() }

    private fun TestScope.selectedUsers() = InMemorySelectedUserRepository(ownUser = OWNER, deviceUsers = deviceUsers, channel = channel, scope = backgroundScope)

    private fun announce(action: String) {
        context.sendBroadcast(Intent(action))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun givenAWorkProfileIsAdded_whenTheSystemAnnouncesIt_thenEveryProfileSwitcherListsIt() = runTest(UnconfinedTestDispatcher()) {
        monitor()
        val switcher = selectedUsers()
        deviceUsers.systemUsers = listOf(deviceUser(OWNER), deviceUser(WORK))

        announce(Intent.ACTION_MANAGED_PROFILE_ADDED)

        assertThat(deviceUsers.refreshCount).isEqualTo(1)
        // The app list, the search and both rule screens list the users of this repository.
        assertThat(switcher.users.value.map { it.id }).containsExactly(OWNER, WORK).inOrder()
    }

    @Test
    fun givenAWorkProfileIsRemoved_whenTheSystemAnnouncesIt_thenItLeavesTheSwitcher() = runTest(UnconfinedTestDispatcher()) {
        deviceUsers.sendUsers(listOf(deviceUser(OWNER), deviceUser(WORK)))
        monitor()
        val switcher = selectedUsers()
        deviceUsers.systemUsers = listOf(deviceUser(OWNER))

        announce(Intent.ACTION_MANAGED_PROFILE_REMOVED)

        assertThat(switcher.users.value.map { it.id }).containsExactly(OWNER)
    }

    @Test
    fun givenTheAppComesToTheForeground_whenAProfileWasAddedMeanwhile_thenItIsListed() = runTest(UnconfinedTestDispatcher()) {
        val monitor = monitor()
        deviceUsers.systemUsers = listOf(deviceUser(OWNER), deviceUser(WORK))

        monitor.onForeground()

        assertThat(deviceUsers.refreshCount).isEqualTo(1)
        assertThat(selectedUsers().users.value.map { it.id }).containsExactly(OWNER, WORK).inOrder()
    }

    @Test
    fun givenNoAccess_whenAProfileChanges_thenTheCachedUsersAreKept() = runTest(UnconfinedTestDispatcher()) {
        channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_RUNNING))
        val monitor = monitor()

        announce(Intent.ACTION_MANAGED_PROFILE_ADDED)
        monitor.onForeground()

        // Without access only the user Blocker runs in can be read, which would drop the others.
        assertThat(deviceUsers.refreshCount).isEqualTo(0)
    }

    @Test
    fun givenStartedTwice_whenAProfileBecomesAvailable_thenTheUsersAreReadOnce() = runTest(UnconfinedTestDispatcher()) {
        monitor().start()

        announce(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)

        assertThat(deviceUsers.refreshCount).isEqualTo(1)
    }

    @Test
    fun givenAnotherBroadcast_whenReceived_thenNothingIsRead() = runTest(UnconfinedTestDispatcher()) {
        monitor()

        announce(Intent.ACTION_TIMEZONE_CHANGED)

        assertThat(deviceUsers.refreshCount).isEqualTo(0)
    }
}
