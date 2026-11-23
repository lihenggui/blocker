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

import com.merxury.blocker.core.database.app.AppComponentDao
import com.merxury.blocker.core.datastore.BlockerAppPropertiesDataStore
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds, once per install, the record of components Blocker disabled through PackageManager from
 * the component list earlier versions kept, for users who upgrade. Runs at start-up and again
 * before every reset of the PackageManager settings, whichever comes first; later calls do nothing.
 */
interface PmBlockRecordSeeder {
    /**
     * Seeds the record unless it was seeded before. Never throws except for cancellation; a seed
     * that fails is logged and tried again by the next call.
     */
    suspend fun seedOnce()
}

/**
 * [PmBlockRecordSeeder] over the old component list, whose `pm_blocked` flag is all that earlier
 * versions knew of their own blocks. That flag only says a component was seen not enabled, so
 * besides Blocker's blocks it holds components the app or the system disabled itself, of system
 * apps too, which the reset of earlier versions cleared as well. The seed takes every flagged
 * component of the user Blocker runs in, the only user that list held, on purpose: the reset is
 * the way back for a user who disabled components through an earlier version, system components
 * included, and the record cannot tell those apart from the rest. So the first reset after an
 * upgrade is as broad as the reset of earlier versions, once; the record is exact from then on, as
 * that reset drops what it cleared and refreshes drop what is no longer disabled. The reset clears
 * only what is still DISABLED when it runs, so a flag the system has meanwhile cleared costs nothing.
 *
 * When the old list was never filled ([BlockerAppPropertiesDataStore] says the component database
 * was never initialised), this is a new install with no earlier blocks: nothing is seeded, and
 * the seed is marked done all the same, so the list the first scan fills later, which holds only
 * what the system and the apps disabled, is never taken for Blocker's blocks.
 */
@Singleton
internal class LegacyListPmBlockRecordSeeder @Inject constructor(
    private val legacyComponents: AppComponentDao,
    private val properties: BlockerAppPropertiesDataStore,
    private val records: PmBlockRecords,
) : PmBlockRecordSeeder {
    private val mutex = Mutex()

    override suspend fun seedOnce() = mutex.withLock {
        try {
            if (properties.pmBlockRecordsSeeded.first()) return@withLock
            if (properties.appPropertiesData.first().componentDatabaseInitialized) {
                val user = ownUser()
                val keys = legacyComponents.getPmBlockedComponents().map { ComponentKey(ComponentId(it.packageName, it.componentName), user) }
                records.record(keys)
                Timber.i("Seeded ${keys.size} PM blocks of user ${user.value} from the old component list")
            } else {
                Timber.i("The old component list was never filled, there are no PM blocks to seed")
            }
            properties.markPmBlockRecordsSeeded()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Cannot seed the PM blocks from the old component list, try again next time")
        }
    }
}
