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

package com.merxury.blocker.core.database.sharetarget

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ShareTargetActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg entities: ShareTargetActivityEntity)

    @Update
    suspend fun update(entity: ShareTargetActivityEntity): Int

    @Delete
    suspend fun delete(entity: ShareTargetActivityEntity): Int

    @Delete
    suspend fun delete(entities: List<ShareTargetActivityEntity>): Int

    @Query("DELETE FROM share_target_activity WHERE package_name LIKE :packageName")
    suspend fun deleteByPackageName(packageName: String): Int

    @Query("DELETE FROM share_target_activity")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM share_target_activity")
    fun getAll(): Flow<List<ShareTargetActivityEntity>>

    @Query("SELECT * FROM share_target_activity WHERE package_name LIKE :packageName")
    fun getByPackageName(packageName: String): Flow<List<ShareTargetActivityEntity>>

    @Query(
        "SELECT * FROM share_target_activity WHERE package_name LIKE :packageName " +
            "AND component_name LIKE :componentName",
    )
    fun getByPackageNameAndComponentName(
        packageName: String,
        componentName: String,
    ): Flow<ShareTargetActivityEntity?>

    @Upsert
    suspend fun upsertAll(entities: List<ShareTargetActivityEntity>)
}
