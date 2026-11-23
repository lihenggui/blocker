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

import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.ControllerType

/** What a [ControlRequest] wants for its components. */
enum class ControlGoal {
    /** Stop the component through the requested layers. */
    BLOCK,

    /** Make the component runnable, whatever layer stops it. */
    UNBLOCK,
}

/**
 * What to do to which components of one user. The components may belong to many packages.
 *
 * @property user whose PackageManager settings change. Intent Firewall rules are device-wide, so a
 * change to them reaches every user.
 * @property components in the order to work through them; packages are handled in the order they
 * first appear, and a component listed twice counts once
 * @property layers the layers a [ControlGoal.BLOCK] uses; not empty for a block, ignored for
 * [ControlGoal.UNBLOCK], which clears every layer. A provider is not filtered by the Intent
 * Firewall, so a block that asks for [ControlLayer.INTENT_FIREWALL] uses
 * [ControlLayer.PACKAGE_MANAGER] for it instead.
 */
data class ControlRequest(
    val user: UserId,
    val components: List<ComponentDeclaration>,
    val goal: ControlGoal,
    val layers: Set<ControlLayer>,
) {
    init {
        require(goal == ControlGoal.UNBLOCK || layers.isNotEmpty()) { "A block needs at least one layer" }
    }
}

/** The layers a [ControlGoal.BLOCK] uses when the user chose this controller type. */
val ControllerType.blockLayers: Set<ControlLayer>
    get() = when (this) {
        ControllerType.PM, ControllerType.SHIZUKU -> setOf(ControlLayer.PACKAGE_MANAGER)
        ControllerType.IFW -> setOf(ControlLayer.INTENT_FIREWALL)
        ControllerType.IFW_PLUS_PM -> setOf(ControlLayer.PACKAGE_MANAGER, ControlLayer.INTENT_FIREWALL)
    }
