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

package com.merxury.blocker.core.domain.control

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.PackageControlActivity
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whose batch it is. One batch runs per key at a time, so the batches of different screens never
 * stand in each other's way; the repository changes one package at a time, whatever key asked.
 */
sealed interface BatchKey {
    /** The user whose components the batch changes. */
    val user: UserId

    /** Components of one app, as its detail screen changes them. */
    data class App(val app: AppKey) : BatchKey {
        override val user: UserId get() = app.userId
    }

    /** Components of any apps of [user], as the search changes them. */
    data class Search(override val user: UserId) : BatchKey

    /** Components the general rules match in apps of [user], as the rule list changes them. */
    data class GeneralRules(override val user: UserId) : BatchKey

    /** Components the general rule [ruleId] matches in apps of [user], as its detail screen changes them. */
    data class Rule(val ruleId: Int, override val user: UserId) : BatchKey

    /** The debloater actions for one app. */
    data class Debloater(val app: AppKey) : BatchKey {
        override val user: UserId get() = app.userId
    }
}

/**
 * Runs batches of component changes to their end in the application's scope, whatever screen
 * started them: leaving that screen does not drop the requests of a batch still to be sent, and
 * the cache shows the system's state whenever the screen is shown again. One batch runs per
 * [BatchKey] at a time.
 *
 * A batch is [reserve]d at once with the components it will change, so it counts as running
 * before anything is read, then [start]ed. A [BatchRun] holds nothing of the screen that started
 * it. That screen follows [running] and awaits [BatchRun.outcomes] in its own scope, so once it is
 * gone nothing is told to it. [BatchRun.outcomes] completes with the outcomes of the components
 * sent, or fails if the batch could not be prepared.
 */
@Singleton
class ComponentBatchRunner @Inject constructor(
    private val componentStatuses: ComponentStatusRepository,
    @ApplicationScope private val scope: CoroutineScope,
    private val controlActivity: PackageControlActivity,
) {
    private val runs = MutableStateFlow<Map<BatchKey, BatchRun>>(emptyMap())

    /** The batch running for [key], or null. */
    fun running(key: BatchKey): Flow<BatchRun?> = runs.map { it[key] }.distinctUntilChanged()

    /** The batch running for [key] now, or null. */
    fun current(key: BatchKey): BatchRun? = runs.value[key]

    /**
     * The components whose batch request has not ended, of every batch whatever its key, so a
     * screen shows a component in flight however it was asked to change.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val inFlight: Flow<Set<ComponentKey>> = runs
        .flatMapLatest { current ->
            if (current.isEmpty()) {
                flowOf(emptySet())
            } else {
                combine(current.values.map { run -> run.step.map { step -> step.pending.mapTo(mutableSetOf()) { ComponentKey(it, run.key.user) } } }) { pending ->
                    pending.flatMapTo(mutableSetOf()) { it }
                }
            }
        }
        .distinctUntilChanged()

    /** Whether a batch of any key is changing [component] now. */
    fun isInFlight(component: ComponentKey): Boolean = runs.value.values.any { run ->
        run.key.user == component.userId && component.id in run.step.value.pending
    }

    /**
     * Lists a batch of [components] of [key]'s user towards [goal] as running, before anything is
     * sent.
     *
     * @return the batch to [start] or [release], or null when a batch of [key] is running
     * already, which is then left alone
     */
    fun reserve(key: BatchKey, goal: ControlGoal, components: Collection<ComponentId>): BatchRun? = synchronized(runs) {
        if (key in runs.value) return null
        val run = BatchRun(key, goal, MutableStateFlow(BatchStep(done = 0, total = components.size, pending = components.toSet())))
        runs.update { it + (key to run) }
        run
    }

    /** Ends [run] without sending anything, as when the screen went away before it started. */
    fun release(run: BatchRun) {
        if (run.result.complete(emptyList())) remove(run)
    }

    /**
     * Sends [components] of [run] through [layers], in requests of at most [BATCH_LIMIT]
     * components: up to that many go in one request, which is fastest, with one read, one
     * PackageManager call and one firewall write for each app. A request that fails in a way the
     * repository does not report keeps the outcomes gathered before it and fails the components
     * not yet done with [ControlFailure.SystemError].
     */
    fun start(run: BatchRun, components: List<ComponentDeclaration>, layers: Set<ControlLayer>) {
        updatePending(run, components)
        launch(run) { ControlRequest(run.key.user, components, run.goal, layers) }
    }

    /** Prepares and sends a reserved batch in the application scope. */
    fun start(run: BatchRun, prepare: suspend () -> ControlRequest) {
        launch(run) {
            val request = prepare()
            require(request.user == run.key.user && request.goal == run.goal)
            updatePending(run, request.components)
            request
        }
    }

    private fun updatePending(run: BatchRun, components: List<ComponentDeclaration>) {
        run.progress.value = BatchStep(done = 0, total = components.size, pending = components.mapTo(mutableSetOf()) { it.id })
    }

    private fun launch(run: BatchRun, prepare: suspend () -> ControlRequest) {
        scope.launch {
            val outcomes = mutableListOf<ComponentOutcome>()
            try {
                val request = prepare()
                val packages = request.components.mapTo(mutableSetOf()) { it.id.packageName }
                controlActivity.duringControl(packages) { send(run, request.components, request.layers, outcomes) }
                run.result.complete(outcomes)
            } catch (e: CancellationException) {
                run.result.cancel(e)
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Cannot run batch ${run.goal} of ${run.key}")
                run.result.completeExceptionally(e)
            } finally {
                remove(run)
            }
        }.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                run.result.cancel(cause)
                remove(run)
            }
        }
    }

    private suspend fun send(run: BatchRun, components: List<ComponentDeclaration>, layers: Set<ControlLayer>, outcomes: MutableList<ComponentOutcome>) {
        components.chunked(BATCH_LIMIT).forEachIndexed { index, step ->
            try {
                componentStatuses.control(ControlRequest(run.key.user, step, run.goal, layers)).collect { event ->
                    if (event is ControlEvent.PackageDone) outcomes += event.outcomes
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Batch ${run.goal} of ${run.key} failed in request ${index + 1} of ${step.size} components")
                val done = outcomes.mapTo(mutableSetOf()) { it.key.id }
                outcomes += components.filterNot { it.id in done }.map { it.failedWith(run, layers, e) }
                return
            }
            val sent = step.mapTo(mutableSetOf()) { it.id }
            run.progress.update { it.copy(done = it.done + step.size, pending = it.pending - sent) }
        }
    }

    private fun ComponentDeclaration.failedWith(run: BatchRun, layers: Set<ControlLayer>, e: Exception) = ComponentOutcome(
        key = ComponentKey(id, run.key.user),
        state = ControlState(PmSetting.UNKNOWN, IfwBlock.UNKNOWN),
        result = ControlResult.FAILED,
        failures = layersFor(layers).associateWith { ControlFailure.SystemError(e.toString()) },
    )

    private fun remove(run: BatchRun) = synchronized(runs) {
        runs.update { current -> if (current[run.key] === run) current - run.key else current }
    }

    companion object {
        /**
         * Components per request. Each request reads the apps, changes them and reads them back,
         * so fewer requests are faster; above this a batch shows its progress between requests.
         */
        const val BATCH_LIMIT = 200
    }
}

/**
 * A batch of components of [key]'s user changing towards [goal].
 *
 * @property step how far it got
 * @property outcomes every outcome, once the last request ended; none when it was released.
 * Cancellation of the batch cancels the deferred, even if some components already changed.
 */
class BatchRun internal constructor(
    val key: BatchKey,
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
