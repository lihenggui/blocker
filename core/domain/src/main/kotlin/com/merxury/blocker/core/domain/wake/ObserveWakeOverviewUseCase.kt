// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.domain.wake

import com.merxury.blocker.core.data.wake.WakeRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.wake.AppWakeSummary
import com.merxury.blocker.core.model.wake.WakeVerdict
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveWakeOverviewUseCase @Inject internal constructor(
    private val repository: WakeRepository,
    private val states: WakeStateObserver,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(days: Int = 7): Flow<List<AppWakeSummary>> = observeWakeWindow(days) { fromDay, throughDay ->
        combine(repository.findingsSince(fromDay), repository.verdictsSince(fromDay)) { findings, verdicts ->
            val summaries = buildWakeOverview(fromDay, throughDay, findings, verdicts)
            val byTarget = findings.groupBy { it.target }
            val byActor = findings.filter { it.actor != it.target }.groupBy { it.actor }
            val byVerdictTarget = verdicts.groupBy { it.target }
            val activities = summaries.filter { it.verdict != WakeVerdict.NORMAL }.map { (packageName) ->
                buildAppWakeActivity(
                    packageName,
                    fromDay,
                    throughDay,
                    byTarget[packageName].orEmpty() + byActor[packageName].orEmpty(),
                    byVerdictTarget[packageName].orEmpty(),
                )
            }
            summaries to activities
        }.flatMapLatest { (summaries, activities) ->
            states.observe(activities).map { apps ->
                val blocked = apps.filter { it.allBlocked }.mapTo(mutableSetOf()) { it.activity.packageName }
                summaries.map { summary -> summary.copy(allBlocked = summary.packageName in blocked) }
            }
        }
    }.flowOn(ioDispatcher)
}
