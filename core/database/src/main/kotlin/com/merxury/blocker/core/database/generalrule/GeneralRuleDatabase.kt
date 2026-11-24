/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.database.generalrule

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import com.merxury.blocker.core.database.util.ListConverter

@Database(
    entities = [GeneralRuleEntity::class],
    version = 4,
    autoMigrations = [AutoMigration(from = 3, to = 4, spec = RemoveMatchedAppCount::class)],
)
@TypeConverters(ListConverter::class)
internal abstract class GeneralRuleDatabase : RoomDatabase() {
    abstract fun generalRuleDao(): GeneralRuleDao
}

@DeleteColumn(tableName = "general_rules", columnName = "matchedAppCount")
internal class RemoveMatchedAppCount : AutoMigrationSpec
