package com.merxury.blocker.data.instantinfo

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity

@Keep
@Entity(primaryKeys = ["package_path", "component_name"], tableName = "instant_component_info")
data class InstantComponentInfo(
    @ColumnInfo(name = "package_path") val packagePath: String,
    @ColumnInfo(name = "component_name") val componentName: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "recommend_to_block") val recommendToBlock: Boolean
)