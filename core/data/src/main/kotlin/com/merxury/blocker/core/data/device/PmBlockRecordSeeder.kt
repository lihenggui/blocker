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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.core.database.getStringOrNull
import com.merxury.blocker.core.database.wake.WakeHistoryDao
import com.merxury.blocker.core.datastore.BlockerAppPropertiesDataStore
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
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
 * Components the wake feature disabled and still claims are left out: they are not the user's
 * blocks, and the wake feature restores them itself when it lets go of them. It changes components
 * of the owner user only.
 */
@Singleton
internal class InstalledAppDbPmBlockRecordSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    private val properties: BlockerAppPropertiesDataStore,
    private val records: PmBlockRecords,
    private val wakeHistory: WakeHistoryDao,
) : PmBlockRecordSeeder {
    private val mutex = Mutex()

    override suspend fun seedOnce() {
        withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    val file = context.getDatabasePath(LEGACY_DATABASE)
                    if (!properties.pmBlockRecordsSeeded.first()) {
                        seed(file)
                        properties.markPmBlockRecordsSeeded()
                    }
                    // A restart after the flag was written or a failed deletion only retries cleanup.
                    SQLiteDatabase.deleteDatabase(file)
                    if (file.exists()) Timber.w("Cannot delete the old installed app database")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Cannot migrate the old installed app database, try again next time")
                }
            }
        }
    }

    private suspend fun seed(file: File) {
        val ids = readBlockedComponents(file)
        if (ids.isEmpty()) return

        val user = ownUser()
        val wakeClaims = if (user == UserId.SYSTEM) wakeClaims() else emptySet()
        val keys = ids.filterNot { it in wakeClaims }.map { ComponentKey(it, user) }
        records.record(keys)
        Timber.i("Seeded ${keys.size} PM blocks of user ${user.value} from the old component list")
    }

    // Released versions 1 and 2 have the same app_component schema. Keep the file if it cannot
    // be opened or if its table has changed. Only a confirmed missing file or table is empty.
    private fun readBlockedComponents(file: File): List<ComponentId> {
        if (!file.exists()) {
            if (file.parentFile?.list()?.contains(file.name) == false) return emptyList()
            try {
                Os.stat(file.path)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.ENOENT) return emptyList()
                throw e
            }
        }
        val flags = SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        val database = SQLiteDatabase.openDatabase(file.path, null, flags) { Timber.w("The old installed app database is corrupt") }
        database.use { database ->
            val hasTable = database.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'app_component'",
                null,
            ).use { it.moveToFirst() }
            if (!hasTable) return emptyList()

            val columns = database.rawQuery("PRAGMA table_info(app_component)", null).use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) put(cursor.getString(1), cursor.getString(2).uppercase())
                }
            }
            check(columns["package_name"] == "TEXT" && columns["component_name"] == "TEXT" && columns["pm_blocked"] == "INTEGER") {
                "Unexpected schema in the old component table"
            }

            return database.rawQuery(
                "SELECT package_name, component_name FROM app_component WHERE pm_blocked = 1",
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val packageName = checkNotNull(cursor.getStringOrNull(0))
                        val className = checkNotNull(cursor.getStringOrNull(1))
                        add(checkNotNull(ComponentId.unflatten("$packageName/$className")))
                    }
                }
            }
        }
    }

    /** The components the wake feature disabled through PackageManager, or is disabling, and claims. */
    private suspend fun wakeClaims(): Set<ComponentId> = wakeHistory.readProtections()
        .filter { it.method == "PM" && (it.configured || it.pendingChange) }
        .map { ComponentId(it.target, it.component) }
        .toSet()
    private companion object {
        const val LEGACY_DATABASE = "installed_app"
    }
}
