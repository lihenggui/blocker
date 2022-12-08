/*
 * Copyright 2022 Blocker
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
