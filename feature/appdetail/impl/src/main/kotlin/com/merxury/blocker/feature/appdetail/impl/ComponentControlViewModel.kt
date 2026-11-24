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
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.blockLayers
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Blocks and enables components of one app for one user through the component repository.
 *
 * Nothing here changes what a row shows: the repository stores the state read back after each
 * change and its flow updates the rows. This only tracks which components are in flight and turns
 * the outcomes into messages. A block uses the layers of the controller type chosen in the
 * settings; an enable uses every layer, so the component can run whatever blocked it.
 */
@HiltViewModel(assistedFactory = ComponentControlViewModel.Factory::class)
class ComponentControlViewModel @AssistedInject constructor(
    selectedUsers: SelectedUserRepository,
    private val componentStatuses: ComponentStatusRepository,
    private val userDataRepository: UserDataRepository,
    private val analyticsHelper: AnalyticsHelper,
    @Assisted packageName: String,
    @Assisted userId: Int?,
) : ViewModel() {
    private val app = selectedUsers.appKeyOf(packageName, userId)
    private val inFlight = MutableStateFlow<Set<ComponentId>>(emptySet())
    private val batch = MutableStateFlow<BatchProgress?>(null)
    private val messages = MutableStateFlow<List<ControlMessage>>(emptyList())

    val uiState: StateFlow<ControlUiState> = combine(inFlight, batch, messages, ::ControlUiState)
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
        if (component in inFlight.value) return
        viewModelScope.launch {
            analyticsHelper.logSwitchComponentClicked(newState = goal != ControlGoal.BLOCK)
            val outcomes = run(listOf(component), goal) {}
            outcomes.firstNotNullOfOrNull { it.toMessage(goal) }?.let(::tell)
        }
    }

    /**
     * Changes [components] towards [goal] in steps, reporting the progress, and ends with one
     * summary. Components already in flight are left out.
     */
    fun controlAll(components: List<ComponentId>, goal: ControlGoal, fromMatchedRule: Boolean = false) {
        val ids = components.distinct().filterNot { it in inFlight.value }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val enable = goal != ControlGoal.BLOCK
            if (fromMatchedRule) analyticsHelper.logControlAllComponentsInSdkClicked(enable) else analyticsHelper.logBatchOperationPerformed(enable)
            batch.value = BatchProgress(goal, done = 0, total = ids.size)
            val outcomes = try {
                run(ids, goal) { done -> batch.value = BatchProgress(goal, done, ids.size).takeIf { done < ids.size } }
            } finally {
                batch.value = null
            }
            tell(batchSummary(goal, outcomes))
        }
    }

    fun onMessageShown(message: ControlMessage) {
        messages.update { it - message }
    }

    /**
     * Sends [ids] in steps of [STEP], each its own request, so a batch reports progress. Every
     * component is in flight until its step ends.
     */
    private suspend fun run(ids: List<ComponentId>, goal: ControlGoal, onStep: (done: Int) -> Unit): List<ComponentOutcome> {
        inFlight.update { it + ids }
        try {
            val declarations = declarationsOf(ids)
            val layers = if (goal == ControlGoal.BLOCK) userDataRepository.userData.first().controllerType.blockLayers else ControlLayer.entries.toSet()
            val outcomes = mutableListOf<ComponentOutcome>()
            declarations.chunked(STEP).forEach { step ->
                componentStatuses.control(ControlRequest(app.userId, step, goal, layers)).collect { event ->
                    if (event is ControlEvent.PackageDone) outcomes += event.outcomes
                }
                inFlight.update { it - step.map(ComponentDeclaration::id).toSet() }
                onStep(outcomes.size)
            }
            return outcomes
        } finally {
            inFlight.update { it - ids.toSet() }
        }
    }

    private suspend fun declarationsOf(ids: List<ComponentId>): List<ComponentDeclaration> {
        val cached = componentStatuses.components(app).first().associateBy { it.declaration.id }
        return ids.mapNotNull { id ->
            cached[id]?.declaration ?: run {
                Timber.w("$id is no longer cached for $app, leave it out")
                null
            }
        }
    }

    private fun tell(message: ControlMessage) {
        messages.update { it + message }
    }

    @AssistedFactory
    interface Factory {
        fun create(packageName: String, userId: Int?): ComponentControlViewModel
    }

    private companion object {
        /** Components per request of a batch: few enough to show progress, many enough to be quick. */
        const val STEP = 10
    }
}
