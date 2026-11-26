// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.wake.WakeRunRequester
import com.merxury.blocker.core.model.wake.WakeAvailability
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RequestWakeCheckUseCase @Inject constructor(
    private val observeAvailability: ObserveWakeAvailabilityUseCase,
    private val runs: WakeRunRequester,
) {
    /** Returns whether a wake collection was requested. */
    suspend operator fun invoke(): Boolean = when (observeAvailability().first()) {
        WakeAvailability.Collecting, is WakeAvailability.Ready -> {
            runs.requestRun()
            true
        }

        WakeAvailability.NoAccess, WakeAvailability.NotOwnerProfile, WakeAvailability.ReadsRefused -> false
    }
}
