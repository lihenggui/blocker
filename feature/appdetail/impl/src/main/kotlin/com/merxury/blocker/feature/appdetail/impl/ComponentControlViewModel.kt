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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.domain.control.BatchKey
import com.merxury.blocker.core.domain.control.BatchRun
import com.merxury.blocker.core.domain.control.BatchStep
import com.merxury.blocker.core.domain.control.ComponentBatchRunner
import com.merxury.blocker.core.domain.control.ComponentBatchRunner.Companion.BATCH_LIMIT
import com.merxury.blocker.core.domain.control.ControlComponentUseCase
import com.merxury.blocker.core.domain.control.GetControlLayersUseCase
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.ui.control.ControlMessage
import com.merxury.blocker.core.ui.control.QueuedControlMessage
import com.merxury.blocker.core.ui.control.batchSummary
import com.merxury.blocker.core.ui.control.enqueue
import com.merxury.blocker.core.ui.control.toMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Blocks and unblocks components of one app for one user through the component repository.
 *
 * Nothing here changes what a row shows: the repository stores the state read back after each
 * change and its flow updates the rows. This only tracks which components are in flight and turns
 * the outcomes into messages. A block uses the layers of the controller type chosen in the
 * settings. Turning one switch on enables the component through every layer, so it can run
 * whatever blocked it; a batch only removes the blocks of every layer, and leaves components the
 * app ships disabled as they are.
 *
 * Batches run in [batches], so they end even when the screen goes away; only the view model that
 * started one tells its summary.
 */
@HiltViewModel(assistedFactory = ComponentControlViewModel.Factory::class)
class ComponentControlViewModel @AssistedInject constructor(
    selectedUsers: SelectedUserRepository,
    private val componentStatuses: ComponentStatusRepository,
    private val batches: ComponentBatchRunner,
    private val controlComponent: ControlComponentUseCase,
    private val layersFor: GetControlLayersUseCase,
    private val analyticsHelper: AnalyticsHelper,
    @Assisted packageName: String,
    @Assisted userId: Int?,
) : ViewModel() {
    private val app = selectedUsers.appKeyOf(packageName, userId)
    private val batchKey = BatchKey.App(app)
    private val singles = MutableStateFlow<Set<ComponentId>>(emptySet())
    private val messages = MutableStateFlow<List<QueuedControlMessage>>(emptyList())
    private var nextMessageId = 0L

    @OptIn(ExperimentalCoroutinesApi::class)
    private val batch: Flow<Pair<BatchRun, BatchStep>?> = batches.running(batchKey).flatMapLatest { run ->
        run?.step?.map { run to it } ?: flowOf(null)
    }

    // The app's components a batch of any screen is changing, such as one started from the search.
    private val inBatches: Flow<Set<ComponentId>> = batches.inFlight.map { keys ->
        keys.filter { it.userId == app.userId && it.id.packageName == app.packageName }.mapTo(mutableSetOf()) { it.id }
    }.distinctUntilChanged()

    val uiState: StateFlow<ControlUiState> = combine(singles, batch, inBatches, messages) { singles, batch, inBatches, messages ->
        val ownPending = batch?.second?.pending.orEmpty()
        ControlUiState(
            inFlight = singles + inBatches,
            batch = batch?.let { (run, step) -> BatchProgress(run.goal, done = step.done.takeIf { step.total > BATCH_LIMIT }, total = step.total) },
            batchElsewhere = (inBatches - ownPending).isNotEmpty(),
            messages = messages,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ControlUiState(),
        )

    /** Turns one component's switch: on enables it through every layer, off blocks it. */
    fun setEnabled(component: ComponentId, enabled: Boolean) {
        control(component, if (enabled) ControlGoal.ENABLE else ControlGoal.BLOCK)
    }

    /** Changes one component towards [goal]; ignored while a change of it is in flight. */
    private fun control(component: ComponentId, goal: ControlGoal) {
        if (isInFlight(component)) return
        singles.update { it + component }
        viewModelScope.launch {
            try {
                analyticsHelper.logSwitchComponentClicked(newState = goal != ControlGoal.BLOCK)
                controlComponent(ComponentKey(component, app.userId), goal)?.toMessage(goal)?.let { tell(it, component) }
            } finally {
                singles.update { it - component }
            }
        }
    }

    /** Blocks [components] through the layers of the controller type and ends with one summary. */
    fun blockAll(components: List<ComponentId>, fromMatchedRule: Boolean = false) = startBatch(components, ControlGoal.BLOCK, fromMatchedRule)

    /**
     * Removes every block of [components], through every layer, and ends with one summary.
     * Components the app ships disabled stay as they are: undoing blocks in bulk does not turn on
     * what the app keeps off.
     */
    fun unblockAll(components: List<ComponentId>, fromMatchedRule: Boolean = false) = startBatch(components, ControlGoal.UNBLOCK, fromMatchedRule)

    fun onMessageShown(id: Long) {
        messages.update { queued -> queued.filterNot { it.id == id } }
    }

    /**
     * Sends [components] as one batch, see [ComponentBatchRunner]. The batch counts as running,
     * with its components in flight, from the moment it is chosen, before the cache and the
     * settings are read. Components in flight are left out, and nothing starts while another
     * batch of the app runs.
     */
    private fun startBatch(components: List<ComponentId>, goal: ControlGoal, fromMatchedRule: Boolean) {
        val ids = components.distinct().filterNot(::isInFlight)
        if (ids.isEmpty()) return
        val run = batches.reserve(batchKey, goal, ids) ?: return
        val enable = goal != ControlGoal.BLOCK
        if (fromMatchedRule) analyticsHelper.logControlAllComponentsInSdkClicked(enable) else analyticsHelper.logBatchOperationPerformed(enable)
        viewModelScope.launch {
            var started = false
            try {
                val statuses = cachedStatuses(ids)
                val vanished = (ids - statuses.mapTo(mutableSetOf()) { it.declaration.id }).map { ComponentKey(it, app.userId) }
                batches.start(run, statuses.map { it.declaration }, layersFor(goal))
                started = true
                tell(batchSummary(run.outcomes.await(), vanished))
            } finally {
                if (!started) batches.release(run)
            }
        }
    }

    private fun isInFlight(component: ComponentId): Boolean = component in singles.value || batches.isInFlight(ComponentKey(component, app.userId))

    private suspend fun cachedStatuses(ids: List<ComponentId>): List<ComponentStatus> {
        val cached = componentStatuses.components(app).first().associateBy { it.declaration.id }
        return ids.mapNotNull { id ->
            cached[id] ?: run {
                Timber.w("$id is no longer cached for $app, tell it as not found")
                null
            }
        }
    }

    /**
     * Queues [message]. A message about [component] replaces one about it that waits, so repeated
     * taps queue one message each; the message shown, the first, stays, and so do batch summaries.
     */
    private fun tell(message: ControlMessage, component: ComponentId? = null) {
        val queued = QueuedControlMessage(nextMessageId++, message, component)
        messages.update { it.enqueue(queued) }
    }

    @AssistedFactory
    interface Factory {
        fun create(packageName: String, userId: Int?): ComponentControlViewModel
    }
}
