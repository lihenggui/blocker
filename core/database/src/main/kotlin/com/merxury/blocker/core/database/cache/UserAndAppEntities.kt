/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.core.database.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.UserKind
import kotlin.time.Instant

/** A cached [DeviceUser]. Deleting a user cascades to its [AppEntity] rows. */
@Entity(tableName = "device_user")
data class DeviceUserEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: Int,
    val name: String?,
    val kind: UserKind,
    @ColumnInfo(name = "parent_id") val parentId: Int?,
    val running: Boolean,
)

fun DeviceUserEntity.asExternalModel() = DeviceUser(
    id = UserId(userId),
    name = name,
    kind = kind,
    parent = parentId?.let(::UserId),
    running = running,
)

fun DeviceUser.asEntity() = DeviceUserEntity(
    userId = id.value,
    name = name,
    kind = kind,
    parentId = parent?.value,
    running = running,
)

/**
 * A cached [InstalledApp] for one user.
 *
 * The user must already be stored as a [DeviceUserEntity]; deleting the user deletes the app, and
 * deleting the app deletes that user's [ComponentPmSettingEntity] rows for the package.
 *
 * The `(user_id, package_name)` index lists one user's packages in order without reading the
 * rows, and serves the cascade from [DeviceUserEntity].
 */
@Entity(
    tableName = "app",
    primaryKeys = ["package_name", "user_id"],
    foreignKeys = [
        ForeignKey(
            entity = DeviceUserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("user_id", "package_name")],
)
data class AppEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "user_id") val userId: Int,
    val uid: Int?,
    val label: String,
    @ColumnInfo(name = "version_name") val versionName: String,
    @ColumnInfo(name = "version_code") val versionCode: Long,
    @ColumnInfo(name = "is_system") val isSystem: Boolean,
    @ColumnInfo(name = "enabled_setting") val enabledSetting: AppEnabledSetting,
    @ColumnInfo(name = "apk_path") val apkPath: String?,
    @ColumnInfo(name = "first_install_time") val firstInstallTime: Instant,
    @ColumnInfo(name = "last_update_time") val lastUpdateTime: Instant,
    @ColumnInfo(name = "min_sdk") val minSdk: Int,
    @ColumnInfo(name = "target_sdk") val targetSdk: Int,
)

fun AppEntity.asExternalModel() = InstalledApp(
    key = AppKey(packageName, UserId(userId)),
    uid = uid,
    label = label,
    versionName = versionName,
    versionCode = versionCode,
    isSystem = isSystem,
    enabledSetting = enabledSetting,
    apkPath = apkPath,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
    minSdk = minSdk,
    targetSdk = targetSdk,
)

fun InstalledApp.asEntity() = AppEntity(
    packageName = key.packageName,
    userId = key.userId.value,
    uid = uid,
    label = label,
    versionName = versionName,
    versionCode = versionCode,
    isSystem = isSystem,
    enabledSetting = enabledSetting,
    apkPath = apkPath,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
    minSdk = minSdk,
    targetSdk = targetSdk,
)
