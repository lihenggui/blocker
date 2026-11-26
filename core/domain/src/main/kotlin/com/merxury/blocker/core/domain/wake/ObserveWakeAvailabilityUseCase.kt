// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.wake.WakeAuthorizationRepository
import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.model.wake.WakeAvailability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveWakeAvailabilityUseCase @Inject constructor(
    private val repository: WakeRepository,
    private val authorization: WakeAuthorizationRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<WakeAvailability> {
        if (!repository.isOwnerProfile) return flowOf(WakeAvailability.NotOwnerProfile)

        // The live channel state takes precedence over the last recorded authorization attempt.
        return authorization.readyIdentity.flatMapLatest { identity ->
            if (identity == null) {
                flowOf(WakeAvailability.NoAccess)
            } else {
                repository.statuses().map { statuses ->
                    if (statuses.any { it.source == PROTECTION_SOURCE && it.status == READS_REFUSED_STATUS }) {
                        WakeAvailability.ReadsRefused
                    } else {
                        statuses.firstOrNull { it.source == DETECTION_SOURCE && it.status == DETECTION_OK_STATUS }
                            ?.let { WakeAvailability.Ready(it.checkedAt) }
                            ?: WakeAvailability.Collecting
                    }
                }
            }
        }.distinctUntilChanged()
    }
}
