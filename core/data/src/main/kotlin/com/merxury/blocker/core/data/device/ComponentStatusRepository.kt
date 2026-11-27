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

import com.merxury.blocker.core.data.history.OperationScope
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ComponentWriteObserver
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
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
 * The record of components Blocker disabled through PackageManager is user data kept apart from
 * the cache; [control], [refresh] and app removal keep it, see [disabledByBlocker].
 *
 * ## Unknown state
 * Explicit refreshes store unreadable parts as [PmSetting.UNKNOWN] or [IfwBlock.UNKNOWN].
 * Automatic refreshes preserve cached state on failed reads. Parts never read are unknown.
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
     * case as Android's SQLite `LIKE` does, which folds letters beyond ASCII too. `%` and `_`
     * match themselves.
     */
    fun search(user: UserId, keyword: String): Flow<List<ComponentStatus>>

    /**
     * Reads [app] and stores its cached row, then reads and stores three parts in this order:
     * the live declarations, even when the app's `lastUpdateTime` matches the cached scan;
     * the PackageManager settings of every component for that user, in one call; and the Intent
     * Firewall state of every component. A part that cannot be read is stored as unknown, the
     * others are still stored. Declarations that cannot be re-read are kept, with their old scan
     * marker, so the next refresh tries again. A smaller list for the same version is accepted
     * only from a user known to be running and unlocked. Records of what Blocker disabled in the
     * app for that user are dropped for components whose setting read DEFAULT or ENABLED, as the
     * user or the app re-enabled them elsewhere, and, when the declarations are current, for
     * components the app no longer declares; all of them when the app is gone.
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
     * Re-reads the app using the automatic refresh policy. Failed reads preserve the cached
     * state, and confirmed removals evict it.
     */
    suspend fun refreshIfReadable(app: AppKey, firewall: FirewallRead): ReadResult<Unit>

    /**
     * Refreshes the apps of [user], then the components of each app a few apps at a time. It can
     * reuse declarations already scanned for the current revision. A failed app does not stop
     * the others. Cancelling the collection stops between reads, and a package whose writes have
     * started finishes them. Emits
     * [RefreshProgress.Refreshing] once the app list is read and after each app, and ends with
     * [RefreshProgress.Finished], which a collector that cancels never sees.
     */
    fun refreshAll(user: UserId): Flow<RefreshProgress>

    /** Whether [control] skips block requests for [app] to protect the running copy of Blocker. */
    fun isProtectedFromBlocking(app: AppKey): Boolean

    /**
     * A cold flow that carries out [request] through ComponentControl, which connects the
     * privileged channel when needed and may show the su or Shizuku prompt. For every
     * [ControlEvent.PackageDone] the state read back for each outcome is stored before the event
     * is emitted, so an observer of [components] sees the system's state without guessing;
     * outcomes of components the cache does not declare are not stored. A package whose event has
     * arrived is stored even when the collector cancels. Pass the same [operation] for every
     * request made by one action.
     *
     * For a [ControlGoal.BLOCK] that will send a [ControlLayer.PACKAGE_MANAGER] disable, Blocker
     * journals the component before the system call. Under the same package lock, read-back
     * prunes records whose setting is [PmSetting.DEFAULT] or [PmSetting.ENABLED] and keeps
     * records whose setting is [PmSetting.DISABLED] or [PmSetting.UNKNOWN]. A later refresh
     * can prune journal entries left by an interrupted write. An already disabled component
     * acquires a record only when Blocker verifies that its wake protection owns the disable.
     *
     * A [ControlGoal.BLOCK] never reaches Blocker's own components in the user Blocker runs in:
     * they return the cached state with [ControlResult.SKIPPED] and
     * [ControlFailure.ProtectedComponent], and count as skipped in the last event.
     * In another user the firewall layer is protected too, while PackageManager can still
     * block that user's copy.
     */
    fun control(request: ControlRequest, operation: OperationScope): Flow<ControlEvent> = control(request, keepKnownState = false, operation = operation)

    /**
     * Runs [control], optionally keeping the cached value of a layer whose read-back failed.
     * [writeObserver] can decline to start a package's writes and receives acknowledgments as
     * writes complete.
     */
    fun control(request: ControlRequest, keepKnownState: Boolean, writeObserver: ComponentWriteObserver? = null, operation: OperationScope): Flow<ControlEvent>

    /**
     * Restores the layers recorded in a component backup, with PackageManager for providers.
     * Uses the same protection, journaling and read-back as [control].
     */
    fun restore(request: ControlRequest, operation: OperationScope): Flow<ControlEvent>

    /**
     * Clears PackageManager blocks found by [disabledByBlocker]. Like [control], stores the
     * read-back and updates the records before emitting the result. Also drops records for
     * changes the system refused even under root or the system UID.
     */
    fun resetPmBlocks(user: UserId, components: List<ComponentDeclaration>, operation: OperationScope): Flow<ControlEvent>

    /**
     * The components Blocker disabled through PackageManager for [user] that are still
     * [PmSetting.DISABLED]. Refreshes the user's app list and each app that has recorded components,
     * as [refresh] does, dropping records of components no longer disabled or declared. An app
     * whose settings cannot be read is listed in [DisabledComponents.unreadableApps]. A hidden
     * app confirmed to be installed is left out and keeps its records for a later reset.
     *
     * @return what was found, or the failure that kept the user's app list from being read
     */
    suspend fun disabledByBlocker(user: UserId): ReadResult<DisabledComponents>
}

/** How to read the Intent Firewall layer of a package refresh. */
enum class FirewallRead {
    /** Use the app's own PackageManager for its user and keep the cached firewall state. */
    KEEP_CACHED,

    /** Consult the rule store, allowing unavailable firewall access. */
    IF_AVAILABLE,

    /** Consult the rule store and fail if the rules cannot be read. */
    REQUIRED,

    /** Read the rule files again for an explicit refresh. */
    FORCE,
}

/**
 * What [ComponentStatusRepository.disabledByBlocker] found.
 *
 * @property components by package and class name
 * @property unreadableApps apps whose settings or installation could not be verified, by package
 * name. Their recorded components are left out of [components] and keep their records.
 * @property notInstalled recorded components of apps no longer installed for the user; their
 * records are dropped
 * @property notDeclared recorded components the installed app does not declare
 */
data class DisabledComponents(
    val components: List<ComponentKey>,
    val unreadableApps: List<AppKey>,
    val notInstalled: Int = 0,
    val notDeclared: Int = 0,
)

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
