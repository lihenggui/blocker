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

import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlResult

/**
 * The changes of components in flight and what they ended in.
 *
 * @property inFlight components whose change has not ended; their switches ignore taps
 * @property batch the progress of the batch running, or null
 * @property messages outcomes to tell, oldest first; see [ComponentControlViewModel.onMessageShown]
 */
data class ControlUiState(
    val inFlight: Set<ComponentId> = emptySet(),
    val batch: BatchProgress? = null,
    val messages: List<ControlMessage> = emptyList(),
)

/**
 * [done] of [total] components of a batch have been changed or failed.
 *
 * @property done null while the batch is a single request, which reports only once it ends
 */
data class BatchProgress(
    val goal: ControlGoal,
    val done: Int?,
    val total: Int,
)

/** Why a layer of a component does not show what was asked for. */
data class LayerFailure(
    val layer: ControlLayer,
    val failure: ControlFailure,
)

sealed interface ControlMessage {
    /** One component did not reach [goal]; [partial] when some layers did. */
    data class ComponentNotChanged(
        val name: String,
        val goal: ControlGoal,
        val partial: Boolean,
        val failures: List<LayerFailure>,
    ) : ControlMessage

    /**
     * A batch ended: [changed] components reached [goal] and [notChanged] did not, most often
     * because of [mostCommon].
     */
    data class BatchDone(
        val goal: ControlGoal,
        val changed: Int,
        val notChanged: Int,
        val mostCommon: LayerFailure?,
    ) : ControlMessage
}

/** The message for one component's [outcome], or null when it reached its goal. */
internal fun ComponentOutcome.toMessage(goal: ControlGoal): ControlMessage? {
    if (result == ControlResult.ACHIEVED) return null
    return ControlMessage.ComponentNotChanged(
        name = key.id.simpleName,
        goal = goal,
        partial = result == ControlResult.PARTIAL,
        failures = failures.entries.sortedBy { it.key }.map { (layer, failure) -> LayerFailure(layer, failure) },
    )
}

/**
 * The summary of a batch of [outcomes]. The most common reason counts each layer and kind of
 * failure once per component, whatever its message, and keeps the first message seen.
 */
internal fun batchSummary(goal: ControlGoal, outcomes: List<ComponentOutcome>): ControlMessage.BatchDone {
    val notChanged = outcomes.filter { it.result != ControlResult.ACHIEVED }
    val mostCommon = notChanged
        .flatMap { outcome -> outcome.failures.map { (layer, failure) -> LayerFailure(layer, failure) } }
        .groupBy { it.layer to it.failure::class }
        .maxByOrNull { (_, failures) -> failures.size }
        ?.value
        ?.first()
    return ControlMessage.BatchDone(goal, changed = outcomes.size - notChanged.size, notChanged = notChanged.size, mostCommon = mostCommon)
}
