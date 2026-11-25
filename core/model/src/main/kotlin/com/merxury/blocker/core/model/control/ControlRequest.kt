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

package com.merxury.blocker.core.model.control

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.component.isFilteredByIntentFirewall
import com.merxury.blocker.core.model.data.ControllerType

/** What a [ControlRequest] wants for its components. */
enum class ControlGoal {
    /** Stop the component through the layers resolved for this request. */
    BLOCK,

    /**
     * Remove blocks from the layers resolved for this request. A DISABLED PackageManager
     * setting becomes DEFAULT, any other setting is left alone, and the component leaves the
     * unconditional Intent Firewall rules. A component the manifest ships disabled stays at
     * DEFAULT, so it is still not runnable; a layer that was not requested may still block it.
     * What a restore of a backup and a reset of the PackageManager settings use.
     */
    UNBLOCK,

    /**
     * Make the component runnable through the layers resolved for this request: [UNBLOCK], and when
     * [ControlLayer.PACKAGE_MANAGER] is requested for a component the manifest ships disabled,
     * an ENABLED setting. With every layer, [ControlLayer.entries], the component becomes
     * runnable, or conditional when only a conditional rule is left.
     */
    ENABLE,
}

/**
 * What to do to which components of one user. The components may belong to many packages.
 *
 * @property user whose PackageManager settings change. Intent Firewall rules are device-wide, so a
 * change to them reaches every user.
 * @property components in the order to work through them; packages are handled in the order they
 * first appear, and a component listed twice counts once
 * @property layers the requested layers, which must be nonempty. Component control resolves them
 * with [layersFor] for the component type and goal. Backup restoration uses the recorded layers,
 * with the PackageManager layer for providers.
 */
data class ControlRequest(
    val user: UserId,
    val components: List<ComponentDeclaration>,
    val goal: ControlGoal,
    val layers: Set<ControlLayer>,
) {
    init {
        require(layers.isNotEmpty()) { "A request needs at least one layer" }
    }
}

/** The layers a [ControlGoal.BLOCK] uses when the user chose this controller type. */
val ControllerType.blockLayers: Set<ControlLayer>
    get() = when (this) {
        ControllerType.PM, ControllerType.SHIZUKU -> setOf(ControlLayer.PACKAGE_MANAGER)
        ControllerType.IFW -> setOf(ControlLayer.INTENT_FIREWALL)
        ControllerType.IFW_PLUS_PM -> setOf(ControlLayer.PACKAGE_MANAGER, ControlLayer.INTENT_FIREWALL)
    }

/**
 * Returns the layers to change and judge for this component and [goal]. Providers always use
 * PackageManager. Intent Firewall alone cannot stop implicit broadcasts to a single receiver,
 * so blocking a receiver with only that layer requested uses PackageManager. Unblocking or
 * enabling in that case uses both layers to remove any earlier firewall block as well.
 */
fun ComponentDeclaration.layersFor(requested: Set<ControlLayer>, goal: ControlGoal): Set<ControlLayer> = when {
    type == ComponentType.RECEIVER && requested == setOf(ControlLayer.INTENT_FIREWALL) -> when (goal) {
        ControlGoal.BLOCK -> setOf(ControlLayer.PACKAGE_MANAGER)
        ControlGoal.UNBLOCK, ControlGoal.ENABLE -> requested + ControlLayer.PACKAGE_MANAGER
    }

    else -> supportedLayers(requested)
}

/** Resolves the layers a component supports, using PackageManager for providers. */
fun ComponentDeclaration.supportedLayers(requested: Set<ControlLayer>): Set<ControlLayer> = if (type.isFilteredByIntentFirewall) requested else setOf(ControlLayer.PACKAGE_MANAGER)

/** Whether [layer] shows [goal] in [state], as ComponentControl judges a request. */
fun ComponentDeclaration.meets(goal: ControlGoal, layer: ControlLayer, state: ControlState): Boolean = when (goal) {
    ControlGoal.BLOCK -> when (layer) {
        ControlLayer.PACKAGE_MANAGER -> state.pm == PmSetting.DISABLED
        ControlLayer.INTENT_FIREWALL -> state.ifw == IfwBlock.BLOCKED
    }

    // No block by the layer; the manifest default may still keep the component from running.
    ControlGoal.UNBLOCK -> when (layer) {
        ControlLayer.PACKAGE_MANAGER -> state.pm != PmSetting.DISABLED && state.pm != PmSetting.UNKNOWN
        ControlLayer.INTENT_FIREWALL -> state.ifw.isClear
    }

    ControlGoal.ENABLE -> when (layer) {
        ControlLayer.PACKAGE_MANAGER -> isPmClear(state.pm)
        ControlLayer.INTENT_FIREWALL -> state.ifw.isClear
    }
}

/** Whether [layer] of [this] state was read, rather than [PmSetting.UNKNOWN] or [IfwBlock.UNKNOWN]. */
fun ControlState.isKnown(layer: ControlLayer): Boolean = when (layer) {
    ControlLayer.PACKAGE_MANAGER -> pm != PmSetting.UNKNOWN
    ControlLayer.INTENT_FIREWALL -> ifw != IfwBlock.UNKNOWN
}

/** Whether [setting] lets the component run. */
fun ComponentDeclaration.isPmClear(setting: PmSetting): Boolean = setting == PmSetting.ENABLED || (setting == PmSetting.DEFAULT && enabledInManifest)

/** Whether no unconditional rule blocks the component; a conditional rule is not Blocker's. */
private val IfwBlock.isClear: Boolean get() = this == IfwBlock.NOT_BLOCKED || this == IfwBlock.CONDITIONAL
