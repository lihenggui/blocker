package com.merxury.blocker.data.app

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "installed_app", indices = [Index(value = ["package_name"])])
data class InstalledApp(
    @PrimaryKey
    @ColumnInfo(name = "package_name") var packageName: String = "",
    @ColumnInfo(name = "version_name") var versionName: String? = "",
    @ColumnInfo(name = "first_install_time") var firstInstallTime: Date? = null,
    @ColumnInfo(name = "last_update_time") var lastUpdateTime: Date? = null,
    @ColumnInfo(name = "is_enabled") var isEnabled: Boolean = true,
    @ColumnInfo(name = "is_system") var isSystem: Boolean = false,
    @ColumnInfo var label: String = "",
)
