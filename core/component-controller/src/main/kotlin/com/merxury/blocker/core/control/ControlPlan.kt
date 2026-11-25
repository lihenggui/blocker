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
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.control.isKnown
import com.merxury.blocker.core.model.control.isPmClear
import com.merxury.blocker.core.model.control.meets
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity

// What a goal asks of each layer and how the state read back is judged, as ComponentControl says.

/** The setting to send so the PackageManager layer meets [goal], or null when it already does. */
internal fun ComponentDeclaration.pmChange(goal: ControlGoal, layers: Set<ControlLayer>, current: PmSetting): PmSetting? {
    if (ControlLayer.PACKAGE_MANAGER !in layers) return null
    return when (goal) {
        ControlGoal.BLOCK -> PmSetting.DISABLED.takeIf { current != PmSetting.DISABLED }

        // Only the block goes; an unread setting may be one, so it is cleared too.
        ControlGoal.UNBLOCK -> PmSetting.DEFAULT.takeIf { current == PmSetting.DISABLED || current == PmSetting.UNKNOWN }

        // DEFAULT leaves no override, but would keep a component the manifest disables blocked.
        ControlGoal.ENABLE -> when {
            isPmClear(current) -> null
            enabledInManifest -> PmSetting.DEFAULT
            else -> PmSetting.ENABLED
        }
    }
}

/**
 * Whether to block (true) or unblock (false, for an unblock and an enable) through the firewall,
 * or null when the firewall is not involved. There is no "already done": the rule store decides that under its own lock from the
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
    // Only the requested layers count: an unblock of one layer is achieved when that layer is
    // clear, even if another layer still blocks; the state reported still shows that layer.
    val allowed = layers.filterNot { failures[it] == ControlFailure.ProtectedComponent }
    if (allowed.isEmpty()) {
        return ComponentOutcome(ComponentKey(component.id, user), after, ControlResult.SKIPPED, failures)
    }
    val missed = allowed.filterNot { component.meets(goal, it, after) }
    val result = when {
        missed.isEmpty() -> ControlResult.ACHIEVED

        goal == ControlGoal.BLOCK -> if (missed.size < allowed.size) ControlResult.PARTIAL else ControlResult.FAILED

        // Unblocking or enabling moved a layer to the goal, but another requested one does not meet it or cannot be read.
        allowed.any { component.moved(goal, it, before, after) } -> ControlResult.PARTIAL

        else -> ControlResult.FAILED
    }
    val reported = missed.associateWith { failures[it] ?: ControlFailure.StateNotConfirmed }
    return ComponentOutcome(ComponentKey(component.id, user), after, result, reported)
}

/**
 * Whether [layer] moved to [goal]: it was read before and did not show the goal, and shows it now.
 * A layer that could not be read before may have shown the goal already, so reading it afterwards
 * proves no change. What the calls answered does not count, only the states read.
 */
private fun ComponentDeclaration.moved(
    goal: ControlGoal,
    layer: ControlLayer,
    before: ControlState,
    after: ControlState,
): Boolean = before.isKnown(layer) && !meets(goal, layer, before) && meets(goal, layer, after)

/**
 * The layers a request works on and judges for one component, from [this], the requested ones.
 * An identity that cannot write system files, such as the shell, can neither read nor change the
 * Intent Firewall, so an unblock or enable leaves that layer out and is judged on the
 * PackageManager layer alone. A block, or a request for the firewall layer alone, keeps it and
 * reports that the identity is not permitted. Without a channel nothing changes here.
 */
internal fun Set<ControlLayer>.reachableBy(goal: ControlGoal, identity: PrivilegedIdentity?): Set<ControlLayer> {
    val firewallUnreachable = identity != null && !identity.canWriteSystemFiles
    val dropFirewall = goal != ControlGoal.BLOCK && firewallUnreachable && ControlLayer.PACKAGE_MANAGER in this
    return if (dropFirewall) this - ControlLayer.INTENT_FIREWALL else this
}
