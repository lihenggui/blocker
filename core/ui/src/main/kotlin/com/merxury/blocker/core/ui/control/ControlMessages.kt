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

import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlResult

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
    ) : ControlMessage

    /** A batch ended; [notChanged] components did not reach its goal, none when all did. */
    data class BatchDone(
        val notChanged: Int,
        override val needsAccess: Boolean = false,
    ) : ControlMessage
}

/**
 * The message for one component's [this] outcome, or null when it reached its goal. A change that
 * reached only some layers did not reach the goal either.
 */
fun ComponentOutcome.toMessage(goal: ControlGoal): ControlMessage? {
    if (!needsMessage()) return null
    return ControlMessage.ComponentNotChanged(name = key.id.simpleName, goal = goal, needsAccess = needsAccess())
}

/**
 * The summary of a batch of [outcomes].
 *
 * @param vanished components chosen for the batch that were no longer cached when it was sent, so
 * nothing was sent for them; they count as not changed
 */
fun batchSummary(outcomes: List<ComponentOutcome>, vanished: Collection<ComponentKey> = emptyList()): ControlMessage.BatchDone {
    val notChanged = outcomes.filter { it.needsMessage() }
    return ControlMessage.BatchDone(notChanged = notChanged.size + vanished.size, needsAccess = notChanged.any { it.needsAccess() })
}

private fun ComponentOutcome.needsAccess(): Boolean = failures.values.any { it == ControlFailure.ChannelUnavailable }

private fun ComponentOutcome.needsMessage(): Boolean = result == ControlResult.FAILED || result == ControlResult.PARTIAL
