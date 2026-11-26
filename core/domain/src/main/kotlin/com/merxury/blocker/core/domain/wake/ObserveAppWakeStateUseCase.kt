// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.model.wake.AppWakeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class ObserveAppWakeStateUseCase @Inject internal constructor(
    private val activity: ObserveAppWakeActivityUseCase,
    private val states: WakeStateObserver,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(packageName: String, days: Int = 7): Flow<AppWakeState> = activity(packageName, days)
        .flatMapLatest(states::observeApp).combine(states.observeEnvironment(packageName)) { state, environment ->
            state.copy(environment = environment)
        }.distinctUntilChanged()
}
