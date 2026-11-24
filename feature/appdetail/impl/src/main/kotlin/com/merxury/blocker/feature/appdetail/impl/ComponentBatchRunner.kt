/*
 * Copyright 2026 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.feature.appdetail.impl

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs batches of component changes to their end in the application's scope, whatever screen
 * started them: leaving the detail screen does not drop the requests of a batch still to be sent,
 * and the cache shows the system's state whenever the screen is shown again. One batch runs per
 * app at a time.
 *
 * A [BatchRun] holds nothing of the screen that started it. That screen follows [running] and
 * awaits [BatchRun.outcomes] in its own scope, so once it is gone nothing is told to it.
 */
@Singleton
class ComponentBatchRunner @Inject constructor(
    private val componentStatuses: ComponentStatusRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val runs = MutableStateFlow<Map<AppKey, BatchRun>>(emptyMap())

    /** The batch running for [app], or null. */
    fun running(app: AppKey): Flow<BatchRun?> = runs.map { it[app] }.distinctUntilChanged()

    /** The batch running for [app] now, or null. */
    fun current(app: AppKey): BatchRun? = runs.value[app]

    /**
     * Starts changing [components] of [app] towards [goal] through [layers], in requests of at
     * most [BATCH_LIMIT] components: up to that many go in one request, which is fastest, with one
     * read, one PackageManager call and one firewall write for the app.
     *
     * @return the batch, or null when a batch of [app] is running already, which is then left alone
     */
    fun start(app: AppKey, components: List<ComponentDeclaration>, goal: ControlGoal, layers: Set<ControlLayer>): BatchRun? {
        val run = synchronized(runs) {
            if (app in runs.value) return null
            val progress = MutableStateFlow(BatchStep(done = 0, pending = components.mapTo(mutableSetOf()) { it.id }))
            // Started once it is listed, so its end always removes it.
            val outcomes = scope.async(start = CoroutineStart.LAZY) { send(app, components, goal, layers, progress) }
            BatchRun(goal = goal, total = components.size, step = progress.asStateFlow(), outcomes = outcomes)
                .also { run -> runs.update { it + (app to run) } }
        }
        run.outcomes.start()
        return run
    }

    private suspend fun send(
        app: AppKey,
        components: List<ComponentDeclaration>,
        goal: ControlGoal,
        layers: Set<ControlLayer>,
        progress: MutableStateFlow<BatchStep>,
    ): List<ComponentOutcome> = try {
        val outcomes = mutableListOf<ComponentOutcome>()
        components.chunked(BATCH_LIMIT).forEach { step ->
            componentStatuses.control(ControlRequest(app.userId, step, goal, layers)).collect { event ->
                if (event is ControlEvent.PackageDone) outcomes += event.outcomes
            }
            val sent = step.mapTo(mutableSetOf()) { it.id }
            progress.update { BatchStep(done = it.done + step.size, pending = it.pending - sent) }
        }
        outcomes
    } finally {
        synchronized(runs) { runs.update { it - app } }
    }

    internal companion object {
        /**
         * Components per request. Each request reads the app, changes it and reads it back, so
         * fewer requests are faster; above this a batch shows its progress between requests.
         */
        const val BATCH_LIMIT = 200
    }
}

/**
 * A batch of [total] components changing towards [goal].
 *
 * @property step how far it got
 * @property outcomes every outcome, once the last request ended
 */
class BatchRun(
    val goal: ControlGoal,
    val total: Int,
    val step: StateFlow<BatchStep>,
    val outcomes: Deferred<List<ComponentOutcome>>,
)

/**
 * @property done components whose request ended
 * @property pending components whose request has not ended
 */
data class BatchStep(
    val done: Int,
    val pending: Set<ComponentId>,
)
