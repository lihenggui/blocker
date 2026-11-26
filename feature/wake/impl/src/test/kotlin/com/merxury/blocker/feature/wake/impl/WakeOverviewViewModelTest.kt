// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.wake.WakeAuthorizationRepository
import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.domain.wake.ObserveWakeAvailabilityUseCase
import com.merxury.blocker.core.domain.wake.ObserveWakeOverviewUseCase
import com.merxury.blocker.core.domain.wake.RequestWakeCheckUseCase
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.model.wake.AppWakeSummary
import com.merxury.blocker.core.model.wake.WakeHeadline
import com.merxury.blocker.core.model.wake.WakeVerdict
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.repository.wake.FakeAutomaticWakeBlockRepository
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.testing.util.TestWakeRunRequester
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class WakeOverviewViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val denied = ChannelState.Unavailable(UnavailableReason.DENIED)
    private val channel = FakePrivilegedChannel(denied)
    private val hasFindings = MutableStateFlow(false)
    private val repository = mock<WakeRepository> {
        on { isOwnerProfile } doReturn true
        on { statuses() } doReturn flowOf(emptyList())
        on { hasFindings() } doReturn this@WakeOverviewViewModelTest.hasFindings
    }
    private val overview = mock<ObserveWakeOverviewUseCase>()
    private val installedApps = FakeInstalledAppRepository()
    private val selectedUsers = FakeSelectedUserRepository()
    private val runs = TestWakeRunRequester()
    private val availability = ObserveWakeAvailabilityUseCase(repository, WakeAuthorizationRepository(channel))
    private val viewModel by lazy {
        WakeOverviewViewModel(
            availability,
            overview,
            FakeAutomaticWakeBlockRepository(),
            installedApps,
            selectedUsers,
            runs,
            RequestWakeCheckUseCase(availability, runs),
            channel,
        )
    }

    @After
    fun clearViewModel() {
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun givenDeniedAccess_whenGrantIsDenied_thenReportsOneFailure() = runTest {
        channel.connectResult = denied
        viewModel.accessGrantFailures.test {
            viewModel.grantAccess()

            assertThat(awaitItem()).isEqualTo(Unit)
            assertThat(channel.connectCount).isEqualTo(1)
            expectNoEvents()
        }
    }

    @Test
    fun givenDeniedAccess_whenGrantSucceeds_thenStartsObservingAndDoesNotReportFailure() = runTest {
        viewModel.accessGrantFailures.test {
            viewModel.uiState.test {
                assertThat(awaitItem()).isEqualTo(WakeOverviewUiState.Loading)
                assertThat(awaitItem()).isEqualTo(WakeOverviewUiState.NoAccess)

                viewModel.grantAccess()

                assertThat(awaitItem()).isEqualTo(WakeOverviewUiState.Collecting(false))
                assertThat(channel.connectCount).isEqualTo(1)
            }
            expectNoEvents()
        }
    }

    @Test
    fun givenCollecting_whenCheckIsRequested_thenRequestsRunAndFollowsRunningState() = runTest {
        channel.setState(channel.connectResult)
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(WakeOverviewUiState.Loading)
            assertThat(awaitItem()).isEqualTo(WakeOverviewUiState.Collecting(false))

            viewModel.checkNow()
            runCurrent()
            assertThat(runs.requests).isEqualTo(1)

            runs.isRunning.value = true
            assertThat(awaitItem()).isEqualTo(WakeOverviewUiState.Collecting(true))
            runs.isRunning.value = false
            assertThat(awaitItem()).isEqualTo(WakeOverviewUiState.Collecting(false))
        }
    }

    @Test
    fun givenAccessWasLost_whenCheckIsRequested_thenItDoesNotStartRunOrPrompt() = runTest {
        viewModel.checkNow()
        runCurrent()

        assertThat(runs.requests).isEqualTo(0)
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @Test
    fun givenAnotherUserIsSelected_whenReady_thenUsesTheOwnerInventoryAndLiveRunningFlag() = runTest {
        val secondary = UserId(12)
        selectedUsers.sendUsers(listOf(DeviceUser(secondary, "Secondary", UserKind.SECONDARY, null, true)))
        selectedUsers.select(secondary)
        installedApps.sendApps(listOf(wakeTestApp("owner"), wakeTestApp("secondary", secondary)))
        whenever(overview(7)).thenReturn(
            flowOf(listOf(AppWakeSummary("owner", WakeVerdict.KEEP_ALIVE, 1, 0, WakeHeadline.TimedWakeups(2)))),
        )
        hasFindings.value = true
        channel.setState(channel.connectResult)

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(WakeOverviewUiState.Loading)
            val ready = awaitItem() as WakeOverviewUiState.Ready
            assertThat(ready.keepAlive.map { it.packageName }).containsExactly("owner")
            assertThat(ready.otherApps).isEqualTo(0)

            runs.isRunning.value = true
            assertThat(awaitItem()).isEqualTo(ready.copy(checking = true))
        }
    }
}
