// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.model.wake.AppWakeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveAppWakeStateUseCase @Inject internal constructor(
    private val activity: ObserveAppWakeActivityUseCase,
    private val states: WakeStateObserver,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(packageName: String, days: Int = 7): Flow<AppWakeState> = activity(packageName, days).flatMapLatest { app ->
        states.observe(listOf(app)).map { it.single() }
    }
}
