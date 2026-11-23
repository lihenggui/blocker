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

package com.merxury.blocker.core.control

import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.component.isFilteredByIntentFirewall
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlResult

// What a goal asks of each layer and how the state read back is judged, as ComponentControl says.

/**
 * The layers [goal] changes and judges for [component]. A block uses [requested], except that a
 * provider, which the firewall does not filter, uses the PackageManager setting. An unblock uses
 * every layer that applies.
 */
internal fun ComponentDeclaration.layersFor(goal: ControlGoal, requested: Set<ControlLayer>): Set<ControlLayer> {
    val filtered = type.isFilteredByIntentFirewall
    return when (goal) {
        ControlGoal.BLOCK -> if (filtered) requested else setOf(ControlLayer.PACKAGE_MANAGER)
        ControlGoal.UNBLOCK -> if (filtered) ControlLayer.entries.toSet() else setOf(ControlLayer.PACKAGE_MANAGER)
    }
}

/** The setting to send so the PackageManager layer meets [goal], or null when it already does. */
internal fun ComponentDeclaration.pmChange(goal: ControlGoal, layers: Set<ControlLayer>, current: PmSetting): PmSetting? {
    if (ControlLayer.PACKAGE_MANAGER !in layers) return null
    return when (goal) {
        ControlGoal.BLOCK -> PmSetting.DISABLED.takeIf { current != PmSetting.DISABLED }

        // DEFAULT leaves no override, but would keep a component the manifest disables blocked.
        ControlGoal.UNBLOCK -> when {
            isPmClear(current) -> null
            enabledInManifest -> PmSetting.DEFAULT
            else -> PmSetting.ENABLED
        }
    }
}

/**
 * Whether to block (true) or unblock (false) through the firewall, or null when the firewall is not
 * involved. There is no "already done": the rule store decides that under its own lock from the
 * file, writes nothing when the rules stay as they are, and a conditional rule is never removed.
 */
internal fun ifwChange(goal: ControlGoal, layers: Set<ControlLayer>): Boolean? = if (ControlLayer.INTENT_FIREWALL in layers) goal == ControlGoal.BLOCK else null

/**
 * Judges [component] by [after], the state read back. [before] is the state the request started
 * from; [failures] holds what went wrong per layer while changing it. A layer that does not show
 * [goal] without a recorded failure gets [ControlFailure.StateNotConfirmed].
 */
internal fun judge(
    component: ComponentDeclaration,
    user: UserId,
    goal: ControlGoal,
    layers: Set<ControlLayer>,
    before: ControlState,
    after: ControlState,
    failures: Map<ControlLayer, ControlFailure>,
): ComponentOutcome {
    // For an unblock, no layer missed exactly when the component is runnable or conditional.
    val missed = layers.filterNot { component.meets(goal, it, after) }
    val result = when {
        missed.isEmpty() -> ControlResult.ACHIEVED

        goal == ControlGoal.BLOCK -> if (missed.size < layers.size) ControlResult.PARTIAL else ControlResult.FAILED

        // Unblocking moved a layer to the goal, but another one still blocks or cannot be read.
        layers.any { component.meets(goal, it, after) && !component.meets(goal, it, before) } -> ControlResult.PARTIAL

        else -> ControlResult.FAILED
    }
    val reported = missed.associateWith { failures[it] ?: ControlFailure.StateNotConfirmed }
    return ComponentOutcome(ComponentKey(component.id, user), after, result, reported)
}

/** Whether [layer] shows [goal] in [state]. */
private fun ComponentDeclaration.meets(goal: ControlGoal, layer: ControlLayer, state: ControlState): Boolean = when (goal) {
    ControlGoal.BLOCK -> when (layer) {
        ControlLayer.PACKAGE_MANAGER -> state.pm == PmSetting.DISABLED
        ControlLayer.INTENT_FIREWALL -> state.ifw == IfwBlock.BLOCKED
    }

    ControlGoal.UNBLOCK -> when (layer) {
        ControlLayer.PACKAGE_MANAGER -> isPmClear(state.pm)
        ControlLayer.INTENT_FIREWALL -> state.ifw == IfwBlock.NOT_BLOCKED || state.ifw == IfwBlock.CONDITIONAL
    }
}

/** Whether [setting] lets the component run. */
private fun ComponentDeclaration.isPmClear(setting: PmSetting): Boolean = setting == PmSetting.ENABLED || (setting == PmSetting.DEFAULT && enabledInManifest)
