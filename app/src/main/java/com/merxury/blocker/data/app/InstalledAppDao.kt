package com.merxury.blocker.data.app

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface InstalledAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg installedApp: InstalledApp)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(installedApp: InstalledApp)

    @Delete
    suspend fun delete(installedApp: InstalledApp): Int

    @Update
    suspend fun update(installedApp: InstalledApp): Int

    @Query("SELECT * FROM installed_app")
    suspend fun getAll(): List<InstalledApp>

    @Query("SELECT * FROM installed_app WHERE package_name = :packageName")
    suspend fun getByPackageName(packageName: String): InstalledApp?

    @Query("SELECT COUNT(package_name) FROM installed_app")
    suspend fun getCount(): Int
}