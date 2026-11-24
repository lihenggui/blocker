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

import android.os.Process
import com.merxury.blocker.core.database.record.PmBlockRecordDao
import com.merxury.blocker.core.database.record.PmBlockRecordEntity
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.layersFor
import timber.log.Timber
import javax.inject.Inject

// Android gives each user a range of this many uids.
private const val UIDS_PER_USER = 100_000

/** The user Blocker runs in. */
internal fun ownUser(): UserId = UserId(Process.myUid() / UIDS_PER_USER)

/**
 * The record of components Blocker disabled through PackageManager, per user. The system does not
 * say who disabled a component, so this record is what tells Blocker's blocks apart from those of
 * the app itself or of the system, and what a reset of the PackageManager settings undoes.
 *
 * ## Who writes
 * - [afterControl], for each package a control finishes, under that package's lock right after its
 *   read-back is stored: a block through PackageManager records what reads back DISABLED, and any
 *   request drops what reads back as not disabled.
 * - [afterRefresh], for each package refresh under the same lock: drops what reads back as not
 *   disabled, which the user or the app changed elsewhere, and what the app no longer declares.
 * - [forgetApp] and [forgetUninstalled] when a live read confirms the app is gone for the user,
 *   including records left behind by earlier removals.
 * - The one-time seed from the component list of earlier versions, see [PmBlockRecordSeeder], and
 *   while old screens still change components through the old controllers, the hook in the old
 *   component repository. Both add and drop records through [record] and [forget].
 *
 * A setting that cannot be read, [PmSetting.UNKNOWN], never drops a record.
 */
internal class PmBlockRecords @Inject constructor(
    private val dao: PmBlockRecordDao,
) {
    /** The recorded components of [user], by package and class name. */
    suspend fun of(user: UserId): List<ComponentKey> = dao.getByUser(user.value).map { it.asKey() }

    /** Records [keys] as disabled by Blocker now; a key recorded already keeps its time. */
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

    suspend fun forgetUninstalled(user: UserId, installed: Set<String>) {
        forget(of(user).filter { it.id.packageName !in installed })
    }

    /**
     * Updates the record from the state [request] read back for one package: under a
     * [ControlGoal.BLOCK] whose layers for a component include PackageManager, a component that
     * reads back DISABLED is recorded; under any goal, one that reads back DEFAULT or ENABLED is
     * dropped. A provider blocked with the firewall layer only is blocked through PackageManager,
     * so it is recorded too.
     */
    suspend fun afterControl(request: ControlRequest, event: ControlEvent.PackageDone) {
        val declarations = request.components.associateBy { it.id }
        val (disabled, cleared) = event.outcomes.filter { it.state.pm != PmSetting.UNKNOWN }.partition { it.state.pm == PmSetting.DISABLED }
        val recorded = disabled.filter { outcome ->
            request.goal == ControlGoal.BLOCK && declarations[outcome.key.id]?.layersFor(request.layers)?.contains(ControlLayer.PACKAGE_MANAGER) == true
        }
        record(recorded.map { it.key })
        forget(cleared.map { it.key })
    }

    /**
     * Updates the record of [app] from what one refresh read: drops the components whose setting
     * read DEFAULT or ENABLED and, when the declarations are current, those the app does not
     * declare. Nothing is dropped when the settings could not be read; an app removed meanwhile
     * loses all its records.
     */
    suspend fun afterRefresh(app: AppKey, refresh: PackageRefresh) {
        if (refresh.removed) {
            forgetApp(app)
            return
        }
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
