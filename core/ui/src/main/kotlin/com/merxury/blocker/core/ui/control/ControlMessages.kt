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

package com.merxury.blocker.core.ui.control

import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.control.isKnown
import com.merxury.blocker.core.model.control.layersFor
import com.merxury.blocker.core.model.control.meets
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity

/** Why a layer of a component does not show what was asked for. */
data class LayerFailure(
    val layer: ControlLayer,
    val failure: ControlFailure,
)

/** How a change of components ended, as the user is told. */
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

/** The message for one component's [this] outcome, or null when it reached its goal. */
fun ComponentOutcome.toMessage(goal: ControlGoal, identity: PrivilegedIdentity?): ControlMessage? {
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
 * and keeps the first message seen. The components may belong to several apps of one user.
 *
 * @param vanished components chosen for the batch that were no longer cached when it was sent, so
 * nothing was sent for them; they count as not changed because they were not found
 */
fun batchSummary(
    goal: ControlGoal,
    outcomes: List<ComponentOutcome>,
    before: Map<ComponentId, ComponentStatus>,
    layers: Set<ControlLayer>,
    identity: PrivilegedIdentity?,
    vanished: Collection<ComponentKey> = emptyList(),
): ControlMessage.BatchDone {
    val achieved = outcomes.filter { it.result == ControlResult.ACHIEVED }
    val notChanged = outcomes.filter { it.result != ControlResult.ACHIEVED } + vanished.map { it.notFound() }
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

private fun ComponentKey.notFound() = ComponentOutcome(
    key = this,
    state = ControlState(PmSetting.UNKNOWN, IfwBlock.UNKNOWN),
    result = ControlResult.FAILED,
    failures = mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.TargetNotFound(message = null)),
)

/** Whether a layer of [this] component was read and did not meet [goal]. */
private fun ComponentStatus.wasShortOf(goal: ControlGoal, layers: Set<ControlLayer>): Boolean = declaration.layersFor(layers).any { layer ->
    state.isKnown(layer) && !declaration.meets(goal, layer, state)
}

/** Whether the app ships [this] component disabled and [setting] leaves it so. */
private fun ComponentDeclaration.isShippedDisabledAt(setting: PmSetting): Boolean = !enabledInManifest && setting == PmSetting.DEFAULT
