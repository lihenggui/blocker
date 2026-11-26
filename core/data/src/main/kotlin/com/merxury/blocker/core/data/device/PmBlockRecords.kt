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

import com.merxury.blocker.core.database.record.PmBlockRecordDao
import com.merxury.blocker.core.database.record.PmBlockRecordEntity
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * The record of components Blocker disabled through PackageManager, per user. The system does not
 * say who disabled a component, so this record is what tells Blocker's blocks apart from those of
 * the app itself or of the system, and what a reset of the PackageManager settings undoes.
 *
 * ## Who writes
 * - Component control journals each intended PM disable before sending it under the package lock.
 * - [afterControl] prunes the journal from read-back under the same lock. A reset also drops
 *   blocks the system refuses to clear even under root or the system UID.
 * - Component control can take over an existing block verified as belonging to wake protection.
 * - Wake protection checks records and drops stale ones under the same lock before it changes PM.
 * - [afterRefresh], for each package refresh under the same lock: drops what reads back as not
 *   disabled, which the user or the app changed elsewhere, and what the app no longer declares.
 * - [forgetApp] when a live read confirms the app is gone for the user,
 *   including records left behind by earlier removals.
 * - [forgetUser] removes the records of a removed user.
 * - [PmBlockRecordSeeder] records blocks from the legacy component list once during upgrade.
 *
 * An unreadable setting alone never drops a record.
 */
class PmBlockRecords @Inject constructor(
    private val dao: PmBlockRecordDao,
) {
    /** Observes Blocker's PackageManager blocks of [user]. */
    internal fun observe(user: UserId) = dao.observeByUser(user.value).map { rows -> rows.map { it.asKey() }.toSet() }

    /** The recorded components of [user], by package and class name. */
    suspend fun of(user: UserId): List<ComponentKey> = dao.getByUser(user.value).map { it.asKey() }

    /** Journals [keys] as Blocker claims the PM block; a recorded key keeps its time. */
    suspend fun record(keys: Collection<ComponentKey>) {
        if (keys.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.insertOrIgnore(keys.map { PmBlockRecordEntity(it.id.packageName, it.id.className, it.userId.value, now) })
    }

    /** Drops the records of [keys]; keys not recorded are ignored. */
    suspend fun forget(keys: Collection<ComponentKey>) {
        if (keys.isEmpty()) return
        dao.delete(keys.map { PmBlockRecordEntity(it.id.packageName, it.id.className, it.userId.value, 0) })
    }

    /** Drops every record of [app]. */
    suspend fun forgetApp(app: AppKey) = dao.deleteByPackage(app.packageName, app.userId.value)

    suspend fun forgetUser(user: UserId) = dao.deleteByUser(user.value)

    /** Checks whether Blocker owns this block. Call under the package lock. */
    suspend fun contains(key: ComponentKey): Boolean = dao.getByPackage(key.id.packageName, key.userId.value).any { it.className == key.id.className }

    /** Drops confirmed clears, and permanently refused clears during Reset PM. */
    suspend fun afterControl(event: ControlEvent.PackageDone, forgetRefusedPmBlocks: Boolean) {
        val cleared = event.outcomes.filter { it.state.pm == PmSetting.DEFAULT || it.state.pm == PmSetting.ENABLED }
        val refused = if (forgetRefusedPmBlocks) {
            event.outcomes.filter { outcome ->
                val failure = outcome.failures[ControlLayer.PACKAGE_MANAGER]
                failure is ControlFailure.NotPermitted && failure.isSystemRefusal
            }.map { outcome ->
                Timber.w("Forget PM block ${outcome.key.id.flatten()} for user ${outcome.key.userId.value} after a system refusal")
                outcome.key
            }
        } else {
            emptyList()
        }
        forget(cleared.map { it.key } + refused)
    }

    /**
     * Updates the record of [app] from what one refresh read: drops the components whose setting
     * read DEFAULT or ENABLED and, when the declarations are current, those the app does not
     * declare. Nothing is dropped when the settings could not be read.
     */
    internal suspend fun afterRefresh(app: AppKey, refresh: PackageRefresh) {
        val settings = refresh.pmSettings ?: return
        if (refresh.pmFailure != null) return
        val gone = dao.getByPackage(app.packageName, app.userId.value).map { it.asKey() }.filter { key ->
            when (settings[key.id]) {
                null -> refresh.declarationsCurrent
                PmSetting.DEFAULT, PmSetting.ENABLED -> true
                PmSetting.DISABLED, PmSetting.UNKNOWN -> false
            }
        }
        if (gone.isNotEmpty()) Timber.i("Forget ${gone.size} PM blocks of $app that are no longer disabled or declared")
        forget(gone)
    }
}

private fun PmBlockRecordEntity.asKey() = ComponentKey(ComponentId(packageName, className), UserId(userId))
