/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.data.source.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.merxury.blocker.core.database.converter.ListConverter
import com.merxury.blocker.data.source.GeneralRule

@Database(entities = [GeneralRule::class], version = 1)
@TypeConverters(ListConverter::class)
abstract class GeneralRuleDatabase : RoomDatabase() {
    abstract fun generalRuleDao(): GeneralRuleDao

    companion object {
        @Volatile private var instance: GeneralRuleDatabase? = null

        fun getInstance(context: Context): GeneralRuleDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): GeneralRuleDatabase {
            return Room.databaseBuilder(context, GeneralRuleDatabase::class.java, "general_rule")
                .fallbackToDestructiveMigration().build()
        }
    }
}
