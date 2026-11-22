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

package com.merxury.blocker.core.model.component

import com.merxury.blocker.core.model.ComponentType

/**
 * What the app's manifest says about a component. Fixed until the app is updated.
 *
 * @property enabledInManifest the `android:enabled` value, which applies while the
 * PackageManager setting is [PmSetting.DEFAULT]
 */
data class ComponentDeclaration(
    val id: ComponentId,
    val type: ComponentType,
    val exported: Boolean,
    val enabledInManifest: Boolean,
)

/** Whether Intent Firewall rules can affect components of this type. Providers are not filtered. */
val ComponentType.isFilteredByIntentFirewall: Boolean
    get() = this != ComponentType.PROVIDER

/** The two independent mechanisms that can stop a component. */
enum class ControlLayer {
    /** The per-user enabled setting kept by PackageManager. */
    PACKAGE_MANAGER,

    /** Device-wide Intent Firewall rules. */
    INTENT_FIREWALL,
}

/**
 * The enabled setting PackageManager holds for a component and user.
 *
 * PackageManager stores only these three values for components; the other disabled states exist
 * for whole applications.
 */
enum class PmSetting {
    /** No override; the manifest's `android:enabled` decides. */
    DEFAULT,
    ENABLED,
    DISABLED,

    /** The setting could not be read, for example because the package is gone for that user. */
    UNKNOWN,
}

/** What the Intent Firewall rules on the device say about a component, for all users. */
enum class IfwBlock {
    /** No rule blocks the component by name. */
    NOT_BLOCKED,

    /** A rule blocks the component by name with no further condition. */
    BLOCKED,

    /**
     * A blocking rule names the component but also carries conditions, such as a sender filter,
     * so whether a start is blocked depends on the intent and its sender.
     */
    CONDITIONAL,

    /** The rules could not be read, for example without root or when the rule file is damaged. */
    UNKNOWN,
}

/**
 * The control state of a component for one user.
 *
 * [pm] belongs to the user of the [ComponentKey] it was read for. [ifw] belongs to the device.
 * Either can be unknown, and unknown is never treated as "not blocked".
 */
data class ControlState(
    val pm: PmSetting,
    val ifw: IfwBlock,
) {
    companion object {
        val UNKNOWN = ControlState(PmSetting.UNKNOWN, IfwBlock.UNKNOWN)
    }
}

/** Why a component cannot start. */
enum class BlockReason {
    /** PackageManager has it disabled for the user. */
    PM_SETTING,

    /** An unconditional Intent Firewall rule blocks it. */
    IFW_RULE,

    /** The app ships it disabled and nothing has enabled it. */
    MANIFEST_DEFAULT,
}

/** Whether a component can be started, judged from its declaration and control state alone. */
sealed interface Availability {
    data object Runnable : Availability

    /** @property reasons every known reason; never empty */
    data class Blocked(val reasons: Set<BlockReason>) : Availability {
        init {
            require(reasons.isNotEmpty()) { "A blocked component needs a reason" }
        }
    }

    /** Not blocked outright, but a conditional Intent Firewall rule may stop some starts. */
    data object Conditional : Availability

    /** Nothing known blocks it, but part of its state could not be read. */
    data object Unknown : Availability
}

/**
 * Decides whether this component can start under [state].
 *
 * A known block wins over missing information: a component PackageManager has disabled is blocked
 * even when the firewall rules cannot be read. Without a known block, any unread part makes the
 * answer [Availability.Unknown]. The state of the app as a whole (disabled, stopped, or not
 * installed for the user) is not considered here.
 */
fun ComponentDeclaration.availability(state: ControlState): Availability {
    val ifw = if (type.isFilteredByIntentFirewall) state.ifw else IfwBlock.NOT_BLOCKED
    val reasons = buildSet {
        when (state.pm) {
            PmSetting.DISABLED -> add(BlockReason.PM_SETTING)
            PmSetting.DEFAULT -> if (!enabledInManifest) add(BlockReason.MANIFEST_DEFAULT)
            PmSetting.ENABLED, PmSetting.UNKNOWN -> Unit
        }
        if (ifw == IfwBlock.BLOCKED) add(BlockReason.IFW_RULE)
    }
    return when {
        reasons.isNotEmpty() -> Availability.Blocked(reasons)
        state.pm == PmSetting.UNKNOWN || ifw == IfwBlock.UNKNOWN -> Availability.Unknown
        ifw == IfwBlock.CONDITIONAL -> Availability.Conditional
        else -> Availability.Runnable
    }
}
