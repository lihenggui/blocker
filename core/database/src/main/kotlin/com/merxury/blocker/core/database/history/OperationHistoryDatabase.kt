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

package com.merxury.blocker.core.database.history

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.merxury.blocker.core.database.util.InstantConverter

internal const val OPERATION_HISTORY_DATABASE_NAME = "operation_history"

/**
 * Component state changes Blocker made, kept so the user can review and undo them.
 *
 * This is user data that cannot be rebuilt from the system, so it is never opened with destructive
 * migration: a schema change bumps the version, commits the exported schema, adds its migration to
 * [OPERATION_HISTORY_MIGRATIONS] and tests that the history survives it. Public so that tests of
 * the repositories over it can open an in-memory instance; production code gets
 * [OperationHistoryDao] from Hilt.
 */
@Database(
    entities = [OperationEntity::class, OperationEntryEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class OperationHistoryDatabase : RoomDatabase() {
    abstract fun operationHistoryDao(): OperationHistoryDao
}

/** Migrations in version order. Version 1 is the initial schema. */
internal val OPERATION_HISTORY_MIGRATIONS: Array<Migration> = emptyArray()
