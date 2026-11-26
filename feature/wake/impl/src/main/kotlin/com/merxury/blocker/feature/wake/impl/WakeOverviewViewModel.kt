// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.wake.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.wake.AutomaticWakeBlockRepository
import com.merxury.blocker.core.data.wake.WakeRunRequester
import com.merxury.blocker.core.domain.wake.ObserveWakeAvailabilityUseCase
import com.merxury.blocker.core.domain.wake.ObserveWakeOverviewUseCase
import com.merxury.blocker.core.domain.wake.RequestWakeCheckUseCase
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.wake.WakeAvailability
import com.merxury.blocker.core.privileged.PrivilegedChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class WakeOverviewViewModel @Inject constructor(
    observeAvailability: ObserveWakeAvailabilityUseCase,
    observeOverview: ObserveWakeOverviewUseCase,
    automaticBlocks: AutomaticWakeBlockRepository,
    installedApps: InstalledAppRepository,
    selectedUsers: SelectedUserRepository,
    runs: WakeRunRequester,
    private val requestCheck: RequestWakeCheckUseCase,
    private val channel: PrivilegedChannel,
) : ViewModel() {

    private val grantFailures = Channel<Unit>(Channel.CONFLATED)
    val accessGrantFailures = grantFailures.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<WakeOverviewUiState> = observeAvailability()
        .distinctUntilChanged { old, new -> old == new || (old is WakeAvailability.Ready && new is WakeAvailability.Ready) }
        .flatMapLatest { availability ->
            when (availability) {
                is WakeAvailability.Ready -> combine(
                    observeOverview(),
                    automaticBlocks.blocks,
                    installedApps.apps(selectedUsers.ownUser),
                    runs.isRunning,
                ) { summaries, blocks, apps, checking ->
                    wakeOverviewUiState(availability, checking, summaries, blocks, apps)
                }

                WakeAvailability.Collecting -> runs.isRunning.map { wakeOverviewUiState(availability, it) }

                else -> flowOf(wakeOverviewUiState(availability, checking = false))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WakeOverviewUiState.Loading)

    fun grantAccess() {
        viewModelScope.launch {
            if (channel.connect() !is ChannelState.Ready) grantFailures.send(Unit)
        }
    }

    fun checkNow() {
        viewModelScope.launch {
            // The request rechecks availability in case it changed after the control was tapped.
            requestCheck()
        }
    }
}
