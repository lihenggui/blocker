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

package com.merxury.blocker.core.data.history

import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlLayer.INTENT_FIREWALL
import com.merxury.blocker.core.model.component.ControlLayer.PACKAGE_MANAGER
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.component.isFilteredByIntentFirewall
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult

/** Declaration and layer requests derived from one live refresh. */
internal data class ComponentUndoPlan(
    val key: ComponentKey,
    val declaration: ComponentDeclaration?,
    val requests: List<UndoRequest>,
    val outcome: UndoOutcome,
    val ifwTarget: IfwBlock? = null,
) {
    companion object {
        fun skipped(key: ComponentKey) = ComponentUndoPlan(key, null, emptyList(), UndoOutcome.SKIPPED)
    }
}

internal data class UndoTarget(val before: ControlState, val after: ControlState) {
    fun plan(key: ComponentKey, status: ComponentStatus, wakeOwned: Boolean): ComponentUndoPlan {
        val pm = planPm(key.userId, status.state.pm, wakeOwned)
        val ifwTarget = before.ifw.takeIf { it != after.ifw && status.declaration.type.isFilteredByIntentFirewall }
        val ifw = if (ifwTarget != null) planIfw(key.userId, status.state.ifw) else LayerPlan.AtTarget
        val outcome = if (LayerPlan.Skip in listOf(pm, ifw)) UndoOutcome.SKIPPED else UndoOutcome.ALREADY_AT_TARGET
        val requests = listOf(pm, ifw).filterIsInstance<LayerPlan.Write>().map { it.request }
        return ComponentUndoPlan(key, status.declaration, requests, outcome, ifwTarget)
    }

    private fun planPm(user: UserId, current: PmSetting, wakeOwned: Boolean): LayerPlan {
        val target = before.pm
        val expected = after.pm
        if (target == expected) return LayerPlan.AtTarget
        if (wakeOwned || current == PmSetting.UNKNOWN || target == PmSetting.UNKNOWN) return LayerPlan.Skip
        if (current == target) return LayerPlan.AtTarget
        if (current != expected || (target == PmSetting.DEFAULT && expected == PmSetting.ENABLED)) return LayerPlan.Skip
        val goal = when (target) {
            PmSetting.DISABLED -> ControlGoal.BLOCK
            PmSetting.DEFAULT -> ControlGoal.UNBLOCK
            PmSetting.ENABLED -> ControlGoal.ENABLE_EXPLICIT
            PmSetting.UNKNOWN -> return LayerPlan.Skip
        }
        return LayerPlan.Write(UndoRequest(user, goal, PACKAGE_MANAGER, expected))
    }

    private fun planIfw(user: UserId, current: IfwBlock): LayerPlan {
        val target = before.ifw
        val expected = after.ifw
        if (current == IfwBlock.UNKNOWN || current == IfwBlock.CONDITIONAL) return LayerPlan.Skip
        val goal = when (target) {
            IfwBlock.BLOCKED -> ControlGoal.BLOCK
            IfwBlock.NOT_BLOCKED -> ControlGoal.UNBLOCK
            IfwBlock.UNKNOWN, IfwBlock.CONDITIONAL -> return LayerPlan.Skip
        }
        if (current == target) return LayerPlan.AtTarget
        if (current != expected) return LayerPlan.Skip
        return LayerPlan.Write(UndoRequest(user, goal, INTENT_FIREWALL))
    }

    private sealed interface LayerPlan {
        data object AtTarget : LayerPlan
        data object Skip : LayerPlan
        data class Write(val request: UndoRequest) : LayerPlan
    }
}

/** Requests with the same user, goal, layer and PM guard can share one restore call. */
internal data class UndoRequest(val user: UserId, val goal: ControlGoal, val layer: ControlLayer, val expectedPm: PmSetting? = null) {
    fun controlRequest(declarations: List<ComponentDeclaration>) = ControlRequest(user, declarations, goal, setOf(layer), expectedPm)

    fun outcome(outcome: ComponentOutcome, written: Boolean): UndoOutcome {
        if (outcome.result == ControlResult.SKIPPED) return UndoOutcome.SKIPPED
        if (layer in outcome.failures || !reachedTarget(outcome.state)) return UndoOutcome.FAILED
        return if (written) UndoOutcome.RESTORED else UndoOutcome.ALREADY_AT_TARGET
    }

    private fun reachedTarget(state: ControlState): Boolean = when (layer) {
        PACKAGE_MANAGER -> state.pm == when (goal) {
            ControlGoal.BLOCK -> PmSetting.DISABLED
            ControlGoal.UNBLOCK -> PmSetting.DEFAULT
            ControlGoal.ENABLE_EXPLICIT -> PmSetting.ENABLED
            ControlGoal.ENABLE -> error("Undo requires an exact PackageManager target")
        }

        INTENT_FIREWALL -> state.ifw == if (goal == ControlGoal.BLOCK) IfwBlock.BLOCKED else IfwBlock.NOT_BLOCKED
    }
}

/** Later outcomes take precedence when combining the layers of a component. */
internal enum class UndoOutcome {
    ALREADY_AT_TARGET,
    RESTORED,
    SKIPPED,
    FAILED,
    ;

    val complete: Boolean get() = this == ALREADY_AT_TARGET || this == RESTORED
}
