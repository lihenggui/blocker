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
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.control.layersFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs batches of component changes to their end in the application's scope, whatever screen
 * started them: leaving the detail screen does not drop the requests of a batch still to be sent,
 * and the cache shows the system's state whenever the screen is shown again. One batch runs per
 * app at a time.
 *
 * A batch is [reserve]d at once with the components it will change, so it counts as running
 * before anything is read, then [start]ed. A [BatchRun] holds nothing of the screen that started
 * it. That screen follows [running] and awaits [BatchRun.outcomes] in its own scope, so once it is
 * gone nothing is told to it; [BatchRun.outcomes] always completes with an outcome per component.
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
     * Lists a batch of [components] of [app] towards [goal] as running, before anything is sent.
     *
     * @return the batch to [start] or [release], or null when a batch of [app] is running
     * already, which is then left alone
     */
    fun reserve(app: AppKey, goal: ControlGoal, components: Collection<ComponentId>): BatchRun? = synchronized(runs) {
        if (app in runs.value) return null
        val run = BatchRun(app, goal, MutableStateFlow(BatchStep(done = 0, total = components.size, pending = components.toSet())))
        runs.update { it + (app to run) }
        run
    }

    /** Ends [run] without sending anything, as when the screen went away before it started. */
    fun release(run: BatchRun) {
        if (run.result.complete(emptyList())) remove(run)
    }

    /**
     * Sends [components] of [run] through [layers], in requests of at most [BATCH_LIMIT]
     * components: up to that many go in one request, which is fastest, with one read, one
     * PackageManager call and one firewall write for the app. A request that fails in a way the
     * repository does not report keeps the outcomes gathered before it and fails the components
     * not yet done with [ControlFailure.SystemError].
     */
    fun start(run: BatchRun, components: List<ComponentDeclaration>, layers: Set<ControlLayer>) {
        run.progress.value = BatchStep(done = 0, total = components.size, pending = components.mapTo(mutableSetOf()) { it.id })
        scope.launch {
            val outcomes = mutableListOf<ComponentOutcome>()
            try {
                send(run, components, layers, outcomes)
            } finally {
                run.result.complete(outcomes)
                remove(run)
            }
        }
    }

    private suspend fun send(run: BatchRun, components: List<ComponentDeclaration>, layers: Set<ControlLayer>, outcomes: MutableList<ComponentOutcome>) {
        components.chunked(BATCH_LIMIT).forEachIndexed { index, step ->
            try {
                componentStatuses.control(ControlRequest(run.app.userId, step, run.goal, layers)).collect { event ->
                    if (event is ControlEvent.PackageDone) outcomes += event.outcomes
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Batch ${run.goal} of ${run.app} failed in request ${index + 1} of ${step.size} components")
                val done = outcomes.mapTo(mutableSetOf()) { it.key.id }
                outcomes += components.filterNot { it.id in done }.map { it.failedWith(run, layers, e) }
                return
            }
            val sent = step.mapTo(mutableSetOf()) { it.id }
            run.progress.update { it.copy(done = it.done + step.size, pending = it.pending - sent) }
        }
    }

    private fun ComponentDeclaration.failedWith(run: BatchRun, layers: Set<ControlLayer>, e: Exception) = ComponentOutcome(
        key = ComponentKey(id, run.app.userId),
        state = ControlState(PmSetting.UNKNOWN, IfwBlock.UNKNOWN),
        result = ControlResult.FAILED,
        failures = layersFor(layers).associateWith { ControlFailure.SystemError(e.toString()) },
    )

    private fun remove(run: BatchRun) = synchronized(runs) {
        runs.update { current -> if (current[run.app] === run) current - run.app else current }
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
 * A batch of [app]'s components changing towards [goal].
 *
 * @property step how far it got
 * @property outcomes every outcome, once the last request ended; none when it was released
 */
class BatchRun internal constructor(
    val app: AppKey,
    val goal: ControlGoal,
    internal val progress: MutableStateFlow<BatchStep>,
) {
    internal val result = CompletableDeferred<List<ComponentOutcome>>()
    val step: StateFlow<BatchStep> = progress.asStateFlow()
    val outcomes: Deferred<List<ComponentOutcome>> get() = result
}

/**
 * @property done components whose request ended
 * @property total components in the batch
 * @property pending components whose request has not ended
 */
data class BatchStep(
    val done: Int,
    val total: Int,
    val pending: Set<ComponentId>,
)
