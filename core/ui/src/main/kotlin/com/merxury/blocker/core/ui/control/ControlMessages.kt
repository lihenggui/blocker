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

import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity

/**
 * How a change of components ended, as the user is told: whether it worked, and missing access
 * when that is what the user can do something about. The outcomes keep every detail; the user
 * only needs to know whether the component is disabled.
 */
sealed interface ControlMessage {
    /**
     * Whether granting root or Shizuku access would help: some change failed because there was
     * none, so the message offers to grant it.
     */
    val needsAccess: Boolean

    /** One component, called [name], did not reach [goal]. */
    data class ComponentNotChanged(
        val name: String,
        val goal: ControlGoal,
        override val needsAccess: Boolean = false,
        val needsRoot: Boolean = false,
    ) : ControlMessage

    data class ComponentPartlyChanged(
        val name: String,
        override val needsAccess: Boolean,
        val needsRoot: Boolean,
    ) : ControlMessage

    data object FirewallSelfBlockRefused : ControlMessage {
        override val needsAccess = false
    }

    /** No components are available for a batch. */
    data object NoComponents : ControlMessage {
        override val needsAccess = false
    }

    /** A batch ended. [notChanged] counts failures and vanished components. */
    data class BatchDone(
        val notChanged: Int,
        override val needsAccess: Boolean = false,
        val partlyChanged: Int = 0,
        val needsRoot: Boolean = false,
        val firewallSelfBlockRefused: Boolean = false,
    ) : ControlMessage {
        val hasIssues: Boolean get() = notChanged > 0 || partlyChanged > 0 || firewallSelfBlockRefused
    }
}

/** The message for a component that did not fully reach its goal, or null if none is needed. */
fun ComponentOutcome?.toMessage(component: ComponentId, goal: ControlGoal): ControlMessage? = when (this?.result) {
    ControlResult.ACHIEVED -> null

    ControlResult.SKIPPED -> if (firewallSelfBlockRefused()) ControlMessage.FirewallSelfBlockRefused else null

    ControlResult.PARTIAL -> ControlMessage.ComponentPartlyChanged(component.simpleName, needsAccess(), needsRoot())

    ControlResult.FAILED, null -> ControlMessage.ComponentNotChanged(
        name = component.simpleName,
        goal = goal,
        needsAccess = this?.needsAccess() == true,
        needsRoot = this?.needsRoot() == true,
    )
}

/**
 * The summary of a batch of [outcomes].
 *
 * @param vanished components chosen for the batch that were no longer cached when it was sent, so
 * nothing was sent for them; they count as not changed
 */
fun batchSummary(outcomes: List<ComponentOutcome>, vanished: Collection<ComponentKey> = emptyList()): ControlMessage.BatchDone = ControlMessage.BatchDone(
    notChanged = outcomes.count { it.result == ControlResult.FAILED } + vanished.size,
    needsAccess = outcomes.any { it.needsAccess() },
    partlyChanged = outcomes.count { it.result == ControlResult.PARTIAL },
    needsRoot = outcomes.any { it.needsRoot() },
    firewallSelfBlockRefused = outcomes.any { it.firewallSelfBlockRefused() },
)

private fun ComponentOutcome.needsAccess(): Boolean = failures.values.any { it == ControlFailure.ChannelUnavailable }

private fun ComponentOutcome.needsRoot(): Boolean = failures.values.any { it is ControlFailure.NotPermitted && it.identity == PrivilegedIdentity.SHELL }

private fun ComponentOutcome.firewallSelfBlockRefused(): Boolean = failures.values.any { it == ControlFailure.FirewallProtectedComponent }
