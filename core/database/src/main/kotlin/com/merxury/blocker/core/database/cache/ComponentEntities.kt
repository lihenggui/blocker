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
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import kotlin.time.Instant

/**
 * A cached [ComponentDeclaration], the same for every user.
 *
 * Kept only while at least one user has the package. Deleting a declaration deletes its
 * [ComponentPmSettingEntity] and [ComponentIfwBlockEntity] rows.
 */
@Entity(tableName = "component_declaration", primaryKeys = ["package_name", "class_name"])
data class ComponentDeclarationEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "class_name") val className: String,
    val type: ComponentType,
    val exported: Boolean,
    @ColumnInfo(name = "enabled_in_manifest") val enabledInManifest: Boolean,
)

fun ComponentDeclarationEntity.asExternalModel() = ComponentDeclaration(
    id = ComponentId(packageName, className),
    type = type,
    exported = exported,
    enabledInManifest = enabledInManifest,
)

fun ComponentDeclaration.asEntity() = ComponentDeclarationEntity(
    packageName = id.packageName,
    className = id.className,
    type = type,
    exported = exported,
    enabledInManifest = enabledInManifest,
)

/**
 * When the declarations of a package were last read: the app's `lastUpdateTime` at that read. A
 * package without a marker has not been read; a marker older than the app's current
 * `lastUpdateTime` means the declarations are stale.
 */
@Entity(tableName = "component_scan")
data class ComponentScanEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_last_update_time") val appLastUpdateTime: Instant,
)

/**
 * The PackageManager setting of a declared component for one user who has the app.
 *
 * A missing row means the setting has not been read and reads as [PmSetting.UNKNOWN]; a stored
 * [PmSetting.UNKNOWN] means it was read and could not be determined.
 *
 * The `(user_id, package_name)` index serves the cascade from [AppEntity] and reads of one
 * user's settings; the primary key serves the cascade from [ComponentDeclarationEntity].
 */
@Entity(
    tableName = "component_pm_setting",
    primaryKeys = ["package_name", "class_name", "user_id"],
    foreignKeys = [
        ForeignKey(
            entity = ComponentDeclarationEntity::class,
            parentColumns = ["package_name", "class_name"],
            childColumns = ["package_name", "class_name"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AppEntity::class,
            parentColumns = ["user_id", "package_name"],
            childColumns = ["user_id", "package_name"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("user_id", "package_name")],
)
data class ComponentPmSettingEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "class_name") val className: String,
    @ColumnInfo(name = "user_id") val userId: Int,
    val setting: PmSetting,
)

/**
 * The Intent Firewall state of a declared component, the same for every user.
 *
 * A missing row means the rules have not been read and reads as [IfwBlock.UNKNOWN].
 */
@Entity(
    tableName = "component_ifw_block",
    primaryKeys = ["package_name", "class_name"],
    foreignKeys = [
        ForeignKey(
            entity = ComponentDeclarationEntity::class,
            parentColumns = ["package_name", "class_name"],
            childColumns = ["package_name", "class_name"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ComponentIfwBlockEntity(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "class_name") val className: String,
    val block: IfwBlock,
)

/**
 * A declaration joined with one user's PackageManager setting and the device's Intent Firewall
 * state. A null part has not been read.
 */
data class ComponentRow(
    @Embedded val declaration: ComponentDeclarationEntity,
    @ColumnInfo(name = "pm_setting") val pmSetting: PmSetting?,
    @ColumnInfo(name = "ifw_block") val ifwBlock: IfwBlock?,
)

/** Maps to the declaration and its control state, with parts not read yet as unknown. */
fun ComponentRow.asExternalModel(): Pair<ComponentDeclaration, ControlState> = declaration.asExternalModel() to
    ControlState(pm = pmSetting ?: PmSetting.UNKNOWN, ifw = ifwBlock ?: IfwBlock.UNKNOWN)
