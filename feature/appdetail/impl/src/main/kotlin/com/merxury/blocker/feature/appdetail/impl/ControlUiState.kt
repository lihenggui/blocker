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

import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.control.isKnown
import com.merxury.blocker.core.model.control.layersFor
import com.merxury.blocker.core.model.control.meets
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity

/**
 * The changes of components in flight and what they ended in.
 *
 * @property inFlight components whose change has not ended; their switches ignore taps
 * @property batch the progress of the batch running for the app, or null; the batch actions are
 * disabled meanwhile
 * @property messages outcomes to tell, oldest first; see [ComponentControlViewModel.onMessageShown]
 */
data class ControlUiState(
    val inFlight: Set<ComponentId> = emptySet(),
    val batch: BatchProgress? = null,
    val messages: List<QueuedControlMessage> = emptyList(),
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

/**
 * A message waiting to be told. Two equal messages, such as the same switch failing twice, are
 * queued apart by their [id].
 *
 * @property component the component the message is about, or null for a batch summary
 */
data class QueuedControlMessage(
    val id: Long,
    val message: ControlMessage,
    val component: ComponentId? = null,
)

sealed interface ControlMessage {
    /**
     * One component did not reach [goal]; [partial] when some layers did.
     *
     * @property identity what the privileged channel ran as when the change ended, or null when it
     * was not ready
     */
    data class ComponentNotChanged(
        val name: String,
        val goal: ControlGoal,
        val partial: Boolean,
        val failures: List<LayerFailure>,
        val identity: PrivilegedIdentity? = null,
    ) : ControlMessage

    /**
     * A batch ended: [changed] components reached [goal] from another state and [notChanged] did
     * not, most often because of [mostCommon]. Components already at the goal count in neither, so
     * both are 0 when nothing needed changing.
     *
     * @property identity what the privileged channel ran as when the batch ended, or null when it
     * was not ready
     * @property staysOffAsShipped components an unblock left unblocked that still do not run,
     * because the app ships them disabled
     */
    data class BatchDone(
        val goal: ControlGoal,
        val changed: Int,
        val notChanged: Int,
        val mostCommon: LayerFailure?,
        val identity: PrivilegedIdentity? = null,
        val staysOffAsShipped: Int = 0,
    ) : ControlMessage
}

/** The message for one component's [outcome], or null when it reached its goal. */
internal fun ComponentOutcome.toMessage(goal: ControlGoal, identity: PrivilegedIdentity?): ControlMessage? {
    if (result == ControlResult.ACHIEVED) return null
    return ControlMessage.ComponentNotChanged(
        name = key.id.simpleName,
        goal = goal,
        partial = result == ControlResult.PARTIAL,
        failures = failures.entries.sortedBy { it.key }.map { (layer, failure) -> LayerFailure(layer, failure) },
        identity = identity,
    )
}

/**
 * The summary of a batch of [outcomes] through [layers]. A component counts as changed when it
 * reached the goal and one of its layers was read in [before], the cache when the batch was sent,
 * and did not meet the goal there: a layer that was unknown may have met it already. The most
 * common reason counts each layer and kind of failure once per component, whatever its message,
 * and keeps the first message seen.
 */
internal fun batchSummary(
    goal: ControlGoal,
    outcomes: List<ComponentOutcome>,
    before: Map<ComponentId, ComponentStatus>,
    layers: Set<ControlLayer>,
    identity: PrivilegedIdentity?,
): ControlMessage.BatchDone {
    val achieved = outcomes.filter { it.result == ControlResult.ACHIEVED }
    val notChanged = outcomes.filter { it.result != ControlResult.ACHIEVED }
    val changed = achieved.count { outcome -> before[outcome.key.id]?.wasShortOf(goal, layers) ?: false }
    val staysOff = if (goal == ControlGoal.UNBLOCK) achieved.count { before[it.key.id]?.declaration?.isShippedDisabledAt(it.state.pm) == true } else 0
    val mostCommon = notChanged
        .flatMap { outcome -> outcome.failures.map { (layer, failure) -> LayerFailure(layer, failure) } }
        .groupBy { it.layer to it.failure::class }
        .maxByOrNull { (_, failures) -> failures.size }
        ?.value
        ?.first()
    return ControlMessage.BatchDone(goal, changed = changed, notChanged = notChanged.size, mostCommon = mostCommon, identity = identity, staysOffAsShipped = staysOff)
}

/** Whether a layer of [this] component was read and did not meet [goal]. */
private fun ComponentStatus.wasShortOf(goal: ControlGoal, layers: Set<ControlLayer>): Boolean = declaration.layersFor(layers).any { layer ->
    state.isKnown(layer) && !declaration.meets(goal, layer, state)
}

/** Whether the app ships [this] component disabled and [setting] leaves it so. */
private fun ComponentDeclaration.isShippedDisabledAt(setting: PmSetting): Boolean = !enabledInManifest && setting == PmSetting.DEFAULT
