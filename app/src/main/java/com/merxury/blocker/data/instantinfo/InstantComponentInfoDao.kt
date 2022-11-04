package com.merxury.blocker.data.instantinfo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface InstantComponentInfoDao {
    @Query("SELECT * FROM instant_component_info WHERE app_id LIKE:appId AND package_path LIKE:packagePath AND component_name LIKE:componentName LIMIT 1")
    fun find(appId: String, packagePath: String, componentName: String): InstantComponentInfo?

    @Insert
    fun insert(vararg components: InstantComponentInfo)
}