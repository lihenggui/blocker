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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.merxury.blocker.core.model.ComponentType
import kotlinx.coroutines.flow.Flow

@Dao
interface AppComponentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg appComponentEntities: AppComponentEntity)

    @Update
    suspend fun update(appComponentEntity: AppComponentEntity): Int

    @Delete
    suspend fun delete(appComponentEntity: AppComponentEntity): Int

    @Query("DELETE FROM app_component WHERE package_name LIKE :packageName")
    suspend fun deleteByPackageName(packageName: String): Int

    @Query("Delete FROM app_component")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM app_component WHERE package_name LIKE :packageName")
    fun getByPackageName(packageName: String): Flow<List<AppComponentEntity>>

    @Query(
        "SELECT * FROM app_component WHERE package_name LIKE :packageName " +
            "AND component_name LIKE :componentName",
    )
    suspend fun getByPackageNameAndComponentName(
        packageName: String,
        componentName: String,
    ): AppComponentEntity?

    @Query("SELECT * FROM app_component WHERE package_name LIKE :packageName AND type = :type")
    fun getByPackageNameAndType(
        packageName: String,
        type: ComponentType,
    ): Flow<List<AppComponentEntity>>

    @Transaction
    @Query("SELECT * FROM app_component WHERE component_name LIKE '%' || :searchKeyword || '%'")
    suspend fun getByName(searchKeyword: String): List<AppComponentEntity>

    @Upsert
    suspend fun upsertComponentList(componentList: List<AppComponentEntity>)
}
