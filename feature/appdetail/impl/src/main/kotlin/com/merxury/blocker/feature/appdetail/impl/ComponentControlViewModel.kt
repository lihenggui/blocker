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
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.blockLayers
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.feature.appdetail.impl.ComponentBatchRunner.Companion.BATCH_LIMIT
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
    private val channel: PrivilegedChannel,
    private val userDataRepository: UserDataRepository,
    private val analyticsHelper: AnalyticsHelper,
    @Assisted packageName: String,
    @Assisted userId: Int?,
) : ViewModel() {
    private val app = selectedUsers.appKeyOf(packageName, userId)
    private val singles = MutableStateFlow<Set<ComponentId>>(emptySet())
    private val messages = MutableStateFlow<List<QueuedControlMessage>>(emptyList())
    private var nextMessageId = 0L

    @OptIn(ExperimentalCoroutinesApi::class)
    private val batch: Flow<Pair<BatchRun, BatchStep>?> = batches.running(app).flatMapLatest { run ->
        run?.step?.map { run to it } ?: flowOf(null)
    }

    val uiState: StateFlow<ControlUiState> = combine(singles, batch, messages) { singles, batch, messages ->
        ControlUiState(
            inFlight = singles + batch?.second?.pending.orEmpty(),
            batch = batch?.let { (run, step) -> BatchProgress(run.goal, done = step.done.takeIf { step.total > BATCH_LIMIT }, total = step.total) },
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
    fun control(component: ComponentId, goal: ControlGoal) {
        if (isInFlight(component)) return
        singles.update { it + component }
        viewModelScope.launch {
            try {
                analyticsHelper.logSwitchComponentClicked(newState = goal != ControlGoal.BLOCK)
                val status = cachedStatuses(listOf(component)).singleOrNull() ?: return@launch
                val outcomes = mutableListOf<ComponentOutcome>()
                componentStatuses.control(ControlRequest(app.userId, listOf(status.declaration), goal, layersFor(goal))).collect { event ->
                    if (event is ControlEvent.PackageDone) outcomes += event.outcomes
                }
                outcomes.firstNotNullOfOrNull { it.toMessage(goal, identity()) }?.let { tell(it, component) }
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
        val run = batches.reserve(app, goal, ids) ?: return
        val enable = goal != ControlGoal.BLOCK
        if (fromMatchedRule) analyticsHelper.logControlAllComponentsInSdkClicked(enable) else analyticsHelper.logBatchOperationPerformed(enable)
        viewModelScope.launch {
            var started = false
            try {
                // The states the rows showed when the batch was sent, to tell what it changed.
                val statuses = cachedStatuses(ids)
                val layers = layersFor(goal)
                batches.start(run, statuses.map { it.declaration }, layers)
                started = true
                tell(batchSummary(goal, run.outcomes.await(), statuses.associateBy { it.declaration.id }, layers, identity()))
            } finally {
                if (!started) batches.release(run)
            }
        }
    }

    private fun isInFlight(component: ComponentId): Boolean = component in singles.value || batches.current(app)?.step?.value?.pending?.contains(component) == true

    private suspend fun layersFor(goal: ControlGoal): Set<ControlLayer> = if (goal == ControlGoal.BLOCK) {
        userDataRepository.userData.first().controllerType.blockLayers
    } else {
        ControlLayer.entries.toSet()
    }

    private suspend fun cachedStatuses(ids: List<ComponentId>): List<ComponentStatus> {
        val cached = componentStatuses.components(app).first().associateBy { it.declaration.id }
        return ids.mapNotNull { id ->
            cached[id] ?: run {
                Timber.w("$id is no longer cached for $app, leave it out")
                null
            }
        }
    }

    private fun identity(): PrivilegedIdentity? = (channel.state.value as? ChannelState.Ready)?.identity

    /**
     * Queues [message]. A message about [component] replaces one about it that waits, so repeated
     * taps queue one message each; the message shown, the first, stays, and so do batch summaries.
     */
    private fun tell(message: ControlMessage, component: ComponentId? = null) {
        val queued = QueuedControlMessage(nextMessageId++, message, component)
        messages.update { current ->
            val waiting = current.drop(1).filterNot { component != null && it.component == component }
            current.take(1) + waiting + queued
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(packageName: String, userId: Int?): ComponentControlViewModel
    }
}
