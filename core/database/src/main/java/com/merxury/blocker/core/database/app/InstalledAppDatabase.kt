/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.core.database.app

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.merxury.blocker.core.database.util.InstantConverter

@Database(entities = [InstalledAppEntity::class, AppComponentEntity::class], version = 2)
@TypeConverters(InstantConverter::class)
abstract class InstalledAppDatabase : RoomDatabase() {
    abstract fun installedAppDao(): InstalledAppDao
    abstract fun appComponentDao(): AppComponentDao
}
