package com.merxury.blocker.data.app

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.merxury.libkit.entity.EComponentType

@Entity(
    primaryKeys = ["package_name", "component_name"],
    tableName = "app_component",
    indices = [Index(value = ["package_name", "component_name"])],
)
data class AppComponent(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "component_name") val componentName: String,
    @ColumnInfo(name = "ifw_blocked") var ifwBlocked: Boolean,
    @ColumnInfo(name = "pm_blocked") var pmBlocked: Boolean,
    val type: EComponentType,
    val exported: Boolean,
)
