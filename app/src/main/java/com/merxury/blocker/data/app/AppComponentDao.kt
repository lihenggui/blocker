package com.merxury.blocker.data.app

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.merxury.libkit.entity.EComponentType

@Dao
interface AppComponentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg appComponents: AppComponent)

    @Update
    suspend fun update(appComponent: AppComponent): Int

    @Delete
    suspend fun delete(appComponent: AppComponent): Int

    @Query("DELETE from app_component WHERE package_name LIKE :packageName")
    suspend fun deleteByPackageName(packageName: String): Int

    @Query("SELECT * FROM app_component WHERE package_name LIKE :packageName")
    suspend fun getByPackageName(packageName: String): List<AppComponent>

    @Query("SELECT * FROM app_component WHERE package_name LIKE :packageName AND component_name LIKE :componentName")
    suspend fun getByPackageNameAndComponentName(packageName: String, componentName: String): AppComponent?

    @Query("SELECT * FROM app_component WHERE package_name LIKE :packageName AND type = :type")
    suspend fun getByPackageNameAndType(
        packageName: String,
        type: EComponentType
    ): List<AppComponent>

    @Transaction
    @Query("SELECT * FROM app_component WHERE component_name LIKE '%' || :searchKeywords || '%' OR package_name LIKE '%' || :searchKeywords || '%'")
    suspend fun getByName(searchKeywords: List<String>): List<AppComponent>

}