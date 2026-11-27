// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.feature.history.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.device.DeviceUserRepository
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.history.OperationHistoryRepository
import com.merxury.blocker.core.data.repository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.util.TimeZoneMonitor
import com.merxury.blocker.core.model.history.Operation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.toJavaInstant

@HiltViewModel
internal class HistoryViewModel @Inject constructor(
    history: OperationHistoryRepository,
    installedApps: InstalledAppRepository,
    deviceUsers: DeviceUserRepository,
    selectedUser: SelectedUserRepository,
    generalRules: GeneralRuleRepository,
    timeZoneMonitor: TimeZoneMonitor,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        history.observeHistory().withRuleLabels(generalRules),
        observeDeviceLabels(deviceUsers, installedApps, selectedUser.ownUser),
        timeZoneMonitor.currentTimeZone,
    ) { (result, rules), device, timeZone ->
        val zone = ZoneId.of(timeZone.id)
        HistoryUiState(
            days = result.getOrNull()
                ?.sortedWith(compareByDescending<Operation> { it.timestamp }.thenByDescending { it.id })
                ?.historyRows(device, rules)
                ?.groupBy { it.operation.timestamp.toJavaInstant().atZone(zone).toLocalDate() }
                ?.map { (day, rows) -> HistoryDay(day, rows) },
            zone = zone,
            loadFailed = result.isFailure,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
