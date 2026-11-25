// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.wake.AppWakeActivity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ObserveAppWakeActivityUseCase @Inject constructor(
    private val repository: WakeRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(packageName: String, days: Int = 7): Flow<AppWakeActivity> = observeWakeWindow(days) { fromDay, throughDay ->
        combine(repository.findingsInvolving(packageName, fromDay), repository.verdictsSince(fromDay)) { findings, verdicts ->
            buildAppWakeActivity(packageName, fromDay, throughDay, findings, verdicts)
        }
    }.flowOn(ioDispatcher)
}
