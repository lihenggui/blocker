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

package com.merxury.blocker.data.instantinfo

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.merxury.blocker.data.source.local.ListConverters

@Database(entities = [InstantComponentInfo::class], version = 1, exportSchema = true)
@TypeConverters(ListConverters::class)
abstract class InstantComponentInfoDatabase : RoomDatabase() {
    abstract fun instantComponentInfoDao(): InstantComponentInfoDao

    companion object {
        @Volatile
        private var instance: InstantComponentInfoDatabase? = null

        fun getInstance(context: Context): InstantComponentInfoDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): InstantComponentInfoDatabase {
            return Room.databaseBuilder(
                context,
                InstantComponentInfoDatabase::class.java,
                "instant_component_info"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
