/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.feature.applist.impl

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.TopAppType
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Created after the rule sets the main dispatcher, which the view model's scope keeps.
    private lateinit var fixture: AppListFixture
    private val viewModel get() = fixture.viewModel

    @Before
    fun setUp() {
        fixture = AppListFixture(mainDispatcherRule.testDispatcher)
    }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    private fun TestScope.keepFresh() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.refreshOnAccessChanges() }
    }

    @Test
    fun givenNewViewModel_whenNothingCollects_thenNoWorkStarts() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a")))

        // Creating the view model alone reads and refreshes nothing.
        assertThat(viewModel.uiState.value.apps).isEqualTo(AppsUiState.Loading)
        assertThat(fixture.deviceUsers.refreshCount).isEqualTo(0)
        assertThat(fixture.installedApps.calls).isEmpty()
        assertThat(fixture.appControl.calls).isEmpty()
    }

    @Test
    fun givenSingleUser_whenCollected_thenNoSwitcherAndCachedAppsShown() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a", label = "A")))

        collectState()

        val state = viewModel.uiState.value
        assertThat(state.showProfileSwitcher).isFalse()
        assertThat(state.selectedUser).isEqualTo(state.ownUser)
        assertThat(fixture.rows().map { it.label }).containsExactly("A")
    }

    @Test
    fun givenTwoUsers_whenCollected_thenSwitcherShowsPersonalAndWork() = runTest {
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))

        collectState()

        val state = viewModel.uiState.value
        assertThat(state.showProfileSwitcher).isTrue()
        assertThat(state.profiles.map { it.label }).containsExactly(ProfileLabel.Personal, ProfileLabel.Work).inOrder()
        assertThat(state.selectedUser).isEqualTo(OWNER)
    }

    @Test
    fun givenOtherUsers_whenCollected_thenNamedOrNumberedAndStoppedMarked() = runTest {
        val named = DeviceUser(UserId(11), name = "Guest", kind = UserKind.SECONDARY, parent = null, running = false)
        val unnamed = DeviceUser(UserId(12), name = null, kind = UserKind.OTHER_PROFILE, parent = OWNER, running = true)
        fixture.selectedUsers.sendUsers(listOf(ownerUser, named, unnamed))

        collectState()

        assertThat(viewModel.uiState.value.profiles).containsExactly(
            ProfileTab(OWNER, ProfileLabel.Personal, running = true),
            ProfileTab(UserId(11), ProfileLabel.Named("Guest"), running = false),
            ProfileTab(UserId(12), ProfileLabel.Numbered(12), running = true),
        ).inOrder()
    }

    @Test
    fun givenTwoUsers_whenWorkSelected_thenWorkAppsShownAndRefreshedOnceWithPreferencesKept() = runTest {
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
        fixture.userData.sendUserData(defaultUserData.copy(showSystemApps = true, appSortingOrder = SortingOrder.DESCENDING))
        fixture.installedApps.sendApps(
            listOf(installedApp("com.a", label = "A"), installedApp("com.b", WORK, "B"), installedApp("com.c", WORK, "C")),
        )
        collectState()

        viewModel.selectUser(WORK)
        viewModel.selectUser(OWNER)
        viewModel.selectUser(WORK)

        val state = viewModel.uiState.value
        assertThat(state.selectedUser).isEqualTo(WORK)
        assertThat(state.showProfileSwitcher).isTrue()
        assertThat(fixture.rows().map { it.label }).containsExactly("C", "B").inOrder()
        assertThat(fixture.installedApps.calls.count { it == "refresh 10" }).isEqualTo(1)
    }

    @Test
    fun givenWorkReadBefore_whenListRefreshed_thenWorkReadAgainWhenNextSelected() = runTest {
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
        collectState()
        viewModel.selectUser(WORK)
        viewModel.selectUser(OWNER)

        viewModel.refresh()
        viewModel.selectUser(WORK)

        assertThat(fixture.installedApps.calls.count { it == "refresh 10" }).isEqualTo(2)
    }

    @Test
    fun givenWorkSelected_whenWorkProfileDisappears_thenOwnAppsShown() = runTest {
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
        fixture.installedApps.sendApps(listOf(installedApp("com.a", label = "A"), installedApp("com.b", WORK, "B")))
        collectState()
        viewModel.selectUser(WORK)

        fixture.selectedUsers.sendUsers(listOf(ownerUser))

        val state = viewModel.uiState.value
        assertThat(state.selectedUser).isEqualTo(OWNER)
        assertThat(state.showProfileSwitcher).isFalse()
        assertThat(fixture.rows().map { it.label }).containsExactly("A")
    }

    @Test
    fun givenChannelNotChecked_whenShown_thenWaitsForTheCheckBeforeRefreshing() = runTest {
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
        collectState()
        keepFresh()

        assertThat(fixture.deviceUsers.refreshCount).isEqualTo(0)

        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))

        assertThat(fixture.deviceUsers.refreshCount).isEqualTo(1)
        assertThat(fixture.installedApps.calls).containsExactly("refresh 0")
    }

    @Test
    fun givenChannelNotReady_whenRefreshed_thenRunningAppsAreNotAskedFor() = runTest {
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))
        fixture.installedApps.sendApps(listOf(installedApp("com.a")))
        collectState()

        viewModel.refresh()

        assertThat(fixture.appControl.calls).isEmpty()
        assertThat(fixture.rows().single().isRunning).isNull()
    }

    @Test
    fun givenShownOnce_whenShownAgainWithSameAccess_thenNotRefreshedAgain() = runTest {
        collectState()
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.refreshOnAccessChanges() }
        first.cancel()

        keepFresh()

        assertThat(fixture.deviceUsers.refreshCount).isEqualTo(1)
    }

    @Test
    fun givenShown_whenChannelLostAndRegained_thenRefreshedEachTime() = runTest {
        collectState()
        keepFresh()

        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_RUNNING))
        fixture.channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))

        assertThat(fixture.deviceUsers.refreshCount).isEqualTo(3)
    }

    @Test
    fun givenNoCache_whenRefreshFails_thenErrorState() = runTest {
        fixture.installedApps.refreshFailure = ReadFailure.ChannelUnavailable
        collectState()

        viewModel.refresh()

        assertThat(viewModel.uiState.value.apps).isEqualTo(AppsUiState.Error(ReadFailure.ChannelUnavailable))
    }

    @Test
    fun givenCachedApps_whenRefreshFails_thenCachedListKeptWithFailure() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a", label = "A")))
        fixture.installedApps.refreshFailure = ReadFailure.ChannelUnavailable
        collectState()

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertThat(fixture.rows().map { it.label }).containsExactly("A")
        assertThat(state.refreshFailure).isEqualTo(ReadFailure.ChannelUnavailable)
        assertThat(state.isRefreshing).isFalse()
    }

    @Test
    fun givenFailedRefresh_whenRefreshSucceeds_thenFailureCleared() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a")))
        fixture.installedApps.refreshFailure = ReadFailure.ChannelUnavailable
        collectState()
        viewModel.refresh()

        fixture.installedApps.refreshFailure = null
        viewModel.refresh()

        assertThat(viewModel.uiState.value.refreshFailure).isNull()
        assertThat(fixture.installedApps.calls).containsExactly("refresh 0", "refresh 0")
        assertThat(fixture.deviceUsers.refreshCount).isEqualTo(2)
    }

    @Test
    fun givenNoCache_whenRefreshFindsNothing_thenEmptyList() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem().apps).isEqualTo(AppsUiState.Loading)

            viewModel.refresh()

            assertThat(expectMostRecentItem().apps).isEqualTo(AppsUiState.Loaded(emptyList()))
        }
    }

    @Test
    fun givenRunningApps_whenRefreshed_thenRunningBadgeShown() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a"), installedApp("com.b")))
        fixture.appControl.running[OWNER] = mutableSetOf("com.a")
        collectState()

        viewModel.refresh()

        assertThat(fixture.rows().associate { it.key.packageName to it.isRunning })
            .containsExactly("com.a", true, "com.b", false)
    }

    @Test
    fun givenRunningNotPermitted_whenRefreshed_thenNoRunningBadge() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a")))
        fixture.appControl.running[OWNER] = mutableSetOf("com.a")
        fixture.appControl.failures["runningPackages"] = CallFailure.NotPermitted("Root sees running processes of user 0 only")
        collectState()

        viewModel.refresh()

        assertThat(fixture.rows().single().isRunning).isNull()
    }

    @Test
    fun givenSystemAppsHidden_whenCollected_thenOnlyUserAppsShown() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a", label = "A"), installedApp("android", label = "System", isSystem = true)))
        collectState()

        assertThat(fixture.rows().map { it.label }).containsExactly("A")

        fixture.userData.sendUserData(defaultUserData.copy(showSystemApps = true))

        assertThat(fixture.rows().map { it.label }).containsExactly("A", "System").inOrder()
    }

    @Test
    fun givenSortByInstallDateDescending_whenCollected_thenNewestFirst() = runTest {
        fixture.installedApps.sendApps(
            listOf(
                installedApp("com.old", label = "Old", firstInstallTime = 1),
                installedApp("com.new", label = "New", firstInstallTime = 3),
                installedApp("com.mid", label = "Mid", firstInstallTime = 2),
            ),
        )
        fixture.userData.sendUserData(
            defaultUserData.copy(appSorting = AppSorting.FIRST_INSTALL_TIME, appSortingOrder = SortingOrder.DESCENDING),
        )
        collectState()

        assertThat(fixture.rows().map { it.label }).containsExactly("New", "Mid", "Old").inOrder()
    }

    @Test
    fun givenNameSorting_whenCollected_thenSortedIgnoringCase() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.b", label = "beta"), installedApp("com.a", label = "Alpha")))
        collectState()

        assertThat(fixture.rows().map { it.label }).containsExactly("Alpha", "beta").inOrder()
    }

    @Test
    fun givenRunningOnTop_whenRefreshed_thenRunningAppsFirstInNameOrder() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a", label = "A"), installedApp("com.b", label = "B"), installedApp("com.c", label = "C")))
        fixture.appControl.running[OWNER] = mutableSetOf("com.c", "com.b")
        fixture.userData.sendUserData(defaultUserData.copy(topAppType = TopAppType.RUNNING))
        collectState()

        viewModel.refresh()

        assertThat(fixture.rows().map { it.label }).containsExactly("B", "C", "A").inOrder()
    }

    @Test
    fun givenDisabledOnTop_whenCollected_thenDisabledAppsFirst() = runTest {
        fixture.installedApps.sendApps(
            listOf(
                installedApp("com.a", label = "A"),
                installedApp("com.b", label = "B", enabledSetting = AppEnabledSetting.DISABLED_USER),
            ),
        )
        fixture.userData.sendUserData(defaultUserData.copy(topAppType = TopAppType.DISABLED))
        collectState()

        assertThat(fixture.rows().map { it.label to it.isDisabled }).containsExactly("B" to true, "A" to false).inOrder()
    }

    @Test
    fun givenBlockerInstalledForItsOwnUser_whenCollected_thenLeftOut() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp(SELF_PACKAGE, label = "Blocker"), installedApp("com.a", label = "A")))
        collectState()

        assertThat(fixture.rows().map { it.label }).containsExactly("A")
    }

    @Test
    fun givenBlockerInstalledInWorkProfile_whenWorkSelected_thenListed() = runTest {
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
        fixture.installedApps.sendApps(listOf(installedApp(SELF_PACKAGE, label = "Blocker"), installedApp(SELF_PACKAGE, WORK, "Blocker")))
        collectState()

        viewModel.selectUser(WORK)

        assertThat(fixture.rows().map { it.key }).containsExactly(AppKey(SELF_PACKAGE, WORK))
    }

    @Test
    fun givenManifestDisabledAppWithoutOverride_whenCollected_thenShownDisabled() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a", enabledSetting = AppEnabledSetting.DEFAULT, enabled = false)))
        collectState()

        assertThat(fixture.rows().single().isDisabled).isTrue()
    }

    @Test
    fun givenManifestDisabledAppWithEnabledOverride_whenCollected_thenShownEnabled() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a", enabledSetting = AppEnabledSetting.ENABLED, enabled = true)))
        collectState()

        assertThat(fixture.rows().single().isDisabled).isFalse()
    }

    @Test
    fun givenManifestDisabledApp_whenEnabled_thenShownEnabledFromTheFreshRead() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a", enabledSetting = AppEnabledSetting.DEFAULT, enabled = false)))
        collectState()

        viewModel.setEnabled(AppKey("com.a", OWNER), enabled = true)

        assertThat(fixture.rows().single().isDisabled).isFalse()
    }
}
