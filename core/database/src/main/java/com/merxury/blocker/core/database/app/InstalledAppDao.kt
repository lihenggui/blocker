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
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg installedAppEntity: InstalledAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(installedAppEntity: InstalledAppEntity)

    @Delete
    suspend fun delete(installedAppEntity: InstalledAppEntity): Int

    @Query("DELETE FROM installed_app WHERE package_name = :packageName")
    suspend fun deleteByPackageName(packageName: String): Int

    @Query("DELETE FROM installed_app")
    suspend fun deleteAll()

    @Update
    suspend fun update(installedAppEntity: InstalledAppEntity): Int

    @Query("SELECT * FROM installed_app")
    suspend fun getAll(): List<InstalledAppEntity>

    @Query("SELECT * FROM installed_app WHERE package_name = :packageName")
    suspend fun getByPackageName(packageName: String): InstalledAppEntity?

    @Query("SELECT * FROM installed_app WHERE package_name IN (:keyword)")
    fun getByPackageNameContains(keyword: String): Flow<List<InstalledAppEntity>>

    @Query("SELECT COUNT(package_name) FROM installed_app")
    suspend fun getCount(): Int

    @Query("SELECT * FROM installed_app")
    fun getInstalledApps(): Flow<List<InstalledAppEntity>>

    @Upsert
    fun upsertInstalledApp(app: InstalledAppEntity)

    @Upsert
    fun upsertInstalledApps(app: List<InstalledAppEntity>)

    @Delete
    suspend fun deleteApps(apps: List<InstalledAppEntity>): Int
}
