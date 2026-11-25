// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.wake.AppWakeSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ObserveWakeOverviewUseCase @Inject constructor(
    private val repository: WakeRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(days: Int = 7): Flow<List<AppWakeSummary>> = observeWakeWindow(days) { fromDay, throughDay ->
        combine(repository.findingsSince(fromDay), repository.verdictsSince(fromDay)) { findings, verdicts ->
            buildWakeOverview(fromDay, throughDay, findings, verdicts)
        }
    }.flowOn(ioDispatcher)
}
