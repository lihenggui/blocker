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
import com.merxury.blocker.core.data.history.OperationRecorder
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.blockLayers
import com.merxury.blocker.core.model.history.OperationSource
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * The layers a change towards a goal goes through: a block uses those of the controller type the
 * user chose, while enabling and unblocking go through every layer, so nothing a layer set stays.
 */
class GetControlLayersUseCase @Inject constructor(
    private val userDataRepository: UserDataRepository,
) {
    suspend operator fun invoke(goal: ControlGoal): Set<ControlLayer> = if (goal == ControlGoal.BLOCK) {
        userDataRepository.userData.first().controllerType.blockLayers
    } else {
        ControlLayer.entries.toSet()
    }
}

/**
 * Changes one component of one user towards a goal, as its switch asks, through the component
 * repository, which stores the state read back.
 */
class ControlComponentUseCase @Inject constructor(
    private val componentStatuses: ComponentStatusRepository,
    private val layersFor: GetControlLayersUseCase,
    private val operationRecorder: OperationRecorder,
) {
    /** The outcome for [component], or null when it is no longer cached and nothing was sent. */
    suspend operator fun invoke(
        component: ComponentKey,
        goal: ControlGoal,
        source: OperationSource,
        ref: String?,
    ): ComponentOutcome? = operationRecorder.withOperation(source, ref) { operation ->
        val status = componentStatuses.component(component).first() ?: run {
            Timber.w("${component.id} is no longer cached for user ${component.userId.value}, nothing to change")
            return@withOperation null
        }
        var outcome: ComponentOutcome? = null
        componentStatuses.control(ControlRequest(component.userId, listOf(status.declaration), goal, layersFor(goal)), operation).collect { event ->
            if (event is ControlEvent.PackageDone) outcome = event.outcomes.firstOrNull { it.key == component } ?: outcome
        }
        outcome
    }
}
