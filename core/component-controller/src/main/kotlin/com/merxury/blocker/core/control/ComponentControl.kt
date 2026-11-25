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
import com.merxury.blocker.core.model.control.ComponentWriteObserver
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.control.layersFor
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
 * An Intent Firewall-only request also blocks receivers through PackageManager, since the
 * firewall cannot stop implicit broadcasts to a single receiver.
 *
 * [ControlGoal.UNBLOCK] removes the blocks of the requested layers and nothing more, so "reset
 * the PackageManager settings" and "reset the firewall rules" stay separate. Clearing the
 * PackageManager layer turns a [PmSetting.DISABLED] setting into [PmSetting.DEFAULT] and leaves
 * any other setting alone, so a component the manifest ships disabled stays at
 * [PmSetting.DEFAULT] and is still not runnable. Clearing the firewall layer removes the
 * component from the unconditional firewall rules. A conditional rule is not Blocker's to remove;
 * when only that is left, the firewall layer counts as clear. As for a block, a provider asked to
 * clear the firewall layer clears its PackageManager setting instead.
 * An Intent Firewall-only request for a receiver clears both layers to remove earlier firewall
 * blocks as well.
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
 * repeated one component at a time, so a protected system component fails alone. Eligible
 * firewall changes go to the rule store in one batch per package. The store
 * re-reads the file under its lock and writes nothing when the rules stay as they are. The
 * controller excludes the firewall layer when blocking Blocker itself. A request with only
 * that layer is [ControlResult.SKIPPED] with [ControlFailure.FirewallProtectedComponent]. A protection
 * refusal from the rule store counts as a failed layer. Other outcomes are judged by the state
 * read back afterwards. A block is [ControlResult.ACHIEVED] when every
 * layer it uses shows the component blocked, [ControlResult.PARTIAL] when some do, and
 * [ControlResult.FAILED] when none does. An unblock is achieved when no layer it uses blocks the
 * component any more: the setting is not [PmSetting.DISABLED] and no unconditional rule names it,
 * whatever the manifest default or a conditional rule say and even if a layer it was not asked to
 * clear still blocks; the state reported then shows that layer as it is. An enable is achieved
 * when every layer it uses lets the component run, so with every layer requested achieved means
 * [Availability.Runnable] or [Availability.Conditional]. An unblock or enable is partial when a
 * layer that did not meet the goal before does now but another requested layer does not or
 * cannot be read, and failed when no requested layer moved toward the goal. A layer counts as moved
 * only when it was read before the change and now meets the goal: a setting that could not be
 * read may have met the goal already, so one that reads differently afterwards proves no progress.
 * A PackageManager disable requires a known starting setting so Blocker can journal its own
 * writes. When the starting setting cannot be confirmed or the read-back does not show the goal,
 * a layer without a more specific failure gets [ControlFailure.StateNotConfirmed]. A partial
 * result is kept and no change is rolled back.
 *
 * Without a ready channel nothing is sent and the layers fail with
 * [ControlFailure.ChannelUnavailable]. Shizuku running as the shell may change the components of
 * a test-only app only; the system decides, so the PackageManager call is sent and its refusal
 * becomes [ControlFailure.NotPermitted], once per package and setting rather than once per
 * component. An identity that may not write system files, such as the shell, can neither read nor
 * change the Intent Firewall: an unblock or enable that also asks for the PackageManager layer
 * leaves the firewall layer out and is judged on the PackageManager layer alone, while the state
 * reported still shows the firewall as [IfwBlock.UNKNOWN]; a block, or a request for the firewall
 * layer alone, gets [ControlFailure.NotPermitted] for it without a call.
 *
 * A step that throws unexpectedly fails only the layer and components it was changing, with
 * [ControlFailure.SystemError].
 *
 * ## Order and cancellation
 * The work on one package holds that package's lock, shared by every request in the process,
 * from its first read through persistence of its read-back, so requests touching the same package never interleave.
 * A request locks one package at a time, in its own order. A package whose changes have been sent
 * runs through persistence even when the collector cancels; no further package is started.
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
     * outcome, including those that needed no change. A failed system change leaves the remaining
     * packages eligible. Cancellation and errors from [persistence] propagate.
     *
     * [layersForComponent] resolves the requested layers for a component. Backup restoration
     * supplies the recorded layers.
     * [writeObserver] can decline to start a package's writes and receives acknowledgments
     * as writes complete, before read-back.
     */
    fun apply(
        request: ControlRequest,
        writeObserver: ComponentWriteObserver? = null,
        persistence: ComponentControlPersistence? = null,
        layersForComponent: (ComponentDeclaration) -> Set<ControlLayer> = { it.layersFor(request.layers, request.goal) },
    ): Flow<ControlEvent>
}

/** Persists each package under its control lock, before progress reaches the collector. */
interface ComponentControlPersistence {
    /** Journals PM disables before the system call, so a process restart can verify them. */
    suspend fun beforePmBlock(app: AppKey, components: Collection<ComponentId>)

    /**
     * Stores read-back and reconciles the journal. Runs to completion once writes begin.
     * [alreadyDisabled] holds requested PM blocks that already existed before this request.
     */
    suspend fun afterPackage(event: ControlEvent.PackageDone, alreadyDisabled: Set<ComponentId>)
}
