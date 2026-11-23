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

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.Availability
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import kotlinx.coroutines.flow.Flow

/**
 * Blocks and unblocks components through PackageManager settings and Intent Firewall rules, and
 * reports what the system holds afterwards.
 *
 * ## What a request does
 * [ControlGoal.BLOCK] changes only the requested layers that apply to a component: the
 * PackageManager setting becomes [PmSetting.DISABLED], and the Intent Firewall gets an
 * unconditional rule for it. A provider is not filtered by the firewall, so where the firewall was
 * asked for, its PackageManager setting is changed instead.
 *
 * [ControlGoal.UNBLOCK] removes the blocks of the requested layers and nothing more, so "reset
 * the PackageManager settings" and "reset the firewall rules" stay separate. Clearing the
 * PackageManager layer turns a [PmSetting.DISABLED] setting into [PmSetting.DEFAULT] and leaves
 * any other setting alone, so a component the manifest ships disabled stays at
 * [PmSetting.DEFAULT] and is still not runnable. Clearing the firewall layer removes the
 * component from the unconditional firewall rules. A conditional rule is not Blocker's to remove;
 * when only that is left, the firewall layer counts as clear. As for a block, a provider asked to
 * clear the firewall layer clears its PackageManager setting instead.
 *
 * [ControlGoal.ENABLE] makes the component runnable through the requested layers: it does what
 * an unblock does, and where the PackageManager layer is requested for a component the manifest
 * ships disabled it sets [PmSetting.ENABLED], also from [PmSetting.DEFAULT]. To make a component
 * runnable, request every layer ([ControlLayer.entries]).
 *
 * ## How the outcome is decided
 * Each package's state is read first. PackageManager calls are sent only for components whose
 * setting does not already meet the goal: one call per package and target setting where the
 * platform can change many components in one call (Android 13 and later). A refused batch call is
 * repeated one component at a time, so a protected component fails alone. Every component whose
 * goal involves the Intent Firewall goes to the rule store in one change per package; the store
 * re-reads the file under its lock and writes nothing when the rules stay as they are, so it alone
 * decides whether the firewall needs a change. The outcome comes from the state read back
 * afterwards, never from what a call answered. A block is [ControlResult.ACHIEVED] when every
 * layer it uses shows the component blocked, [ControlResult.PARTIAL] when some do, and
 * [ControlResult.FAILED] when none does. An unblock is achieved when no layer it uses blocks the
 * component any more: the setting is not [PmSetting.DISABLED] and no unconditional rule names it,
 * whatever the manifest default or a conditional rule say and even if a layer it was not asked to
 * clear still blocks; the state reported then shows that layer as it is. An enable is achieved
 * when every layer it uses lets the component run, so with every layer requested achieved means
 * [Availability.Runnable] or [Availability.Conditional]. An unblock or enable is partial when a
 * layer that did not meet the goal before does now but another requested layer does not or
 * cannot be read, and failed when no requested layer moved toward the goal. A layer whose call
 * succeeded but whose state
 * does not show it fails with [ControlFailure.StateNotConfirmed]. A partial result is kept, not
 * rolled back.
 *
 * Without a ready channel nothing is sent and the layers fail with
 * [ControlFailure.ChannelUnavailable]. An identity that may not change components, such as Shizuku
 * running as the shell, gets [ControlFailure.NotPermitted] for the PackageManager layer without a
 * call, and one that may not write system files gets it for the firewall layer without a call.
 *
 * ## Order and cancellation
 * The work on one package holds that package's lock, shared by every request in the process,
 * from its first read to its read-back, so requests touching the same package never interleave.
 * A request locks one package at a time, in its own order. A package whose changes have been sent
 * runs to its read-back even when the collector cancels; no further package is started.
 *
 * The Intent Firewall state reported comes from the rule store, which answers from rules it read
 * earlier and may miss a change another writer made to a file it has not written itself; after a
 * change through the store it reflects the file the store just read or wrote.
 */
interface ComponentControl {
    /**
     * The current state of [components] of [app]: the PackageManager setting for the user of
     * [app], read from the system, and the Intent Firewall state from the rule store. What cannot
     * be read is [PmSetting.UNKNOWN] or [IfwBlock.UNKNOWN], and so is the firewall state of a
     * component of another package. Never connects the privileged channel, so it never shows a
     * prompt. Never throws except for cancellation.
     *
     * @return an entry for every component
     */
    suspend fun state(app: AppKey, components: Collection<ComponentDeclaration>): Map<ComponentId, ControlState>

    /**
     * A cold flow that carries out [request] when collected. It connects the privileged channel
     * first when it is not ready, which may show the su or Shizuku prompt. It works package by
     * package, emits [ControlEvent.PackageDone] for each package, also when its changes failed,
     * and finally [ControlEvent.Finished]. Every requested component appears in exactly one
     * outcome, including those that needed no change. A failure never stops the remaining
     * packages, and the flow never throws except for cancellation.
     *
     * The [ControlLayer]s to set or clear come from the request for every goal; see [ComponentControl].
     */
    fun apply(request: ControlRequest): Flow<ControlEvent>
}
