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

package com.merxury.blocker.core.data.device

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.flow.Flow

/**
 * The components of installed apps with their control state per user, as held by the component
 * cache, and the only way to change that state.
 *
 * ## Who writes
 * Component state in the cache is written only here: by [refresh] and [refreshAll] from reads of
 * the system, by [control] from the state read back after each package's change, and by
 * re-reading a package's Intent Firewall rules whenever the rule store reports a change to them,
 * such as a save in the rule editor or an import. Every write for a package runs under that
 * package's lock in this repository, so a refresh and a control of the same package never
 * interleave: a refresh holding the lock when a control's result arrives stores its read first,
 * and the control's read-back, taken after the change, is stored after it.
 *
 * ## Unknown state
 * A part that could not be read is stored as [PmSetting.UNKNOWN] or [IfwBlock.UNKNOWN], never left
 * as it was and never stored as a default. A part never read reads as unknown too.
 *
 * Reads never connect the privileged channel, so only [control] can show a prompt. No method
 * throws except for cancellation.
 */
interface ComponentStatusRepository {
    /**
     * Whether the privileged channel can reach the Intent Firewall rules: it is ready with an
     * identity that can write system files. While false, every filtered component's firewall
     * state is [IfwBlock.UNKNOWN] by design and refreshes do not report that as a failure; the
     * UI uses this to explain why the firewall state is unknown.
     */
    val firewallReadable: Flow<Boolean>

    /**
     * The components of [app] with that user's state, by class name. Empty until declarations are
     * read, and empty while the app is not cached as installed for that user, even when another
     * user's copy keeps the shared declarations cached.
     */
    fun components(app: AppKey): Flow<List<ComponentStatus>>

    /**
     * One component with its user's state, or null while its app is not cached as installed for
     * that user or does not declare it.
     */
    fun component(key: ComponentKey): Flow<ComponentStatus?>

    /** Every component of the apps cached for [user], by package and class name. */
    fun allComponents(user: UserId): Flow<List<ComponentStatus>>

    /**
     * The components of the apps cached for [user] whose class name contains [keyword], ignoring
     * ASCII case. `%` and `_` match themselves.
     */
    fun search(user: UserId, keyword: String): Flow<List<ComponentStatus>>

    /**
     * Reads [app] and stores its cached row, then reads and stores three parts in this order:
     * the declarations, only when they have not been read for the app's current `lastUpdateTime`;
     * the PackageManager settings of every component for that user, in one call; and the Intent
     * Firewall state of every component. A part that cannot be read is stored as unknown, the
     * others are still stored. Declarations that cannot be re-read are kept, with their old scan
     * marker, so the next refresh tries again.
     *
     * @return success when every part was read. Otherwise the first failure in the order app,
     * declarations, settings, firewall, with every part that was read already stored. Firewall
     * state is [ReadFailure.RulesUnreadable] only when the channel could reach the rules and they
     * still could not be read, such as a damaged file; without that access it is stored as
     * unknown and not reported, see [firewallReadable]. When the app is not installed
     * for that user it is removed from the cache and the result is [ReadFailure.PackageNotFound].
     */
    suspend fun refresh(app: AppKey): ReadResult<Unit>

    /**
     * Refreshes the apps of [user], then the components of each app as [refresh] does, a few
     * apps at a time. A failed app does not stop the others; cancelling the collection stops
     * between reads, and a package whose writes have started finishes them. Emits
     * [RefreshProgress.Refreshing] once the app list is read and after each app, and ends with
     * [RefreshProgress.Finished], which a collector that cancels never sees.
     */
    fun refreshAll(user: UserId): Flow<RefreshProgress>

    /**
     * A cold flow that carries out [request] through ComponentControl, which connects the
     * privileged channel when needed and may show the su or Shizuku prompt. For every
     * [ControlEvent.PackageDone] the state read back for each outcome is stored before the event
     * is emitted, so an observer of [components] sees the system's state without guessing;
     * outcomes of components the cache does not declare are not stored. A package whose event has
     * arrived is stored even when the collector cancels.
     */
    fun control(request: ControlRequest): Flow<ControlEvent>

    /**
     * The components whose PackageManager setting is [PmSetting.DISABLED] for [user], read from the
     * system for every app installed for that user rather than taken from the cache. Refreshes the
     * apps and their components as [refreshAll] does on the way.
     *
     * @return the components by package and class name, or the first failure that kept an app's
     * settings from being read; an app removed meanwhile is skipped, not a failure
     */
    suspend fun disabledByPackageManager(user: UserId): ReadResult<List<ComponentKey>>
}

/** Progress of [ComponentStatusRepository.refreshAll]. */
sealed interface RefreshProgress {
    /**
     * [done] of [total] apps are refreshed.
     * @property current the app refreshed last, or null before the first one finishes. Several
     * apps are refreshed at once, so this is the latest to finish, not the only one in progress.
     */
    data class Refreshing(
        val done: Int,
        val total: Int,
        val current: String?,
    ) : RefreshProgress

    /**
     * The last event.
     * @property failed apps whose refresh returned a failure, apps removed meanwhile not counted
     * @property appListFailure why the user's apps could not be read; then no app was refreshed and
     * [total] is 0
     */
    data class Finished(
        val total: Int,
        val failed: Int,
        val appListFailure: ReadFailure?,
    ) : RefreshProgress
}
