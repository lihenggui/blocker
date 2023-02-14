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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.merxury.blocker.core.model.data.InstalledApp
import kotlinx.datetime.Instant

@Entity(tableName = "installed_app", indices = [Index(value = ["package_name"])])
data class InstalledAppEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String = "",
    @ColumnInfo(name = "version_name") val versionName: String = "",
    @ColumnInfo(name = "version_code") val versionCode: Long = 0,
    @ColumnInfo(name = "min_sdk_version") val minSdkVersion: Int = 0,
    @ColumnInfo(name = "target_sdk_version") val targetSdkVersion: Int = 0,
    @ColumnInfo(name = "first_install_time") val firstInstallTime: Instant? = null,
    @ColumnInfo(name = "last_update_time") val lastUpdateTime: Instant? = null,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean = true,
    @ColumnInfo(name = "is_system") val isSystem: Boolean = false,
    @ColumnInfo val label: String = "",
)

fun InstalledAppEntity.asExternalModel() = InstalledApp(
    packageName = packageName,
    versionCode = versionCode,
    versionName = versionName,
    minSdkVersion = minSdkVersion,
    targetSdkVersion = targetSdkVersion,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
    isEnabled = isEnabled,
    isSystem = isSystem,
    label = label,
)

fun InstalledApp.fromExternalModel() = InstalledAppEntity(
    packageName = packageName,
    versionCode = versionCode,
    versionName = versionName,
    minSdkVersion = minSdkVersion,
    targetSdkVersion = targetSdkVersion,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
    isEnabled = isEnabled,
    isSystem = isSystem,
    label = label,
)
