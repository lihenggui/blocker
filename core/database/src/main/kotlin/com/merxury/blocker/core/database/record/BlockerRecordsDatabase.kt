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

package com.merxury.blocker.core.database.record

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/** The file name of [BlockerRecordsDatabase]. */
internal const val BLOCKER_RECORDS_DATABASE_NAME = "blocker_records"

/**
 * What Blocker has done to the device that the system does not record: which components it
 * disabled through PackageManager, for each user.
 *
 * This is user data. Unlike the caches it cannot be rebuilt from the system, so it is never opened
 * with destructive migration: a schema change bumps the version, commits the exported schema, adds
 * its migration to [BLOCKER_RECORDS_MIGRATIONS] and tests that the records survive it. Public so
 * that tests of the repositories over it can open an in-memory instance; production code gets
 * [PmBlockRecordDao] from Hilt.
 */
@Database(
    entities = [PmBlockRecordEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BlockerRecordsDatabase : RoomDatabase() {
    abstract fun pmBlockRecordDao(): PmBlockRecordDao
}

/** Every migration of [BlockerRecordsDatabase], oldest first. Version 1 is the first schema, so there is none yet. */
val BLOCKER_RECORDS_MIGRATIONS: Array<Migration> = emptyArray()
