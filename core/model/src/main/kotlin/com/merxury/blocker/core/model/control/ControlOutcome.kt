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

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity

/** Why one layer of a component does not show what was asked for. */
sealed interface ControlFailure {
    /** No root, Shizuku not running or not granted, or the privileged process died. */
    data object ChannelUnavailable : ControlFailure

    /** The identity the channel runs as may not do this, or the system protects the component. */
    data class NotPermitted(val message: String?, val identity: PrivilegedIdentity? = null) : ControlFailure {
        /** Whether the system refused the change even with a root or system identity. */
        val isSystemRefusal: Boolean get() = identity == PrivilegedIdentity.ROOT || identity == PrivilegedIdentity.SYSTEM
    }

    /** Blocker protects its own components. */
    data object ProtectedComponent : ControlFailure

    /** An Intent Firewall block would also block the copy of Blocker in use. */
    data object FirewallProtectedComponent : ControlFailure

    /** The package, component or user is not known to the system. */
    data class TargetNotFound(val message: String?) : ControlFailure

    /** The package's Intent Firewall rule file exists but cannot be read, so it is left alone. */
    data object RulesUnreadable : ControlFailure

    /** Writing the package's Intent Firewall rule file failed. */
    data class WriteFailed(val message: String?) : ControlFailure

    /** The system failed in another way. */
    data class SystemError(val message: String?) : ControlFailure

    /** The starting state could not be verified, or the state read back did not confirm the goal. */
    data object StateNotConfirmed : ControlFailure
}

/** How far a component got towards the goal of its request, judged from its state read back. */
enum class ControlResult {
    ACHIEVED,

    /** Some layers show the goal and others do not. Nothing is rolled back. */
    PARTIAL,
    FAILED,

    /** Blocker skipped the requested change to protect its own components. */
    SKIPPED,
}

/**
 * What a request did to one component.
 *
 * @property state as read back after the change, or before it when nothing had to change
 * @property failures why each reported layer failed or was skipped. Empty exactly when [result]
 * is [ControlResult.ACHIEVED].
 */
data class ComponentOutcome(
    val key: ComponentKey,
    val state: ControlState,
    val result: ControlResult,
    val failures: Map<ControlLayer, ControlFailure>,
) {
    init {
        require((result == ControlResult.ACHIEVED) == failures.isEmpty()) { "$result with failures $failures for $key" }
    }
}

/** Observes the writes of one control call. */
interface ComponentWriteObserver {
    /** The live settings used to plan the request, read under the package lock. */
    fun onPmRead(settings: Map<ComponentId, PmSetting>) = Unit

    /** Selects components still owned by a guarded PM restore, under the package lock. */
    suspend fun selectPmRestoreComponents(app: AppKey, components: Set<ComponentId>): Set<ComponentId> = components

    /** Checked after the package has been read and immediately before its first write. */
    fun canStart(): Boolean

    /** The IDs acknowledged by the system for a single completed write. */
    fun onWrite(components: Collection<ComponentId>)
}

/** Progress of a request, package by package. */
sealed interface ControlEvent {
    /** Every requested component of [packageName], each once. */
    data class PackageDone(
        val packageName: String,
        val outcomes: List<ComponentOutcome>,
        val pmWrites: Set<ComponentId> = emptySet(),
    ) : ControlEvent

    /**
     * The last event: how many requested components ended in each [ControlResult]. A collector
     * that cancels never sees it.
     */
    data class Finished(
        val achieved: Int,
        val partial: Int,
        val failed: Int,
        val skipped: Int = 0,
    ) : ControlEvent
}
