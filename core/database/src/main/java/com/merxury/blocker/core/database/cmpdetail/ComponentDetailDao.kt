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

package com.merxury.blocker.core.database.cmpdetail

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ComponentDetailDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComponentDetail(entity: ComponentDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComponentDetails(entities: List<ComponentDetailEntity>)

    @Delete
    suspend fun delete(entity: ComponentDetailEntity)

    @Update
    suspend fun update(entity: ComponentDetailEntity)

    @Query("SELECT * FROM component_detail WHERE name = :name")
    fun getComponentDetail(name: String): Flow<ComponentDetailEntity?>
}
