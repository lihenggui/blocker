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

package com.merxury.blocker.core.database.debloater

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DebloatableComponentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg entities: DebloatableComponentEntity)

    @Update
    suspend fun update(entity: DebloatableComponentEntity): Int

    @Delete
    suspend fun delete(entity: DebloatableComponentEntity): Int

    @Delete
    suspend fun delete(entities: List<DebloatableComponentEntity>): Int

    @Query("DELETE FROM debloatable_component WHERE package_name LIKE :packageName")
    suspend fun deleteByPackageName(packageName: String): Int

    @Query("DELETE FROM debloatable_component")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM debloatable_component")
    fun getAll(): Flow<List<DebloatableComponentEntity>>

    @Query("SELECT * FROM debloatable_component WHERE package_name LIKE :packageName")
    fun getByPackageName(packageName: String): Flow<List<DebloatableComponentEntity>>

    @Query(
        "SELECT * FROM debloatable_component WHERE package_name LIKE :packageName " +
            "AND component_name LIKE :componentName",
    )
    fun getByPackageNameAndComponentName(
        packageName: String,
        componentName: String,
    ): Flow<DebloatableComponentEntity?>

    @Upsert
    suspend fun upsertAll(entities: List<DebloatableComponentEntity>)
}
